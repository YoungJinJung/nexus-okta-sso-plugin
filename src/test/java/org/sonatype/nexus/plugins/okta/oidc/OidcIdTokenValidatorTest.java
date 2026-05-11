package org.sonatype.nexus.plugins.okta.oidc;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThrows;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.openid.connect.sdk.SubjectType;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import org.junit.Before;
import org.junit.Test;

public class OidcIdTokenValidatorTest
{
	private OidcConfig config;
	private RSAKey rsaKey;
	private OIDCProviderMetadata metadata;

	@Before
	public void setup() throws Exception
	{
		config = new OidcConfig(properties());
		rsaKey = rsaKey();
		metadata = new OIDCProviderMetadata(
				new Issuer("https://example.okta.com/oauth2/default"),
				List.of(SubjectType.PUBLIC),
				java.net.URI.create("https://example.okta.com/oauth2/default/v1/keys"));
	}

	@Test
	public void shouldValidateSignedIdToken()
			throws Exception
	{
		final OidcAuthenticatedIdentity identity = validator().validate(
				metadata,
				tokens(idToken("nonce", List.of("Developers"))),
				new OidcLoginState("state", "nonce", java.time.Instant.now()));

		assertThat(identity.getUsername(), equalTo("alice@example.com"));
		assertThat(identity.getRoles(), equalTo(Set.of("nx-developer")));
	}

	@Test
	public void shouldRejectNonceMismatch()
			throws Exception
	{
		assertThrows(OidcProtocolException.class, () -> validator().validate(
				metadata,
				tokens(idToken("other-nonce", List.of("Developers"))),
				new OidcLoginState("state", "nonce", java.time.Instant.now())));
	}

	@Test
	public void shouldRejectIdTokenWithoutMappedGroup()
			throws Exception
	{
		assertThrows(OidcProtocolException.class, () -> validator().validate(
				metadata,
				tokens(idToken("nonce", List.of("Ignored"))),
				new OidcLoginState("state", "nonce", java.time.Instant.now())));
	}

	private OidcIdTokenValidator validator()
	{
		return new OidcIdTokenValidator(config, new OidcIdentityExtractor(config))
		{
			@Override
			protected IDTokenValidator createValidator(final OIDCProviderMetadata metadata)
			{
				return new IDTokenValidator(
						new Issuer(config.getIssuer().toString()),
						new ClientID(config.getClientId()),
						JWSAlgorithm.RS256,
						new JWKSet(rsaKey.toPublicJWK()));
			}
		};
	}

	private SignedJWT idToken(final String nonce, final List<String> groups) throws JOSEException
	{
		final JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(config.getIssuer().toString())
				.subject("okta-subject")
				.audience(config.getClientId())
				.expirationTime(new Date(System.currentTimeMillis() + 60_000))
				.issueTime(new Date())
				.claim("nonce", nonce)
				.claim("preferred_username", "alice@example.com")
				.claim("groups", groups)
				.build();
		final SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
				claims);
		jwt.sign(new RSASSASigner(rsaKey));
		return jwt;
	}

	private OIDCTokens tokens(final SignedJWT idToken)
	{
		return new OIDCTokens(idToken, new BearerAccessToken("access-token"), null);
	}

	private RSAKey rsaKey() throws Exception
	{
		final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		final KeyPair keyPair = generator.generateKeyPair();
		return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
				.privateKey((RSAPrivateKey) keyPair.getPrivate())
				.keyID("test-key")
				.build();
	}

	private Properties properties()
	{
		final Properties properties = new Properties();
		properties.setProperty("oidc.enabled", "true");
		properties.setProperty("oidc.issuer", "https://example.okta.com/oauth2/default");
		properties.setProperty("oidc.client.id", "client-id");
		properties.setProperty("oidc.client.secret", "client-secret");
		properties.setProperty("oidc.redirect.uri", "https://nexus.example.com/service/rest/okta/oidc/callback");
		properties.setProperty("oidc.group.role.mapping", "Developers=nx-developer");
		return properties;
	}

}
