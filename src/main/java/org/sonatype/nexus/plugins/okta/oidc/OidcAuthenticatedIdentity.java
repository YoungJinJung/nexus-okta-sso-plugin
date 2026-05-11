package org.sonatype.nexus.plugins.okta.oidc;

import java.util.Set;

public class OidcAuthenticatedIdentity
{
	private final String subject;
	private final String username;
	private final Set<String> roles;

	public OidcAuthenticatedIdentity(final String subject, final String username, final Set<String> roles)
	{
		this.subject = subject;
		this.username = username;
		this.roles = Set.copyOf(roles);
	}

	public String getSubject()
	{
		return subject;
	}

	public String getUsername()
	{
		return username;
	}

	public Set<String> getRoles()
	{
		return roles;
	}
}
