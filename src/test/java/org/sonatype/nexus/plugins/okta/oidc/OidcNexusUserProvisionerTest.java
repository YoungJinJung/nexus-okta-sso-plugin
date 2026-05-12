package org.sonatype.nexus.plugins.okta.oidc;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.util.Set;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonatype.nexus.security.role.RoleIdentifier;
import org.sonatype.nexus.security.user.User;
import org.sonatype.nexus.security.user.UserManager;
import org.sonatype.nexus.security.user.UserNotFoundException;
import org.sonatype.nexus.security.user.UserStatus;

public class OidcNexusUserProvisionerTest
{
	@Test
	public void shouldCreateMissingNexusUserForOidcIdentity()
		throws Exception
	{
		final UserManager userManager = mock(UserManager.class);
		when(userManager.getUser("alice@example.com")).thenThrow(new UserNotFoundException("alice@example.com"));

		new OidcNexusUserProvisioner(userManager, deterministicRandom())
				.provision(new OidcAuthenticatedIdentity("subject", "alice@example.com", Set.of("nx-developer")));

		final ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userManager).addUser(userCaptor.capture(), anyString());

		final User user = userCaptor.getValue();
		assertThat(user.getUserId(), equalTo("alice@example.com"));
		assertThat(user.getSource(), equalTo(UserManager.DEFAULT_SOURCE));
		assertThat(user.getStatus(), equalTo(UserStatus.active));
		assertThat(user.getFirstName(), equalTo("alice"));
		assertThat(user.getLastName(), equalTo("Okta"));
		assertThat(user.getEmailAddress(), equalTo("alice@example.com"));
		assertThat(user.getRoles(), hasItem(new RoleIdentifier(UserManager.DEFAULT_SOURCE, "nx-developer")));
	}

	@Test
	public void shouldMergeMappedRolesIntoExistingNexusUser()
		throws Exception
	{
		final User user = new User();
		user.setUserId("alice@example.com");
		user.setRoles(Set.of(new RoleIdentifier(UserManager.DEFAULT_SOURCE, "existing-role")));
		final UserManager userManager = mock(UserManager.class);
		when(userManager.getUser("alice@example.com")).thenReturn(user);

		new OidcNexusUserProvisioner(userManager, deterministicRandom())
				.provision(new OidcAuthenticatedIdentity("subject", "alice@example.com", Set.of("nx-developer")));

		verify(userManager).updateUser(user);
		assertThat(user.getRoles(), hasItem(new RoleIdentifier(UserManager.DEFAULT_SOURCE, "existing-role")));
		assertThat(user.getRoles(), hasItem(new RoleIdentifier(UserManager.DEFAULT_SOURCE, "nx-developer")));
	}

	private SecureRandom deterministicRandom()
	{
		return new SecureRandom()
		{
			@Override
			public void nextBytes(final byte[] bytes)
			{
				for (int i = 0; i < bytes.length; i++)
				{
					bytes[i] = (byte) i;
				}
			}
		};
	}
}
