package org.sonatype.nexus.plugins.okta.oidc;

import java.io.IOException;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;

@Singleton
@Named
public class OidcProviderMetadataResolver
{
	private static final int CONNECT_TIMEOUT_MS = 5_000;
	private static final int READ_TIMEOUT_MS = 5_000;

	private final OidcConfig config;

	@Inject
	public OidcProviderMetadataResolver(final OidcConfig config)
	{
		this.config = Objects.requireNonNull(config);
	}

	public OIDCProviderMetadata resolve()
	{
		config.validate();
		try
		{
			final Issuer issuer = new Issuer(config.getIssuer().toString());
			final OIDCProviderMetadata metadata = OIDCProviderMetadata.resolve(issuer, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
			if (!issuer.equals(metadata.getIssuer()))
			{
				throw new OidcProtocolException("OIDC discovery issuer does not match configured issuer");
			}
			if (metadata.getTokenEndpointURI() == null)
			{
				throw new OidcProtocolException("OIDC discovery metadata does not define token endpoint");
			}
			if (metadata.getJWKSetURI() == null)
			{
				throw new OidcProtocolException("OIDC discovery metadata does not define JWKS URI");
			}
			return metadata;
		}
		catch (final GeneralException | IOException e)
		{
			throw new OidcProtocolException("Unable to resolve OIDC discovery metadata", e);
		}
	}
}
