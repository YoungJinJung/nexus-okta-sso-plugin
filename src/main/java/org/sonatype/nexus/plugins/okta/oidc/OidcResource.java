package org.sonatype.nexus.plugins.okta.oidc;

import java.net.URI;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.shiro.SecurityUtils;
import org.sonatype.nexus.rest.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named
@Singleton
@Path("/okta/oidc")
@Produces(MediaType.TEXT_PLAIN)
public class OidcResource
		implements Resource
{
	private static final Logger LOG = LoggerFactory.getLogger(OidcResource.class);

	private static final String SESSION_ID_TOKEN = OidcResource.class.getName() + ".idToken";

	private final OidcAuthenticationService authenticationService;

	@Inject
	public OidcResource(final OidcAuthenticationService authenticationService)
	{
		this.authenticationService = Objects.requireNonNull(authenticationService);
	}

	@GET
	@Path("login")
	public Response login()
	{
		try
		{
			return Response.seeOther(authenticationService.beginLogin()).build();
		}
		catch (final OidcProtocolException | IllegalArgumentException e)
		{
			LOG.warn("OIDC login could not be started: {}", e.getMessage());
			return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("OIDC login is unavailable").build();
		}
	}

	@GET
	@Path("callback")
	public Response callback(
			@QueryParam("code") final String code,
			@QueryParam("state") final String state,
			@QueryParam("error") final String error,
			@Context final HttpServletRequest request)
	{
		if (error != null && !error.isBlank())
		{
			LOG.warn("OIDC callback returned an error");
			return Response.status(Response.Status.UNAUTHORIZED).entity("OIDC login failed").build();
		}

		try
		{
			final OidcLoginResult loginResult = authenticationService.completeLogin(code, state);
			SecurityUtils.getSubject().login(new OidcAuthenticationToken(loginResult.getIdentity(), remoteAddress(request)));
			SecurityUtils.getSubject().getSession().setAttribute(SESSION_ID_TOKEN, loginResult.getIdToken());
			return Response.seeOther(applicationRoot(request)).build();
		}
		catch (final OidcProtocolException | IllegalArgumentException e)
		{
			LOG.warn("OIDC callback failed: {}", e.getMessage());
			return Response.status(Response.Status.UNAUTHORIZED).entity("OIDC login failed").build();
		}
	}

	@GET
	@Path("logout")
	public Response logout(@Context final HttpServletRequest request)
	{
		final Object idToken = SecurityUtils.getSubject().getSession(false) == null ? null
				: SecurityUtils.getSubject().getSession(false).getAttribute(SESSION_ID_TOKEN);
		URI endSessionUri = null;
		try
		{
			endSessionUri = authenticationService.endSession(idToken instanceof String ? (String) idToken : null);
		}
		catch (final OidcProtocolException | IllegalArgumentException e)
		{
			LOG.warn("OIDC logout could not create Okta end-session redirect: {}", e.getMessage());
		}
		SecurityUtils.getSubject().logout();
		return Response.seeOther(endSessionUri == null ? applicationRoot(request) : endSessionUri).build();
	}

	private String remoteAddress(final HttpServletRequest request)
	{
		return request == null ? null : request.getRemoteAddr();
	}

	private URI applicationRoot(final HttpServletRequest request)
	{
		if (request == null)
		{
			return URI.create("/");
		}
		final String scheme = headerOrDefault(request, "X-Forwarded-Proto", request.getScheme());
		final String host = headerOrDefault(request, "X-Forwarded-Host", request.getServerName());
		if (host.contains(":"))
		{
			return URI.create(scheme + "://" + host + "/");
		}
		final int port = request.getServerPort();
		final boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
		return URI.create(scheme + "://" + host + (defaultPort ? "" : ":" + port) + "/");
	}

	private String headerOrDefault(final HttpServletRequest request, final String header, final String defaultValue)
	{
		final String value = request.getHeader(header);
		return value == null || value.isBlank() ? defaultValue : value;
	}
}
