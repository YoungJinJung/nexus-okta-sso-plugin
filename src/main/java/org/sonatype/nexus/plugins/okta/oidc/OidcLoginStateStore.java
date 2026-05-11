package org.sonatype.nexus.plugins.okta.oidc;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named
public class OidcLoginStateStore
{
	private static final Duration STATE_TTL = Duration.ofMinutes(5);

	private final Map<String, OidcLoginState> states = new ConcurrentHashMap<>();
	private final Clock clock;

	@Inject
	public OidcLoginStateStore()
	{
		this(Clock.systemUTC());
	}

	public OidcLoginStateStore(final Clock clock)
	{
		this.clock = Objects.requireNonNull(clock);
	}

	public void put(final OidcLoginState loginState)
	{
		Objects.requireNonNull(loginState);
		removeExpiredStates();
		states.put(loginState.getState(), loginState);
	}

	public OidcLoginState consume(final String state)
	{
		if (state == null || state.isBlank())
		{
			throw new OidcProtocolException("OIDC state is required");
		}

		final OidcLoginState loginState = states.remove(state);
		if (loginState == null || isExpired(loginState))
		{
			throw new OidcProtocolException("OIDC state is invalid or expired");
		}
		return loginState;
	}

	private void removeExpiredStates()
	{
		states.entrySet().removeIf(entry -> isExpired(entry.getValue()));
	}

	private boolean isExpired(final OidcLoginState loginState)
	{
		return loginState.getCreatedAt().plus(STATE_TTL).isBefore(clock.instant());
	}
}
