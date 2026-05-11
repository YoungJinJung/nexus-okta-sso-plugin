package org.sonatype.nexus.plugins.okta.oidc;

import java.io.IOException;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;

@Singleton
@Named
public class OidcTokenClient
{
	private static final int CONNECT_TIMEOUT_MS = 5_000;
	private static final int READ_TIMEOUT_MS = 10_000;

	private final OidcConfig config;

	@Inject
	public OidcTokenClient(final OidcConfig config)
	{
		this.config = Objects.requireNonNull(config);
	}

	public OIDCTokens exchangeAuthorizationCode(final OIDCProviderMetadata metadata, final String code)
	{
		Objects.requireNonNull(metadata);
		if (code == null || code.isBlank())
		{
			throw new OidcProtocolException("Authorization code is required");
		}

		try
		{
			final AuthorizationCodeGrant grant = new AuthorizationCodeGrant(
					new AuthorizationCode(code),
					config.getRedirectUri());
			final TokenRequest request = new TokenRequest(
					metadata.getTokenEndpointURI(),
					new ClientSecretBasic(new ClientID(config.getClientId()), new Secret(config.getClientSecret())),
					grant);

			final HTTPRequest httpRequest = request.toHTTPRequest();
			httpRequest.setConnectTimeout(CONNECT_TIMEOUT_MS);
			httpRequest.setReadTimeout(READ_TIMEOUT_MS);

			final TokenResponse response = OIDCTokenResponseParser.parse(httpRequest.send());
			if (!response.indicatesSuccess())
			{
				throw new OidcProtocolException("OIDC token endpoint rejected authorization code");
			}
			final AccessTokenResponse successResponse = response.toSuccessResponse();
			if (!(successResponse instanceof OIDCTokenResponse))
			{
				throw new OidcProtocolException("OIDC token endpoint response did not include OIDC tokens");
			}
			return ((OIDCTokenResponse) successResponse).getOIDCTokens();
		}
		catch (final IOException | ParseException e)
		{
			throw new OidcProtocolException("Unable to exchange OIDC authorization code", e);
		}
	}
}
