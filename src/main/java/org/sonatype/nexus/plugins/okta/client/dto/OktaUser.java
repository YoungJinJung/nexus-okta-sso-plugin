package org.sonatype.nexus.plugins.okta.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OktaUser
{
	private String id;

	public String getId()
	{
		return id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}
}
