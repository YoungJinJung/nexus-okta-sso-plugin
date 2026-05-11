package org.sonatype.nexus.plugins.okta.oidc;

import java.time.Instant;

public class OidcLoginState
{
	private final String state;
	private final String nonce;
	private final Instant createdAt;

	public OidcLoginState(final String state, final String nonce, final Instant createdAt)
	{
		this.state = state;
		this.nonce = nonce;
		this.createdAt = createdAt;
	}

	public String getState()
	{
		return state;
	}

	public String getNonce()
	{
		return nonce;
	}

	public Instant getCreatedAt()
	{
		return createdAt;
	}
}
