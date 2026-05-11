package org.sonatype.nexus.plugins.okta;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.shiro.authc.AccountException;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.pam.UnsupportedTokenException;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.sonatype.nexus.common.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.sonatype.nexus.plugins.okta.client.OktaAuthClient;
import org.sonatype.nexus.plugins.okta.client.OktaAuthClientException;
import org.sonatype.nexus.plugins.okta.client.OktaAuthClientExceptionSeverity;
import org.sonatype.nexus.plugins.okta.client.dto.OktaAuthResponse;
import org.sonatype.nexus.plugins.okta.oidc.OidcAuthenticatedIdentity;
import org.sonatype.nexus.plugins.okta.oidc.OidcAuthenticationToken;
import org.sonatype.nexus.security.role.RoleIdentifier;
import org.sonatype.nexus.security.user.UserManager;
import org.sonatype.nexus.security.user.UserNotFoundException;

@Singleton
@Named(OktaAuthRealm.NAME)
@Qualifier(OktaAuthRealm.NAME)
@Description("Okta Auth Realm")
public class OktaAuthRealm extends AuthorizingRealm
{
	private static final Logger LOG = LoggerFactory.getLogger(OktaAuthRealm.class);
	public static final String NAME = "org.sonatype.nexus.plugins.okta.OktaAuthRealm";

	private final OktaAuthClient client;
	private final UserManager userManager;
	private final Map<String, Set<String>> oktaMappedRolesByUser = new ConcurrentHashMap<>();

	@Inject
	public OktaAuthRealm(final OktaAuthClient client, final UserManager userManager)
	{
		this.client = Objects.requireNonNull(client);
		this.userManager = Objects.requireNonNull(userManager);

		LOG.info("Okta Auth Realm for {} initialized...", this.client.getConfig().getOktaOrgUrl());
	}

	@Override
	public boolean supports(final AuthenticationToken token)
	{
		return token instanceof UsernamePasswordToken || token instanceof OidcAuthenticationToken;
	}

	@Override
	protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken token) throws AuthenticationException
	{
		if (token instanceof OidcAuthenticationToken)
		{
			final OidcAuthenticatedIdentity identity = ((OidcAuthenticationToken) token).getIdentity();
			oktaMappedRolesByUser.put(identity.getUsername(), new HashSet<>(identity.getRoles()));
			LOG.info("Authenticated OIDC user {}", identity.getUsername());
			return new SimpleAuthenticationInfo(identity.getUsername(), token.getCredentials(), getName());
		}

		if (!(token instanceof UsernamePasswordToken))
		{
			throw new UnsupportedTokenException(String.format("Token of type '%s' is not supported. '%s' is required.",
					token.getClass().getName(), UsernamePasswordToken.class.getName()));
		}

		final UsernamePasswordToken t = (UsernamePasswordToken) token;
		final String password = new String(t.getPassword());

		LOG.info("Authenticating with Okta for user {}", t.getUsername());

		try
		{
			final OktaAuthResponse response = client.authn(t.getUsername(), password);
			oktaMappedRolesByUser.put(t.getUsername(), new HashSet<>(client.getMappedRoles(response)));
			return new SimpleAuthenticationInfo(t.getUsername(), token.getCredentials(), getName());

		} catch (final OktaAuthClientException ex)
		{
			if (OktaAuthClientExceptionSeverity.INFO.equals(ex.getSeverity()))
			{
				LOG.info("Authentication for '" + t.getUsername() + "' was not successful: " + ex.getMessage());
			} else if (OktaAuthClientExceptionSeverity.WARN.equals(ex.getSeverity()))
			{
				LOG.warn("Authentication for '" + t.getUsername() + "' was not successful: " + ex.getMessage());
				throw new AccountException(ex.getMessage(), ex);
			} else
			{
				LOG.error("Authentication for '" + t.getUsername() + "' was not successful: " + ex.getMessage());
				throw new AccountException(ex.getMessage(), ex);
			}
		} catch (final Exception ex)
		{
			LOG.error("Unexpected authentication error: " + ex.getMessage(), ex);
			throw new AuthenticationException(ex.getMessage(), ex);
		}

		return null;
	}

	@Override
	protected AuthorizationInfo doGetAuthorizationInfo(final PrincipalCollection principals)
	{
		final Object principal = principals.getPrimaryPrincipal();
		if (!(principal instanceof String))
		{
			LOG.error("Expected principal of type String but was {}", principal == null ? "null"
					: principal.getClass().getName());
			return null;
		}

		final Set<String> roles = new HashSet<>();
		try {
			for (final RoleIdentifier roleIdentifier : userManager.getUser((String) principal).getRoles()) {
				roles.add(roleIdentifier.getRoleId());
	        }
		}
		catch (final UserNotFoundException e) {
			LOG.info("No local Nexus user found for principal '{}'. Falling back to mapped Okta roles.",
					principals.getPrimaryPrincipal());
		}

		roles.addAll(oktaMappedRolesByUser.getOrDefault((String) principal, Set.of()));
		if (roles.isEmpty())
		{
			throw new AuthorizationException("No Nexus or mapped Okta roles found for principals: "
					+ principals.getPrimaryPrincipal());
		}
		return new SimpleAuthorizationInfo(roles);
	}

	@Override
	public String getName()
	{
		return NAME;
	}
}
