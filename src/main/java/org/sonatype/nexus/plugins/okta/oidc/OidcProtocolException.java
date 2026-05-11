package org.sonatype.nexus.plugins.okta.oidc;

public class OidcProtocolException
		extends RuntimeException
{
	public OidcProtocolException(final String message)
	{
		super(message);
	}

	public OidcProtocolException(final String message, final Throwable cause)
	{
		super(message, cause);
	}
}
