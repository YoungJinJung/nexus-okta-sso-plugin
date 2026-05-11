#!/bin/sh
set -eu

CONFIG_FILE=/opt/sonatype/nexus/etc/nexus-okta-auth.properties

write_config() {
  mkdir -p "$(dirname "$CONFIG_FILE")"
  : > "$CONFIG_FILE"
  {
    printf 'okta.org.url=%s\n' "${OKTA_ORG_URL:-https://your-org-here.okta.com}"
    printf 'okta.api.token=%s\n' "${OKTA_API_TOKEN:-}"
    printf 'okta.group.role.mapping=%s\n' "${OKTA_GROUP_ROLE_MAPPING:-}"
    printf 'oidc.enabled=%s\n' "${OIDC_ENABLED:-false}"
    printf 'oidc.issuer=%s\n' "${OIDC_ISSUER:-}"
    printf 'oidc.client.id=%s\n' "${OIDC_CLIENT_ID:-}"
    printf 'oidc.client.secret=%s\n' "${OIDC_CLIENT_SECRET:-}"
    printf 'oidc.redirect.uri=%s\n' "${OIDC_REDIRECT_URI:-}"
    printf 'oidc.scopes=%s\n' "${OIDC_SCOPES:-openid,profile,email,groups}"
    printf 'oidc.username.claim=%s\n' "${OIDC_USERNAME_CLAIM:-preferred_username}"
    printf 'oidc.groups.claim=%s\n' "${OIDC_GROUPS_CLAIM:-groups}"
    printf 'oidc.group.role.mapping=%s\n' "${OIDC_GROUP_ROLE_MAPPING:-}"
  } >> "$CONFIG_FILE"
}

write_config
exec /opt/sonatype/nexus/bin/nexus run
