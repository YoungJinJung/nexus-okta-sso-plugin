package org.sonatype.nexus.plugins.okta.oidc;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named
public class OidcLoginTicketStore
{
	private static final String PREFIX = "OIDC-TICKET:";
	private static final Duration TTL = Duration.ofMinutes(2);
	private static final int TOKEN_BYTES = 32;

	private final SecureRandom random;
	private final Clock clock;
	private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

	@Inject
	public OidcLoginTicketStore()
	{
		this(new SecureRandom(), Clock.systemUTC());
	}

	OidcLoginTicketStore(final SecureRandom random, final Clock clock)
	{
		this.random = Objects.requireNonNull(random);
		this.clock = Objects.requireNonNull(clock);
	}

	public String issue(final OidcAuthenticatedIdentity identity)
	{
		Objects.requireNonNull(identity);
		purgeExpired();

		final byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		final String secret = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		tickets.put(secret, new Ticket(identity, Instant.now(clock).plus(TTL)));
		return secret;
	}

	public Optional<OidcAuthenticatedIdentity> consume(final String username, final String secret)
	{
		if (username == null || username.isBlank() || secret == null || !secret.startsWith(PREFIX))
		{
			return Optional.empty();
		}

		final Ticket ticket = tickets.remove(secret);
		if (ticket == null || ticket.expiresAt().isBefore(Instant.now(clock)))
		{
			return Optional.empty();
		}
		if (!ticket.identity().getUsername().equals(username))
		{
			return Optional.empty();
		}
		return Optional.of(ticket.identity());
	}

	private void purgeExpired()
	{
		final Instant now = Instant.now(clock);
		tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
	}

	private record Ticket(OidcAuthenticatedIdentity identity, Instant expiresAt)
	{
	}
}
