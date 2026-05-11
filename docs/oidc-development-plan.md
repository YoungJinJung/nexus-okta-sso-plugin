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

Status: authorization request URL generation is implemented. Discovery, token exchange, and ID token validation remain.

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

## Phase 4: OIDC RBAC

- Map OIDC group claims to Nexus role IDs.
- Preserve compatibility with local Nexus roles where possible.
- Remove the Okta Management API token requirement for group RBAC.
- Keep the Classic Authn API path disabled or legacy-only.

Exit criteria:

- User in mapped Okta group receives mapped Nexus role.
- User without mapped role is denied.
- Local Nexus roles and OIDC mapped roles have documented precedence.

## Phase 5: Operational Hardening

- Add runtime documentation for Okta app setup.
- Add Docker/runtime secret injection guidance.
- Add smoke validation instructions for Nexus 3.92.0.
- Add migration notes from Classic Authn API to OIDC.

Exit criteria:

- OIDC setup can be repeated from docs.
- No client secret or token is baked into the Docker image.
- Smoke container exposes `Okta Auth Realm` and the OIDC endpoints.

## Implementation Notes

- Prefer `com.nimbusds:oauth2-oidc-sdk` and `com.nimbusds:nimbus-jose-jwt` because Nexus 3.92.0 already ships Nimbus libraries.
- Keep the first web milestone simple: direct login endpoint first, polished Nexus UI integration later.
- Do not use implicit flow.
- Do not trust group names unless the ID token has passed full validation.
