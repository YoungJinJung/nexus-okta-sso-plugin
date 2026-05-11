package org.sonatype.nexus.plugins.okta.oidc;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Named
public class OidcConfig
{
	private static final Logger LOG = LoggerFactory.getLogger(OidcConfig.class);

	private static final String CFG_FILE = "nexus-okta-auth.properties";
	private static final String ENABLED_KEY = "oidc.enabled";
	private static final String ISSUER_KEY = "oidc.issuer";
	private static final String CLIENT_ID_KEY = "oidc.client.id";
	private static final String CLIENT_SECRET_KEY = "oidc.client.secret";
	private static final String REDIRECT_URI_KEY = "oidc.redirect.uri";
	private static final String SCOPES_KEY = "oidc.scopes";
	private static final String USERNAME_CLAIM_KEY = "oidc.username.claim";
	private static final String GROUPS_CLAIM_KEY = "oidc.groups.claim";
	private static final String GROUP_ROLE_MAPPING_KEY = "oidc.group.role.mapping";

	private static final String SCOPES_DEFAULT = "openid,profile,email,groups";
	private static final String USERNAME_CLAIM_DEFAULT = "preferred_username";
	private static final String GROUPS_CLAIM_DEFAULT = "groups";

	private final Properties configuration;

	@Inject
	public OidcConfig()
	{
		this(loadConfiguration());
	}

	public OidcConfig(final Properties configuration)
	{
		this.configuration = configuration;
	}

	public boolean isEnabled()
	{
		return Boolean.parseBoolean(configuration.getProperty(ENABLED_KEY, "false"));
	}

	public URI getIssuer()
	{
		return uri(ISSUER_KEY);
	}

	public String getClientId()
	{
		return required(CLIENT_ID_KEY);
	}

	public String getClientSecret()
	{
		return required(CLIENT_SECRET_KEY);
	}

	public URI getRedirectUri()
	{
		return uri(REDIRECT_URI_KEY);
	}

	public List<String> getScopes()
	{
		return split(configuration.getProperty(SCOPES_KEY, SCOPES_DEFAULT));
	}

	public String getUsernameClaim()
	{
		return configuration.getProperty(USERNAME_CLAIM_KEY, USERNAME_CLAIM_DEFAULT);
	}

	public String getGroupsClaim()
	{
		return configuration.getProperty(GROUPS_CLAIM_KEY, GROUPS_CLAIM_DEFAULT);
	}

	public Map<String, String> getGroupRoleMapping()
	{
		final String mapping = configuration.getProperty(GROUP_ROLE_MAPPING_KEY);
		if (mapping == null || mapping.isBlank())
		{
			return Collections.emptyMap();
		}

		final Map<String, String> result = new LinkedHashMap<>();
		for (final String entry : split(mapping))
		{
			final String[] parts = entry.split("=", 2);
			if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank())
			{
				LOG.warn("Ignoring invalid OIDC group role mapping entry '{}'. Expected 'okta-group=nexus-role'.", entry);
				continue;
			}
			result.put(parts[0].trim(), parts[1].trim());
		}
		return result;
	}

	public void validate()
	{
		if (!isEnabled())
		{
			return;
		}

		getIssuer();
		getClientId();
		getClientSecret();
		getRedirectUri();
		if (!getScopes().contains("openid"))
		{
			throw new IllegalArgumentException("oidc.scopes must include openid");
		}
		if (getGroupRoleMapping().isEmpty())
		{
			throw new IllegalArgumentException("oidc.group.role.mapping must define at least one mapping");
		}
	}

	private URI uri(final String key)
	{
		final String value = required(key);
		final URI uri = URI.create(value);
		if (!uri.isAbsolute())
		{
			throw new IllegalArgumentException(key + " must be an absolute URI");
		}
		return uri;
	}

	private String required(final String key)
	{
		final String value = configuration.getProperty(key);
		if (value == null || value.isBlank())
		{
			throw new IllegalArgumentException(key + " is required when OIDC is enabled");
		}
		return value.trim();
	}

	private static List<String> split(final String value)
	{
		if (value == null || value.isBlank())
		{
			return Collections.emptyList();
		}
		return Arrays.stream(value.split(","))
				.map(String::trim)
				.filter(item -> !item.isEmpty())
				.collect(Collectors.toList());
	}

	private static Properties loadConfiguration()
	{
		final Properties configuration = new Properties();
		try (InputStream input = Files.newInputStream(Paths.get(System.getProperty("karaf.home", "."), "etc", CFG_FILE)))
		{
			LOG.info("Loading OIDC configuration from '{}'.", CFG_FILE);
			configuration.load(input);
		}
		catch (final IOException e)
		{
			LOG.warn("Error reading '{}' properties. OIDC will stay disabled unless configured elsewhere.", CFG_FILE, e);
		}
		return configuration;
	}
}
