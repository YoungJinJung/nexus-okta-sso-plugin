package org.sonatype.nexus.plugins.okta.client;

import static org.sonatype.nexus.plugins.okta.client.OktaAuthClientExceptionSeverity.INFO;
import static org.sonatype.nexus.plugins.okta.client.OktaAuthClientExceptionSeverity.WARN;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.plugins.okta.client.dto.OktaAuthRequest;
import org.sonatype.nexus.plugins.okta.client.dto.OktaAuthRequestVerifyFactor;
import org.sonatype.nexus.plugins.okta.client.dto.OktaAuthResponse;
import org.sonatype.nexus.plugins.okta.client.dto.OktaAuthResponseEmbeddedFactor;
import org.sonatype.nexus.plugins.okta.client.dto.OktaGroup;

@Singleton
@Named("OktaAuthClient")
public class OktaAuthClient
{
	private static final Logger LOG = LoggerFactory.getLogger(OktaAuthClient.class);

	private ApiHttpClient client;
	private OktaAuthClientConfig config;

	public OktaAuthClient()
	{
		// empty
	}

	@Inject
	public OktaAuthClient(final OktaAuthClientConfig config)
	{
		this.config = config;
		client = new ApiHttpClientImpl();
	}

	public OktaAuthClient(final ApiHttpClient client, final OktaAuthClientConfig config)
	{
		this.client = client;
		this.config = config;
	}

	public OktaAuthClientConfig getConfig()
	{
		return config;
	}

	public OktaAuthResponse authn(final String username, final String password)
	{

		final OktaAuthRequest requestBody = new OktaAuthRequest(username, password, null);

		final String uri = config.getOktaOrgUrl() + config.getOktaApi() + "/authn";
		OktaAuthResponse response = client.sendPostRequest(uri, requestBody, OktaAuthResponse.class);

		if ("MFA_REQUIRED".equals(response.getStatus()))
		{
			response = handleMfaChallenge(response);
		}

		if ("SUCCESS".equals(response.getStatus()))
		{
			if (response.getSessionToken() == null)
			{
				throw new OktaAuthClientException(WARN,
						"Authentication appears to be successful, but no session token was found.");
			}
			return response;
		}

		throw new OktaAuthClientException(INFO, "Authentication was not successful");
	}

	public List<String> getMappedRoles(final OktaAuthResponse authResponse)
	{
		if (authResponse == null || authResponse.getEmbedded() == null || authResponse.getEmbedded().getUser() == null)
		{
			return Collections.emptyList();
		}

		final String userId = authResponse.getEmbedded().getUser().getId();
		if (StringUtils.isBlank(userId) || StringUtils.isBlank(config.getOktaApiToken()))
		{
			return Collections.emptyList();
		}

		final Map<String, String> groupRoleMapping = config.getOktaGroupRoleMapping();
		if (groupRoleMapping.isEmpty())
		{
			return Collections.emptyList();
		}

		final String uri = config.getOktaOrgUrl() + config.getOktaApi() + "/users/" + userId + "/groups";
		final OktaGroup[] groups = client.sendGetRequest(uri, config.getOktaApiToken(), OktaGroup[].class);
		if (groups == null || groups.length == 0)
		{
			return Collections.emptyList();
		}

		final List<String> roles = new ArrayList<>();
		for (final OktaGroup group : groups)
		{
			if (group != null && group.getProfile() != null)
			{
				final String role = groupRoleMapping.get(group.getProfile().getName());
				if (StringUtils.isNotBlank(role))
				{
					roles.add(role);
				}
			}
		}
		return roles;
	}

	protected OktaAuthResponse handleMfaChallenge(final OktaAuthResponse response)
	{
		if (response == null || response.getEmbedded() == null || response.getEmbedded().getFactors() == null
				|| response.getEmbedded().getFactors().isEmpty())
		{
			throw new OktaAuthClientException(INFO,
					"Status indicates that MFA is required, but no second factor config was found in the response. Did you set up MFA correctly?");
		}

		OktaAuthResponseEmbeddedFactor selectedFactor = null;
		for (final OktaAuthResponseEmbeddedFactor factor : response.getEmbedded().getFactors())
		{
			if ("push".equalsIgnoreCase(factor.getFactorType()))
			{
				selectedFactor = factor;
				break;
			}
		}

		if (selectedFactor == null)
		{
			throw new OktaAuthClientException(INFO,
					"No supported factor found. At the moment only the 'push' factor type is supported. Factors found: "
							+ client.asStrOrEmpty(response.getEmbedded().getFactors()));
		}

		final OktaAuthResponse verifiedResponse = verifyMfa(response.getStateToken(), selectedFactor);
		if (verifiedResponse.getEmbedded() == null || verifiedResponse.getEmbedded().getUser() == null)
		{
			verifiedResponse.setEmbedded(response.getEmbedded());
		}
		return verifiedResponse;
	}

	protected OktaAuthResponse verifyMfa(final String stateToken, final OktaAuthResponseEmbeddedFactor factor)
	{
		if (factor == null || factor.getLinks() == null || factor.getLinks().getVerify() == null
				|| StringUtils.isBlank(factor.getLinks().getVerify().getHref()))
		{
			throw new OktaAuthClientException(WARN, "Expected to find link for verification on factor " + client.asStrOrEmpty(factor));
		}
		final String verifyLink = factor.getLinks().getVerify().getHref();
		final OktaAuthRequestVerifyFactor body = new OktaAuthRequestVerifyFactor(stateToken);
		final OktaAuthResponse verificationResponse = client.sendPostRequest(verifyLink, body, OktaAuthResponse.class);

		if (verificationResponse == null || verificationResponse.getLinks() == null
				|| verificationResponse.getLinks().getNext() == null
				|| StringUtils.isBlank(verificationResponse.getLinks().getNext().getHref()))
		{
			throw new OktaAuthClientException(WARN,
					"Expected to find link to poll for push notification: " + client.asStrOrEmpty(verificationResponse));
		}

		final int pollDelay = config.getMfaPollDelay();
		final int pollMaxRetries = config.getMfaPollMaxRetries();
		return pollForPushNotification(stateToken, verificationResponse.getLinks().getNext().getHref(), pollDelay, pollMaxRetries,
				1);
	}

	protected OktaAuthResponse pollForPushNotification(final String stateToken, final String pollLink, final int delay, final int maxRetries, final int tryNo)
	{
		if (tryNo >= maxRetries)
		{
			throw new OktaAuthClientException(WARN,
					"Still waiting for push notification confirmation after too many retries (" + tryNo + ")");
		}

		try
		{
			Thread.sleep(delay);
		} catch (final InterruptedException e)
		{
			LOG.warn(e.getMessage(), e); // ignore
		}

		final OktaAuthRequestVerifyFactor body = new OktaAuthRequestVerifyFactor(stateToken);
		final OktaAuthResponse response = client.sendPostRequest(pollLink, body, OktaAuthResponse.class);
		if ("SUCCESS".equals(response.getStatus()))
		{
			return response;
		} else if ("WAITING".equals(response.getFactorResult()))
		{
			return pollForPushNotification(stateToken, pollLink, delay, maxRetries, tryNo + 1);
		} else if ("TIMEOUT".equals(response.getFactorResult()))
		{
			throw new OktaAuthClientException(INFO, "Push notification confirmation timed out");
		} else if ("REJECTED".equals(response.getFactorResult()))
		{
			throw new OktaAuthClientException(INFO, "Push notification was rejected");
		} else
		{
			throw new OktaAuthClientException(WARN, "Unexpected verification status: " + response.getFactorResult());
		}
	}


}
