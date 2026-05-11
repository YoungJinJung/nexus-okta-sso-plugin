package org.sonatype.nexus.plugins.okta.oidc;

import java.util.Objects;

public class OidcLoginResult
{
	private final OidcAuthenticatedIdentity identity;
	private final String idToken;

	public OidcLoginResult(final OidcAuthenticatedIdentity identity, final String idToken)
	{
		this.identity = Objects.requireNonNull(identity);
		this.idToken = Objects.requireNonNull(idToken);
	}

	public OidcAuthenticatedIdentity getIdentity()
	{
		return identity;
	}

	public String getIdToken()
	{
		return idToken;
	}
}
