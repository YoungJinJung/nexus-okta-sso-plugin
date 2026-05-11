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

	private static final URI LOGIN_SUCCESS_REDIRECT = URI.create("/");

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
			final OidcAuthenticatedIdentity identity = authenticationService.completeLogin(code, state);
			SecurityUtils.getSubject().login(new OidcAuthenticationToken(identity, remoteAddress(request)));
			return Response.seeOther(LOGIN_SUCCESS_REDIRECT).build();
		}
		catch (final OidcProtocolException | IllegalArgumentException e)
		{
			LOG.warn("OIDC callback failed: {}", e.getMessage());
			return Response.status(Response.Status.UNAUTHORIZED).entity("OIDC login failed").build();
		}
	}

	private String remoteAddress(final HttpServletRequest request)
	{
		return request == null ? null : request.getRemoteAddr();
	}
}
