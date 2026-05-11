# OIDC Development Plan

## Phase 1: Secure OIDC Foundation

- Add OIDC configuration parsing.
- Add immutable value objects for OIDC session state and authenticated identity.
- Add secure state/nonce generation using a cryptographic random source.
- Add group-to-role mapping parsing without requiring an Okta API token.
- Add unit tests for config parsing, state/nonce generation, and group mapping.

Exit criteria:

- `mvn test` passes.
- No secrets are logged.
- Missing required OIDC config fails closed.

Status: implemented and covered by unit tests.

## Phase 2: OIDC Protocol Client

- Add discovery loading from `${issuer}/.well-known/openid-configuration`.
- Build authorization request URL with `response_type=code`, configured scopes, `state`, and `nonce`.
- Exchange authorization code at the token endpoint.
- Validate ID token signature using JWKS.
- Validate issuer, audience, expiration, and nonce.
- Extract username and group claims.

Exit criteria:

- Unit tests cover invalid issuer, invalid audience, expired token, nonce mismatch, and missing groups.
- Token/code values are redacted from logs.

Status: discovery, authorization request URL generation, token exchange, ID token validation, and claims extraction are implemented. More negative-path ID token tests still need to be added.

## Phase 3: Nexus Web Integration

- Add `/okta/oidc/login` endpoint.
- Add `/okta/oidc/callback` endpoint.
- Store pending `state` and `nonce` server-side with TTL.
- On callback, create or hand off an authenticated Nexus subject.
- Preserve role resolution through the existing Shiro realm.

Exit criteria:

- Nexus 3.92.0 starts with the plugin.
- Login redirects to Okta.
- Callback validates and establishes a Nexus session.

Status: direct login and callback resources are implemented, with callback authentication handed to the existing realm through an OIDC Shiro token. Runtime smoke validation against Nexus 3.92.0 remains.

## Phase 4: OIDC RBAC

- Map OIDC group claims to Nexus role IDs.
- Preserve compatibility with local Nexus roles where possible.
- Remove the Okta Management API token requirement for group RBAC.
- Keep the Classic Authn API path disabled or legacy-only.

Exit criteria:

- User in mapped Okta group receives mapped Nexus role.
- User without mapped role is denied.
- Local Nexus roles and OIDC mapped roles have documented precedence.

Status: OIDC group claim mapping is implemented. The Classic Authn API path remains available for compatibility.

## Phase 5: Operational Hardening

- Add runtime documentation for Okta app setup.
- Add Docker/runtime secret injection guidance.
- Add smoke validation instructions for Nexus 3.92.0.
- Add migration notes from Classic Authn API to OIDC.

Exit criteria:

- OIDC setup can be repeated from docs.
- No client secret or token is baked into the Docker image.
- Smoke container exposes `Okta Auth Realm` and the OIDC endpoints.

Status: runtime setup documentation and environment-driven Docker configuration are implemented. Smoke validation confirms the direct login endpoint redirects to Okta authorize when OIDC environment variables are supplied.

## Phase 6: Logout and UI Entry Points

- Add OIDC-aware logout endpoint.
- Redirect to provider end-session endpoint when possible.
- Keep direct login/logout URLs documented.
- Evaluate Nexus UI button replacement separately because Nexus 3.92 UI assets are packaged inside runtime jars.

Exit criteria:

- Logout clears the local Nexus subject.
- Logout redirects to Okta end-session when an ID token and post-logout redirect URI are available.
- Runtime docs include login and logout URLs.

Status: logout endpoint is implemented and documented. UI button replacement remains a separate packaging task.

## Implementation Notes

- Prefer `com.nimbusds:oauth2-oidc-sdk` and `com.nimbusds:nimbus-jose-jwt` because Nexus 3.92.0 already ships Nimbus libraries.
- Keep the first web milestone simple: direct login endpoint first, polished Nexus UI integration later.
- Do not use implicit flow.
- Do not trust group names unless the ID token has passed full validation.
