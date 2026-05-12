package org.sonatype.nexus.plugins.okta.oidc;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.sonatype.nexus.security.role.RoleIdentifier;
import org.sonatype.nexus.security.user.User;
import org.sonatype.nexus.security.user.UserManager;
import org.sonatype.nexus.security.user.UserNotFoundException;
import org.sonatype.nexus.security.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Named
public class OidcNexusUserProvisioner
{
	private static final Logger LOG = LoggerFactory.getLogger(OidcNexusUserProvisioner.class);

	private final UserManager userManager;
	private final SecureRandom secureRandom;

	@Inject
	public OidcNexusUserProvisioner(final UserManager userManager)
	{
		this(userManager, new SecureRandom());
	}

	OidcNexusUserProvisioner(final UserManager userManager, final SecureRandom secureRandom)
	{
		this.userManager = Objects.requireNonNull(userManager);
		this.secureRandom = Objects.requireNonNull(secureRandom);
	}

	public void provision(final OidcAuthenticatedIdentity identity)
	{
		Objects.requireNonNull(identity);

		try
		{
			final User user = userManager.getUser(identity.getUsername());
			update(user, identity);
			userManager.updateUser(user);
			LOG.debug("Updated Nexus user record for OIDC user {}", identity.getUsername());
		}
		catch (final UserNotFoundException e)
		{
			final User user = new User();
			update(user, identity);
			userManager.addUser(user, randomPassword());
			LOG.info("Provisioned Nexus user record for OIDC user {}", identity.getUsername());
		}
	}

	private void update(final User user, final OidcAuthenticatedIdentity identity)
	{
		user.setUserId(identity.getUsername());
		user.setSource(UserManager.DEFAULT_SOURCE);
		user.setStatus(UserStatus.active);
		user.setEmailAddress(identity.getUsername());
		user.setName(identity.getUsername());
		user.setFirstName(firstName(identity.getUsername()));
		user.setLastName("Okta");

		final Set<RoleIdentifier> roles = user.getRoles() == null ? new HashSet<>() : new HashSet<>(user.getRoles());
		for (final String role : identity.getRoles())
		{
			roles.add(new RoleIdentifier(UserManager.DEFAULT_SOURCE, role));
		}
		user.setRoles(roles);
	}

	private String randomPassword()
	{
		final byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String firstName(final String username)
	{
		final int at = username.indexOf('@');
		return at > 0 ? username.substring(0, at) : username;
	}
}
