package org.sonatype.nexus.plugins.okta.oidc;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named
public class OidcRoleMapper
{
	private final OidcConfig config;

	@Inject
	public OidcRoleMapper(final OidcConfig config)
	{
		this.config = config;
	}

	public Set<String> mapGroupsToRoles(final Collection<String> groups)
	{
		final Map<String, String> mapping = config.getGroupRoleMapping();
		final Set<String> roles = new HashSet<>();
		if (groups == null || groups.isEmpty() || mapping.isEmpty())
		{
			return roles;
		}

		for (final String group : groups)
		{
			final String role = mapping.get(group);
			if (role != null && !role.isBlank())
			{
				roles.add(role);
			}
		}
		return roles;
	}
}
