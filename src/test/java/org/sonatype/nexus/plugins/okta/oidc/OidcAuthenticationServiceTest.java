package org.sonatype.nexus.plugins.okta.oidc;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.Set;

import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.SubjectType;
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
		when(tokens.getIDTokenString()).thenReturn("id-token");

		final OidcAuthenticationService service = new OidcAuthenticationService(
				mock(OidcLoginStateGenerator.class),
				enabledConfig(),
				stateStore,
				mock(OidcAuthorizationUrlBuilder.class),
				metadataResolver,
				tokenClient,
				idTokenValidator);

		final OidcLoginResult result = service.completeLogin("code", "state");

		assertThat(result.getIdentity(), equalTo(identity));
		assertThat(result.getIdToken(), equalTo("id-token"));
	}

	@Test
	public void shouldBuildEndSessionUrl()
	{
		final OidcLoginStateGenerator stateGenerator = mock(OidcLoginStateGenerator.class);
		final OidcProviderMetadataResolver metadataResolver = mock(OidcProviderMetadataResolver.class);
		final OIDCProviderMetadata metadata = new OIDCProviderMetadata(
				new Issuer("https://example.okta.com/oauth2/default"),
				java.util.List.of(SubjectType.PUBLIC),
				URI.create("https://example.okta.com/oauth2/default/v1/keys"));
		metadata.setEndSessionEndpointURI(URI.create("https://example.okta.com/oauth2/default/v1/logout"));
		when(metadataResolver.resolve()).thenReturn(metadata);
		when(stateGenerator.generate()).thenReturn(new OidcLoginState("logout-state", "nonce", Instant.now()));

		final OidcAuthenticationService service = new OidcAuthenticationService(
				stateGenerator,
				enabledConfig(),
				mock(OidcLoginStateStore.class),
				mock(OidcAuthorizationUrlBuilder.class),
				metadataResolver,
				mock(OidcTokenClient.class),
				mock(OidcIdTokenValidator.class));

		final URI uri = service.endSession("id-token");

		assertThat(uri.getScheme(), equalTo("https"));
		assertThat(uri.getHost(), equalTo("example.okta.com"));
		assertThat(uri.getPath(), equalTo("/oauth2/default/v1/logout"));
		assertThat(uri.getRawQuery().contains("id_token_hint=id-token"), equalTo(true));
		assertThat(uri.getRawQuery().contains("post_logout_redirect_uri=https%3A%2F%2Fnexus.example.com%2F"), equalTo(true));
		assertThat(uri.getRawQuery().contains("state=logout-state"), equalTo(true));
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
		properties.setProperty("oidc.post.logout.redirect.uri", "https://nexus.example.com/");
		return new OidcConfig(properties);
	}
}
