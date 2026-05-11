package org.sonatype.nexus.plugins.okta.oidc;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.Set;

import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import org.junit.Test;

public class OidcAuthenticationServiceTest
{
	@Test
	public void shouldBeginLoginWithStoredState()
	{
		final OidcLoginState loginState = new OidcLoginState("state", "nonce", Instant.parse("2026-05-11T00:00:00Z"));
		final OidcLoginStateGenerator stateGenerator = mock(OidcLoginStateGenerator.class);
		final OidcLoginStateStore stateStore = mock(OidcLoginStateStore.class);
		final OidcAuthorizationUrlBuilder authorizationUrlBuilder = mock(OidcAuthorizationUrlBuilder.class);
		when(stateGenerator.generate()).thenReturn(loginState);
		when(authorizationUrlBuilder.build(loginState)).thenReturn(URI.create("https://example.okta.com/authorize"));

		final URI authorizationUrl = service(stateGenerator, enabledConfig(), stateStore, authorizationUrlBuilder).beginLogin();

		assertThat(authorizationUrl, equalTo(URI.create("https://example.okta.com/authorize")));
		verify(stateStore).put(loginState);
	}

	@Test
	public void shouldCompleteLogin()
	{
		final OidcLoginState loginState = new OidcLoginState("state", "nonce", Instant.parse("2026-05-11T00:00:00Z"));
		final OidcLoginStateStore stateStore = mock(OidcLoginStateStore.class);
		final OidcProviderMetadataResolver metadataResolver = mock(OidcProviderMetadataResolver.class);
		final OidcTokenClient tokenClient = mock(OidcTokenClient.class);
		final OidcIdTokenValidator idTokenValidator = mock(OidcIdTokenValidator.class);
		final OIDCProviderMetadata metadata = mock(OIDCProviderMetadata.class);
		final OIDCTokens tokens = mock(OIDCTokens.class);
		final OidcAuthenticatedIdentity identity = new OidcAuthenticatedIdentity("subject", "user", Set.of("nx-developer"));
		when(stateStore.consume("state")).thenReturn(loginState);
		when(metadataResolver.resolve()).thenReturn(metadata);
		when(tokenClient.exchangeAuthorizationCode(metadata, "code")).thenReturn(tokens);
		when(idTokenValidator.validate(metadata, tokens, loginState)).thenReturn(identity);

		final OidcAuthenticationService service = new OidcAuthenticationService(
				mock(OidcLoginStateGenerator.class),
				enabledConfig(),
				stateStore,
				mock(OidcAuthorizationUrlBuilder.class),
				metadataResolver,
				tokenClient,
				idTokenValidator);

		assertThat(service.completeLogin("code", "state"), equalTo(identity));
	}

	private OidcAuthenticationService service(
			final OidcLoginStateGenerator stateGenerator,
			final OidcConfig config,
			final OidcLoginStateStore stateStore,
			final OidcAuthorizationUrlBuilder authorizationUrlBuilder)
	{
		return new OidcAuthenticationService(
				stateGenerator,
				config,
				stateStore,
				authorizationUrlBuilder,
				mock(OidcProviderMetadataResolver.class),
				mock(OidcTokenClient.class),
				mock(OidcIdTokenValidator.class));
	}

	private OidcConfig enabledConfig()
	{
		final java.util.Properties properties = new java.util.Properties();
		properties.setProperty("oidc.enabled", "true");
		return new OidcConfig(properties);
	}
}
