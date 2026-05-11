package org.sonatype.nexus.plugins.okta.oidc;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.Test;

public class OidcLoginStateGeneratorTest
{
	@Test
	public void shouldGenerateDistinctStateAndNonce()
	{
		final OidcLoginStateGenerator generator = new OidcLoginStateGenerator(
				new SecureRandom(), Clock.fixed(Instant.parse("2026-05-11T00:00:00Z"), ZoneOffset.UTC));

		final OidcLoginState first = generator.generate();
		final OidcLoginState second = generator.generate();

		assertThat(first.getCreatedAt(), equalTo(Instant.parse("2026-05-11T00:00:00Z")));
		assertThat(first.getState(), not(equalTo(first.getNonce())));
		assertThat(first.getState(), not(equalTo(second.getState())));
		assertTrue(first.getState().length() >= 40);
		assertTrue(first.getNonce().length() >= 40);
	}
}
