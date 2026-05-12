# Nexus Okta Auth Plugin

Okta authentication plugin for Sonatype Nexus Repository OSS.

This fork targets Nexus Repository `3.92.0` and supports Okta OIDC Authorization Code login with group-to-role RBAC. The older Okta Authn API username/password flow is still present for compatibility, but new deployments should prefer OIDC so Nexus never handles the user's Okta password directly.

## Features

- Nexus `3.92.0` Docker image support.
- Okta OIDC Authorization Code login.
- Direct login endpoint:

```text
/service/rest/okta/oidc/login
```

- OIDC callback endpoint:

```text
/service/rest/okta/oidc/callback
```

- OIDC-aware logout endpoint:

```text
/service/rest/okta/oidc/logout
```

- Okta `groups` claim mapping to Nexus role IDs.
- Local Nexus user auto-provisioning for OIDC users.

## OIDC Login Flow

1. The user opens the Nexus OIDC login URL.
2. Nexus creates a server-side `state` and `nonce`.
3. Nexus redirects the browser to Okta.
4. Okta authenticates the user and redirects back to the Nexus callback with an authorization code.
5. Nexus exchanges the code for tokens, validates the ID token, reads the username and groups claims, maps groups to Nexus roles, and creates or updates the local Nexus user.
6. Nexus creates the UI web session and redirects the user to `/`.

More detail is in [docs/oidc-login-flow.md](docs/oidc-login-flow.md).

## Okta Application Setup

Create an Okta OIDC web application.

Use Authorization Code flow and configure:

```text
Sign-in redirect URI:
https://<nexus-host>/service/rest/okta/oidc/callback

Initiate login URI:
https://<nexus-host>/service/rest/okta/oidc/login

Sign-out redirect URI:
https://<nexus-host>/
```

To launch Nexus from the Okta dashboard, make the app visible to users and assign the app to the required users or groups. If the tile does not appear in the Okta dashboard, check Okta app assignment and app visibility settings first.

Configure a `groups` claim in the ID token. Prefer a filtered claim that only emits Nexus-related groups.

For Okta org authorization server setups, the issuer usually looks like:

```text
https://your-org.okta.com
```

For a custom authorization server, it usually looks like:

```text
https://your-org.okta.com/oauth2/default
```

Use the issuer that matches the Okta application and token configuration you actually use.

## Nexus Runtime Configuration

The Docker image writes `/opt/sonatype/nexus/etc/nexus-okta-auth.properties` at container start from environment variables.

Required OIDC variables:

```text
OIDC_ENABLED=true
OIDC_ISSUER=https://your-org.okta.com
OIDC_CLIENT_ID=<client-id>
OIDC_CLIENT_SECRET=<client-secret>
OIDC_REDIRECT_URI=https://<nexus-host>/service/rest/okta/oidc/callback
OIDC_POST_LOGOUT_REDIRECT_URI=https://<nexus-host>/
OIDC_GROUP_ROLE_MAPPING=Developers=nx-developer,Okta Admins=nx-admin
```

Optional variables:

```text
OIDC_SCOPES=openid,profile,email,groups
OIDC_USERNAME_CLAIM=preferred_username
OIDC_GROUPS_CLAIM=groups
```

Do not bake `OIDC_CLIENT_SECRET` into an image. Inject it through Docker env files, ECS secrets, Kubernetes secrets, or your runtime secret manager.

## Nexus Realm Activation

After the plugin is installed, enable the realm in Nexus:

1. Log in to Nexus as an administrator.
2. Open `Administration > Security > Realms`.
3. Move `Okta Auth Realm` to the active realms list.
4. Keep the standard Nexus realms active unless you intentionally want to remove local login:

```text
NexusAuthenticatingRealm
NexusAuthorizingRealm
Okta Auth Realm
```

This step is required. The OIDC callback endpoint can exist even when the realm is inactive, but Nexus cannot create the final UI session unless `Okta Auth Realm` is active.

## RBAC

Map Okta group names to Nexus role IDs:

```text
OIDC_GROUP_ROLE_MAPPING=Developers=nx-developer,Okta Admins=nx-admin
```

Users without at least one mapped role are denied. The plugin also provisions a local Nexus user with the mapped roles so Nexus screens such as `My Account` work after OIDC login.

## Docker Build

Build the plugin jar:

```bash
mvn clean package
```

Build an Apple Silicon image:

```bash
docker build --platform linux/arm64 -t nexus-okta-auth-plugin:local-arm64 .
```

Build an x86_64 Linux image:

```bash
docker build --platform linux/amd64 -t nexus-okta-auth-plugin:local-amd64 .
```

For ECR-style tagging:

```bash
docker build --platform linux/amd64 \
  -t 768499315087.dkr.ecr.ap-northeast-2.amazonaws.com/nexus:3.92.0-oidc-<git-sha> .
```

## Docker Compose

Copy the example env file and fill in Okta values:

```bash
cp .env.example .env.local
```

Start Nexus:

```bash
docker compose up -d
```

The compose file exposes Nexus at:

```text
http://localhost:18081
```

Use the local OIDC login URL:

```text
http://localhost:18081/service/rest/okta/oidc/login
```

On Apple Silicon, the compose defaults use:

```text
NEXUS_OKTA_IMAGE_TAG=local-arm64
NEXUS_PLATFORM=linux/arm64
```

For an x86 image, set these in `.env.local`:

```text
NEXUS_OKTA_IMAGE_TAG=local-amd64
NEXUS_PLATFORM=linux/amd64
```

The compose file persists Nexus data in the `nexus-okta-data` volume. Nexus stores its own data under `/nexus-data`; no extra database is required for local compose testing.

## Manual Installation

For non-Docker installs, build the jar and copy it into the Nexus installation.

```bash
mvn clean package
cp target/nexus-okta-auth-plugin-0-SNAPSHOT.jar /opt/sonatype/nexus/system/nexus-okta-auth-plugin.jar
```

Nexus `3.92.0` is Spring Boot based, so the Docker image patches the Nexus boot jar classpath rather than using the older Karaf `startup.properties` plugin loading model. Prefer the provided Dockerfile for repeatable deployments.

Create `/opt/sonatype/nexus/etc/nexus-okta-auth.properties` with the equivalent properties if you run outside the provided container:

```properties
oidc.enabled=true
oidc.issuer=https://your-org.okta.com
oidc.client.id=<client-id>
oidc.client.secret=<client-secret>
oidc.redirect.uri=https://<nexus-host>/service/rest/okta/oidc/callback
oidc.post.logout.redirect.uri=https://<nexus-host>/
oidc.scopes=openid,profile,email,groups
oidc.username.claim=preferred_username
oidc.groups.claim=groups
oidc.group.role.mapping=Developers=nx-developer,Okta Admins=nx-admin
```

## Troubleshooting

### Login returns `OIDC login is unavailable`

Check that OIDC is enabled and all required OIDC values are present. Also verify that the issuer exposes OIDC discovery metadata.

### Callback returns `OIDC login failed`

Check Nexus logs for the detailed reason. Common causes are an invalid client secret, mismatched redirect URI, invalid issuer, missing username claim, missing groups claim, or no mapped Nexus role.

### Page shows `Nexus OIDC session creation failed`

Okta authentication and callback already succeeded, but Nexus could not create the UI session. First check `Administration > Security > Realms` and confirm `Okta Auth Realm` is active.

If Nexus runs with more than one task or pod, also ensure the callback and the following `/service/rapture/session` request reach the same instance, or replace the in-memory login ticket store with a shared store.

### Okta dashboard tile does not appear

Check that the Okta OIDC app is assigned to the user or one of the user's groups, and that the app is not hidden from end users. The app's initiate login URI should point to:

```text
https://<nexus-host>/service/rest/okta/oidc/login
```

## Development

Run unit tests:

```bash
mvn clean test
```

Run integration tests against Okta only when you have a test tenant and credentials:

```bash
mvn verify -Pintegration-test \
  -DoktaTestOrgUrl=https://your-org.okta.com \
  -DoktaTestUserName=your-email \
  -DoktaTestUserPassword=your-password
```

## Documentation

- [Nexus 3.92 upgrade plan](docs/upgrade-plan-nexus-3.92.md)
- [OIDC login flow](docs/oidc-login-flow.md)
- [OIDC runtime setup](docs/oidc-runtime-setup.md)
- [OIDC development plan](docs/oidc-development-plan.md)

## Contributions

Some parts of this project were inspired by the [Nexus3 Crowd Plugin](https://github.com/pingunaut/nexus3-crowd-plugin), released under the Apache License 2.0 by [pingunaut](https://github.com/pingunaut).

This is a plugin for Sonatype Nexus Open Source Version, Copyright (c) 2008-present Sonatype, Inc.
