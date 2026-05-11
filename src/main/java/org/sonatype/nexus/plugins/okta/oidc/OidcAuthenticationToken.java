package org.sonatype.nexus.plugins.okta.oidc;

import java.util.Objects;

import org.apache.shiro.authc.HostAuthenticationToken;

public class OidcAuthenticationToken
		implements HostAuthenticationToken
{
	private final OidcAuthenticatedIdentity identity;
	private final String host;

	public OidcAuthenticationToken(final OidcAuthenticatedIdentity identity, final String host)
	{
		this.identity = Objects.requireNonNull(identity);
		this.host = host;
	}

	public OidcAuthenticatedIdentity getIdentity()
	{
		return identity;
	}

	@Override
	public Object getPrincipal()
	{
		return identity.getUsername();
	}

	@Override
	public Object getCredentials()
	{
		return identity.getSubject();
	}

	@Override
	public String getHost()
	{
		return host;
	}
}
