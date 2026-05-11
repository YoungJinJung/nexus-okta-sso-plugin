package org.sonatype.nexus.plugins.okta.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OktaGroup
{
	private OktaGroupProfile profile;

	public OktaGroupProfile getProfile()
	{
		return profile;
	}

	public void setProfile(final OktaGroupProfile profile)
	{
		this.profile = profile;
	}
}
