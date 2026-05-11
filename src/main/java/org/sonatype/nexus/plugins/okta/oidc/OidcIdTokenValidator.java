package org.sonatype.nexus.plugins.okta.oidc;

import java.net.MalformedURLException;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;

@Singleton
@Named
public class OidcIdTokenValidator
{
	private final OidcConfig config;
	private final OidcIdentityExtractor identityExtractor;

	@Inject
	public OidcIdTokenValidator(final OidcConfig config, final OidcIdentityExtractor identityExtractor)
	{
		this.config = Objects.requireNonNull(config);
		this.identityExtractor = Objects.requireNonNull(identityExtractor);
	}

	public OidcAuthenticatedIdentity validate(
			final OIDCProviderMetadata metadata,
			final OIDCTokens tokens,
			final OidcLoginState loginState)
	{
		Objects.requireNonNull(tokens);
		if (tokens.getIDToken() == null)
		{
			throw new OidcProtocolException("OIDC token response did not include an ID token");
		}

		try
		{
			final IDTokenValidator validator = createValidator(metadata);
			final IDTokenClaimsSet claims = validator.validate(tokens.getIDToken(), new Nonce(loginState.getNonce()));
			return identityExtractor.extract(claims);
		}
		catch (final BadJOSEException e)
		{
			throw new OidcProtocolException("OIDC ID token validation failed", e);
		}
		catch (final JOSEException | MalformedURLException e)
		{
			throw new OidcProtocolException("Unable to validate OIDC ID token", e);
		}
	}

	protected IDTokenValidator createValidator(final OIDCProviderMetadata metadata) throws MalformedURLException
	{
		return new IDTokenValidator(
				new Issuer(config.getIssuer().toString()),
				new ClientID(config.getClientId()),
				JWSAlgorithm.RS256,
				metadata.getJWKSetURI().toURL());
	}
}
