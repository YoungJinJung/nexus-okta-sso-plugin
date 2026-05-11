package org.sonatype.nexus.plugins.okta.oidc;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.Test;

public class OidcLoginStateStoreTest
{
	@Test
	public void shouldConsumeStateOnlyOnce()
	{
		final OidcLoginStateStore store = new OidcLoginStateStore(clock("2026-05-11T00:00:00Z"));
		final OidcLoginState loginState = new OidcLoginState("state", "nonce", Instant.parse("2026-05-11T00:00:00Z"));
		store.put(loginState);

		assertThat(store.consume("state"), equalTo(loginState));
		assertThrows(OidcProtocolException.class, () -> store.consume("state"));
	}

	@Test
	public void shouldRejectExpiredState()
	{
		final OidcLoginStateStore store = new OidcLoginStateStore(clock("2026-05-11T00:06:00Z"));
		store.put(new OidcLoginState("state", "nonce", Instant.parse("2026-05-11T00:00:00Z")));

		assertThrows(OidcProtocolException.class, () -> store.consume("state"));
	}

	private Clock clock(final String instant)
	{
		return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
	}
}
