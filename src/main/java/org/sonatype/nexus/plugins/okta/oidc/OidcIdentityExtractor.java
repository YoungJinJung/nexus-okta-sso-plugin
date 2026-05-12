package org.sonatype.nexus.plugins.okta.oidc;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Named
public class OidcIdentityExtractor
{
	private static final Logger log = LoggerFactory.getLogger(OidcIdentityExtractor.class);

	private final OidcConfig config;
	private final OidcRoleMapper roleMapper;

	@Inject
	public OidcIdentityExtractor(final OidcConfig config)
	{
		this(config, new OidcRoleMapper(config));
	}

	public OidcIdentityExtractor(final OidcConfig config, final OidcRoleMapper roleMapper)
	{
		this.config = Objects.requireNonNull(config);
		this.roleMapper = Objects.requireNonNull(roleMapper);
	}

	public OidcAuthenticatedIdentity extract(final IDTokenClaimsSet claims)
	{
		Objects.requireNonNull(claims);

		final String username = claims.getStringClaim(config.getUsernameClaim());
		final List<String> groups = claims.getStringListClaim(config.getGroupsClaim());
		final Set<String> roles = roleMapper.mapGroupsToRoles(groups == null ? List.of() : groups);
		if (roles.isEmpty())
		{
			log.warn(
					"OIDC user '{}' has no mapped Nexus role. groups claim '{}': {}, configured Okta groups: {}",
					username,
					config.getGroupsClaim(),
					groups == null ? List.of() : groups,
					config.getGroupRoleMapping().keySet());
			throw new OidcProtocolException("OIDC user has no mapped Nexus role");
		}
		if (username == null || username.isBlank())
		{
			throw new OidcProtocolException("OIDC username claim is missing");
		}
		return new OidcAuthenticatedIdentity(claims.getSubject().getValue(), username, roles);
	}
}
