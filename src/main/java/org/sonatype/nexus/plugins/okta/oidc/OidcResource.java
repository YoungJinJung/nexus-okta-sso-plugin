package org.sonatype.nexus.plugins.okta.oidc;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

	private final OidcAuthenticationService authenticationService;
	private final OidcLoginTicketStore loginTicketStore;

	@Inject
	public OidcResource(
			final OidcAuthenticationService authenticationService,
			final OidcLoginTicketStore loginTicketStore)
	{
		this.authenticationService = Objects.requireNonNull(authenticationService);
		this.loginTicketStore = Objects.requireNonNull(loginTicketStore);
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
			LOG.warn("OIDC login could not be started: {}", e.getMessage(), e);
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
			final String ticket = loginTicketStore.issue(loginResult.getIdentity());
			return Response.ok(sessionBootstrapPage(loginResult.getIdentity().getUsername(), ticket))
					.type(MediaType.TEXT_HTML_TYPE)
					.build();
		}
		catch (final OidcProtocolException | IllegalArgumentException e)
		{
			LOG.warn("OIDC callback failed: {}", e.getMessage(), e);
			return Response.status(Response.Status.UNAUTHORIZED).entity("OIDC login failed").build();
		}
	}

	@GET
	@Path("logout")
	public Response logout(@Context final HttpServletRequest request)
	{
		URI endSessionUri = null;
		try
		{
			endSessionUri = authenticationService.endSession(null);
		}
		catch (final OidcProtocolException | IllegalArgumentException e)
		{
			LOG.warn("OIDC logout could not create Okta end-session redirect: {}", e.getMessage());
		}
		return Response.seeOther(endSessionUri == null ? applicationRoot(request) : endSessionUri).build();
	}

	private String sessionBootstrapPage(final String username, final String ticket)
	{
		final String encodedUsername = base64(username);
		final String encodedTicket = base64(ticket);
		return """
				<!doctype html>
				<html>
				<head><meta charset="utf-8"><title>Nexus OIDC Login</title></head>
				<body>
				<script>
				(function () {
				  var body = new URLSearchParams();
				  body.set('username', '%s');
				  body.set('password', '%s');
				  fetch('/service/rapture/session', {
				    method: 'POST',
				    credentials: 'same-origin',
				    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
				    body: body
				  }).then(function (response) {
				    if (response.ok) {
				      window.location.replace('/');
				      return;
				    }
				    document.body.textContent = 'Nexus OIDC session creation failed.';
				  }).catch(function () {
				    document.body.textContent = 'Nexus OIDC session creation failed.';
				  });
				}());
				</script>
				</body>
				</html>
				""".formatted(encodedUsername, encodedTicket);
	}

	private String base64(final String value)
	{
		return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
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
