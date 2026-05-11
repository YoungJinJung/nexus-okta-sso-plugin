package org.sonatype.nexus.plugins.okta.oidc;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import com.nimbusds.oauth2.sdk.id.Audience;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.Subject;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;
import org.junit.Test;

public class OidcIdentityExtractorTest
{
	@Test
	public void shouldExtractMappedIdentity()
	{
		final OidcIdentityExtractor extractor = new OidcIdentityExtractor(new OidcConfig(properties()));
		final IDTokenClaimsSet claims = claims();
		claims.setClaim("preferred_username", "alice@example.com");
		claims.setClaim("groups", List.of("Developers", "Ignored"));

		final OidcAuthenticatedIdentity identity = extractor.extract(claims);

		assertThat(identity.getSubject(), equalTo("okta-subject"));
		assertThat(identity.getUsername(), equalTo("alice@example.com"));
		assertThat(identity.getRoles(), equalTo(Set.of("nx-developer")));
	}

	@Test
	public void shouldFailClosedWhenNoRoleIsMapped()
	{
		final OidcIdentityExtractor extractor = new OidcIdentityExtractor(new OidcConfig(properties()));
		final IDTokenClaimsSet claims = claims();
		claims.setClaim("preferred_username", "alice@example.com");
		claims.setClaim("groups", List.of("Ignored"));

		assertThrows(OidcProtocolException.class, () -> extractor.extract(claims));
	}

	@Test
	public void shouldFailClosedWhenUsernameClaimIsMissing()
	{
		final OidcIdentityExtractor extractor = new OidcIdentityExtractor(new OidcConfig(properties()));
		final IDTokenClaimsSet claims = claims();
		claims.setClaim("groups", List.of("Developers"));

		assertThrows(OidcProtocolException.class, () -> extractor.extract(claims));
	}

	private IDTokenClaimsSet claims()
	{
		return new IDTokenClaimsSet(
				new Issuer("https://example.okta.com/oauth2/default"),
				new Subject("okta-subject"),
				List.of(new Audience("client-id")),
				new Date(System.currentTimeMillis() + 60_000),
				new Date());
	}

	private Properties properties()
	{
		final Properties properties = new Properties();
		properties.setProperty("oidc.group.role.mapping", "Developers=nx-developer");
		return properties;
	}
}
