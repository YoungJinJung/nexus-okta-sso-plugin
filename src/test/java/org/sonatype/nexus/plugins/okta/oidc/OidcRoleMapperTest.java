package org.sonatype.nexus.plugins.okta.oidc;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.Test;

public class OidcRoleMapperTest
{
	@Test
	public void shouldMapGroupsToNexusRoles()
	{
		final Properties properties = new Properties();
		properties.setProperty("oidc.group.role.mapping", "Okta Admins=nx-admin,Developers=nx-developer");
		final OidcRoleMapper mapper = new OidcRoleMapper(new OidcConfig(properties));

		assertThat(mapper.mapGroupsToRoles(List.of("Okta Admins", "Ignored")),
				equalTo(Set.of("nx-admin")));
	}
}
