package org.sonatype.nexus.plugins.okta.oidc;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;

@Singleton
@Named
public class OidcAuthorizationUrlBuilder
{
	private final OidcConfig config;

	@Inject
	public OidcAuthorizationUrlBuilder(final OidcConfig config)
	{
		this.config = Objects.requireNonNull(config);
	}

	public URI build(final OIDCProviderMetadata metadata, final OidcLoginState loginState)
	{
		Objects.requireNonNull(metadata);
		Objects.requireNonNull(loginState);
		config.validate();
		if (metadata.getAuthorizationEndpointURI() == null)
		{
			throw new OidcProtocolException("OIDC discovery metadata does not define authorization endpoint");
		}

		final Map<String, String> params = new LinkedHashMap<>();
		params.put("client_id", config.getClientId());
		params.put("response_type", "code");
		params.put("scope", String.join(" ", config.getScopes()));
		params.put("redirect_uri", config.getRedirectUri().toString());
		params.put("state", loginState.getState());
		params.put("nonce", loginState.getNonce());

		return URI.create(metadata.getAuthorizationEndpointURI() + "?" + encode(params));
	}

	private String encode(final Map<String, String> params)
	{
		final StringBuilder result = new StringBuilder();
		for (final Map.Entry<String, String> entry : params.entrySet())
		{
			if (result.length() > 0)
			{
				result.append('&');
			}
			result.append(urlEncode(entry.getKey()));
			result.append('=');
			result.append(urlEncode(entry.getValue()));
		}
		return result.toString();
	}

	private String urlEncode(final String value)
	{
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

}
