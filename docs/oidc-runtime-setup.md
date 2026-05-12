# OIDC Runtime Setup

## Okta Application

Create an Okta OIDC web application.

Use Authorization Code flow and configure the sign-in redirect URI to the Nexus callback:

```text
https://<nexus-host>/service/rest/okta/oidc/callback
```

Configure a `groups` claim in the ID token. Prefer a filtered claim that only emits Nexus-related groups.

## Nexus Container

The container writes `${karaf.home}/etc/nexus-okta-auth.properties` at startup from environment variables. In the Nexus 3.92 image, `${karaf.home}` is `/opt/sonatype/nexus`.

Example:

```bash
docker run \
  -p 8081:8081 \
  -e OIDC_ENABLED=true \
  -e OIDC_ISSUER=https://your-org.okta.com/oauth2/default \
  -e OIDC_CLIENT_ID=<client-id> \
  -e OIDC_CLIENT_SECRET=<client-secret> \
  -e OIDC_REDIRECT_URI=https://<nexus-host>/service/rest/okta/oidc/callback \
  -e OIDC_POST_LOGOUT_REDIRECT_URI=https://<nexus-host>/ \
  -e OIDC_GROUP_ROLE_MAPPING='Developers=nx-developer,Okta Admins=nx-admin' \
  nexus-okta-auth-plugin:oidc-dev
```

Use your runtime secret manager for `OIDC_CLIENT_SECRET`. Do not bake it into the image.

## Docker Compose

Copy the example env file and fill in Okta values:

```bash
cp .env.example .env.local
```

Start Nexus:

```bash
docker compose up -d
```

On Apple Silicon the compose defaults use:

```text
NEXUS_OKTA_IMAGE_TAG=local-arm64
NEXUS_PLATFORM=linux/arm64
```

For an x86 image, set these in `.env.local`:

```text
NEXUS_OKTA_IMAGE_TAG=local-amd64
NEXUS_PLATFORM=linux/amd64
```

The compose file persists Nexus data in the `nexus-okta-data` volume.

## Login URL

Start login at:

```text
https://<nexus-host>/service/rest/okta/oidc/login
```

The endpoint creates a server-side `state` and `nonce`, then redirects to Okta.

## Logout URL

Start OIDC-aware logout at:

```text
https://<nexus-host>/service/rest/okta/oidc/logout
```

The endpoint clears the Nexus subject and, when `OIDC_POST_LOGOUT_REDIRECT_URI` is configured and the provider exposes an end-session endpoint, redirects to Okta logout with `id_token_hint`, `post_logout_redirect_uri`, and `state`.

## Required Realm

Enable the Okta Auth Realm in Nexus security realms. The OIDC callback authenticates through that realm using a verified OIDC identity.

## RBAC

Map Okta group names to Nexus role IDs:

```text
OIDC_GROUP_ROLE_MAPPING='Developers=nx-developer,Okta Admins=nx-admin'
```

Users without a mapped role are denied.

## Current Limitations

- Direct login URL is implemented; Nexus UI login button integration is not.
- Callback flow is implemented, but it still needs validation against a real Okta tenant.
- Nexus UI login/logout button replacement is not bundled yet; use the direct login/logout URLs above.
