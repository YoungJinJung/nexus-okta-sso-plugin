package org.sonatype.nexus.plugins.okta.oidc;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named
public class OidcLoginStateGenerator
{
	private static final int RANDOM_BYTES = 32;

	private final SecureRandom secureRandom;
	private final Clock clock;

	public OidcLoginStateGenerator()
	{
		this(new SecureRandom(), Clock.systemUTC());
	}

	public OidcLoginStateGenerator(final SecureRandom secureRandom, final Clock clock)
	{
		this.secureRandom = secureRandom;
		this.clock = clock;
	}

	public OidcLoginState generate()
	{
		return new OidcLoginState(randomToken(), randomToken(), clock.instant());
	}

	private String randomToken()
	{
		final byte[] bytes = new byte[RANDOM_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
