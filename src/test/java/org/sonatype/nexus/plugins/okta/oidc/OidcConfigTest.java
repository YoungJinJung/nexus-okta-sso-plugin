package org.sonatype.nexus.plugins.okta.oidc;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;

public class OidcConfigTest
{
	@Test
	public void shouldReadOidcConfiguration()
	{
		final OidcConfig config = new OidcConfig(properties());

		assertTrue(config.isEnabled());
		assertThat(config.getIssuer(), equalTo(URI.create("https://example.okta.com/oauth2/default")));
		assertThat(config.getClientId(), equalTo("client-id"));
		assertThat(config.getRedirectUri(), equalTo(URI.create("https://nexus.example.com/okta/oidc/callback")));
		assertThat(config.getScopes(), equalTo(List.of("openid", "profile", "email", "groups")));
		assertThat(config.getUsernameClaim(), equalTo("preferred_username"));
		assertThat(config.getGroupsClaim(), equalTo("groups"));
		assertThat(config.getGroupRoleMapping(), equalTo(Map.of("Okta Admins", "nx-admin", "Developers", "nx-developer")));
	}

	@Test
	public void shouldStayDisabledByDefault()
	{
		final OidcConfig config = new OidcConfig(new Properties());

		assertFalse(config.isEnabled());
	}

	@Test
	public void shouldFailClosedWhenEnabledConfigIsMissing()
	{
		final Properties properties = new Properties();
		properties.setProperty("oidc.enabled", "true");

		assertThrows(IllegalArgumentException.class, () -> new OidcConfig(properties).validate());
	}

	@Test
	public void shouldRequireOpenIdScope()
	{
		final Properties properties = properties();
		properties.setProperty("oidc.scopes", "profile,email,groups");

		assertThrows(IllegalArgumentException.class, () -> new OidcConfig(properties).validate());
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
		properties.setProperty("oidc.group.role.mapping", "Okta Admins=nx-admin, Developers=nx-developer");
		return properties;
	}
}
