package org.sonatype.nexus.plugins.okta.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Named
public class OktaAuthClientConfig
{
	private static final Logger LOG = LoggerFactory.getLogger(OktaAuthClientConfig.class);

	private static final String CFG_FILE = "nexus-okta-auth.properties";

	private static String OKTA_URL_KEY = "okta.org.url";

	private static String OKTA_API_KEY = "okta.api";
	private static String OKTA_API_DEFAULT = "/api/v1";
	private static String OKTA_API_TOKEN_KEY = "okta.api.token";
	private static String OKTA_GROUP_ROLE_MAPPING_KEY = "okta.group.role.mapping";

	private static String MFA_POLL_DELAY_KEY = "mfa.poll.delay";
	private static int MFA_POLL_DELAY_DEFAULT = 3000;
	private static String MFA_POLL_MAXRETRY_KEY = "mfa.poll.maxretry";
	private static int MFA_POLL_MAXRETRY_DEFAULT = 20;

	private final Properties configuration;

	public OktaAuthClientConfig()
	{
		configuration = new Properties();

		try (InputStream input = Files.newInputStream(Paths.get(System.getProperty("karaf.home", "."), "etc", CFG_FILE)))
		{
			LOG.info("Loading configuraton from '{}'.", CFG_FILE);

			configuration.load(input);
		} catch (final IOException e)
		{
			LOG.warn("Error reading '" + CFG_FILE + "' properties. Falling back to default configuration", e);
		}
	}

	public String getOktaOrgUrl()
	{
		return configuration.getProperty(OKTA_URL_KEY);
	}

	public String getOktaApi()
	{
		return configuration.getProperty(OKTA_API_KEY, OKTA_API_DEFAULT);
	}

	public String getOktaApiToken()
	{
		return configuration.getProperty(OKTA_API_TOKEN_KEY);
	}

	public Map<String, String> getOktaGroupRoleMapping()
	{
		final String mapping = configuration.getProperty(OKTA_GROUP_ROLE_MAPPING_KEY);
		if (mapping == null || mapping.isBlank())
		{
			return Collections.emptyMap();
		}

		final Map<String, String> result = new LinkedHashMap<>();
		for (final String entry : mapping.split(","))
		{
			final String[] parts = entry.split("=", 2);
			if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank())
			{
				LOG.warn("Ignoring invalid Okta group role mapping entry '{}'. Expected 'okta-group=nexus-role'.", entry);
				continue;
			}
			result.put(parts[0].trim(), parts[1].trim());
		}
		return result;
	}

	public int getMfaPollDelay()
	{
		return propertyAsInt(MFA_POLL_DELAY_KEY, MFA_POLL_DELAY_DEFAULT, 100, 60000);
	}

	public int getMfaPollMaxRetries()
	{
		return propertyAsInt(MFA_POLL_MAXRETRY_KEY, MFA_POLL_MAXRETRY_DEFAULT, 1, 60);
	}

	private int propertyAsInt(final String key, final int defaultValue, final int minValue, final int maxValue)
	{
		final String delay = configuration.getProperty(key);
		if (delay != null)
		{
			try
			{
				final int val = Integer.parseInt(delay);
				if (val < minValue)
				{
					throw new IllegalArgumentException(key + " must be equal or greater than " + minValue);
				}
				if (val > maxValue)
				{
					throw new IllegalArgumentException(key + " must be equal or less than " + maxValue);
				}

				return val;
			} catch (final RuntimeException ex)
			{
				LOG.warn("Error reading property '" + key + "'. Falling back to default configuration", ex);
				return defaultValue;
			}
		}
		return defaultValue;
	}

}
