package org.sonatype.nexus.plugins.okta.oidc;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.SubjectType;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import org.junit.Test;

public class OidcAuthorizationUrlBuilderTest
{
	@Test
	public void shouldBuildAuthorizationCodeUrl()
	{
		final OidcAuthorizationUrlBuilder builder = new OidcAuthorizationUrlBuilder(new OidcConfig(properties()));

		final OIDCProviderMetadata metadata = new OIDCProviderMetadata(
				new Issuer("https://example.okta.com"),
				java.util.List.of(SubjectType.PUBLIC),
				URI.create("https://example.okta.com/oauth2/v1/keys"));
		metadata.setAuthorizationEndpointURI(URI.create("https://example.okta.com/oauth2/v1/authorize"));

		final URI uri = builder.build(
				metadata,
				new OidcLoginState("state-value", "nonce-value", Instant.parse("2026-05-11T00:00:00Z")));
		final Map<String, String> params = queryParams(uri);

		assertThat(uri.getScheme(), equalTo("https"));
		assertThat(uri.getHost(), equalTo("example.okta.com"));
		assertThat(uri.getPath(), equalTo("/oauth2/v1/authorize"));
		assertThat(params.get("client_id"), equalTo("client-id"));
		assertThat(params.get("response_type"), equalTo("code"));
		assertThat(params.get("scope"), equalTo("openid profile email groups"));
		assertThat(params.get("redirect_uri"), equalTo("https://nexus.example.com/okta/oidc/callback"));
		assertThat(params.get("state"), equalTo("state-value"));
		assertThat(params.get("nonce"), equalTo("nonce-value"));
		assertThat(params.containsKey("client_secret"), is(false));
	}

	private Map<String, String> queryParams(final URI uri)
	{
		return Arrays.stream(uri.getRawQuery().split("&"))
				.map(param -> param.split("=", 2))
				.collect(Collectors.toMap(
						param -> decode(param[0]),
						param -> decode(param[1])));
	}

	private String decode(final String value)
	{
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}

	private Properties properties()
	{
		final Properties properties = new Properties();
		properties.setProperty("oidc.enabled", "true");
		properties.setProperty("oidc.issuer", "https://example.okta.com/oauth2/default");
		properties.setProperty("oidc.client.id", "client-id");
		properties.setProperty("oidc.client.secret", "client-secret");
		properties.setProperty("oidc.redirect.uri", "https://nexus.example.com/okta/oidc/callback");
		properties.setProperty("oidc.scopes", "openid, profile, email, groups");
		properties.setProperty("oidc.group.role.mapping", "Okta Admins=nx-admin");
		return properties;
	}
}
