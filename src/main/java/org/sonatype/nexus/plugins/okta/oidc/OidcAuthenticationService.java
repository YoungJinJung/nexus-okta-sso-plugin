package org.sonatype.nexus.plugins.okta.oidc;

import java.net.URI;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;

@Singleton
@Named
public class OidcAuthenticationService
{
	private final OidcLoginStateGenerator stateGenerator;
	private final OidcConfig config;
	private final OidcLoginStateStore stateStore;
	private final OidcAuthorizationUrlBuilder authorizationUrlBuilder;
	private final OidcProviderMetadataResolver metadataResolver;
	private final OidcTokenClient tokenClient;
	private final OidcIdTokenValidator idTokenValidator;

	@Inject
	public OidcAuthenticationService(
			final OidcLoginStateGenerator stateGenerator,
			final OidcConfig config,
			final OidcLoginStateStore stateStore,
			final OidcAuthorizationUrlBuilder authorizationUrlBuilder,
			final OidcProviderMetadataResolver metadataResolver,
			final OidcTokenClient tokenClient,
			final OidcIdTokenValidator idTokenValidator)
	{
		this.stateGenerator = Objects.requireNonNull(stateGenerator);
		this.config = Objects.requireNonNull(config);
		this.stateStore = Objects.requireNonNull(stateStore);
		this.authorizationUrlBuilder = Objects.requireNonNull(authorizationUrlBuilder);
		this.metadataResolver = Objects.requireNonNull(metadataResolver);
		this.tokenClient = Objects.requireNonNull(tokenClient);
		this.idTokenValidator = Objects.requireNonNull(idTokenValidator);
	}

	public URI beginLogin()
	{
		if (!config.isEnabled())
		{
			throw new OidcProtocolException("OIDC login is disabled");
		}

		final OidcLoginState loginState = stateGenerator.generate();
		stateStore.put(loginState);
		return authorizationUrlBuilder.build(loginState);
	}

	public OidcAuthenticatedIdentity completeLogin(final String code, final String state)
	{
		final OidcLoginState loginState = stateStore.consume(state);
		final OIDCProviderMetadata metadata = metadataResolver.resolve();
		final OIDCTokens tokens = tokenClient.exchangeAuthorizationCode(metadata, code);
		return idTokenValidator.validate(metadata, tokens, loginState);
	}
}
