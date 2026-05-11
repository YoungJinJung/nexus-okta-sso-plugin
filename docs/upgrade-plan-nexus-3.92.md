# Nexus 3.92.0 Upgrade Plan

## Goal

Make this Okta authentication realm plugin compatible with Sonatype Nexus Repository 3.92.0 while keeping the plugin small and focused on authentication.

## Current State

- The original project targeted `org.sonatype.nexus.plugins:nexus-plugins:3.29.2-02`.
- The original build targeted Java 8.
- Nexus 3.92.0 requires Java 21 at runtime.
- The `3.88.0-08` Nexus plugin parent requires Maven 3.9 or newer.
- Sonatype's Maven Central artifacts currently publish `nexus-plugins` and `nexus-plugin-api` through `3.88.0-08`; a `3.92.0-*` plugin parent was not available when this plan was written.

## Upgrade Strategy

1. Move the build to Java 21.
2. Require Maven 3.9 or newer for local and CI builds.
3. Move the Nexus plugin parent to the newest publicly available parent, `3.88.0-08`.
4. Keep the Docker runtime image pinned to `sonatype/nexus3:3.92.0` so runtime testing happens against the target Nexus version.
5. Verify compilation and unit tests locally.
6. Verify the packaged bundle inside Nexus 3.92.0:
   - plugin jar is added to the Nexus Spring Boot runtime jar under `BOOT-INF/lib/nexus-okta-auth-plugin.jar`
   - `BOOT-INF/classpath.idx` includes the plugin jar
   - "Okta Auth Realm" appears under Security > Realms
   - username/password authentication works against Okta
   - local Nexus roles are still resolved through `UserManager`
7. When Sonatype publishes `3.92.0-*` Maven artifacts, update the parent from `3.88.0-08` to the matching `3.92.0-*` version and repeat compile, unit, and Docker runtime verification.

## Compatibility Risks

- Nexus plugin APIs between 3.88.0 and 3.92.0 may have internal changes not visible at compile time.
- Realm discovery may change if Nexus adjusts Sisu, Shiro, or Karaf bundle wiring.
- Nexus 3.92.0 no longer exposes the legacy Karaf `startup.properties` path used by the original Dockerfile. The Dockerfile patches the Spring Boot runtime jar so the plugin is loaded by the same application classloader as Nexus.
- Okta `/api/v1/authn` is the Classic Authn API. It should be treated as a separate Okta compatibility concern from Nexus compatibility.

## Validation Commands

```bash
mvn -version # must be Maven 3.9+ and Java 21
mvn test
mvn package
docker build -t nexus-okta-auth-plugin:nexus-3.92.0 .
docker run --rm -p 8081:8081 nexus-okta-auth-plugin:nexus-3.92.0
```

## Validation Results

- `mvn test package` passed with Apache Maven 3.9.11 and Java 21.
- `docker build -t nexus-okta-auth-plugin:nexus-3.92.0 .` passed.
- A Nexus 3.92.0 smoke container started successfully.
- The Nexus realms API listed `org.sonatype.nexus.plugins.okta.OktaAuthRealm` with the display name `Okta Auth Realm`.
- Live Okta authentication was not exercised because test Okta credentials were not available locally.

## Okta Group RBAC

The plugin can map Okta groups to Nexus role IDs after successful authentication.

Configure these properties in `nexus-okta-auth.properties`:

```properties
okta.api.token=<okta-api-token-with-group-read-access>
okta.group.role.mapping=Okta Admins=nx-admin,Developers=nx-developer
```

The mapping is additive: roles from mapped Okta groups are combined with roles from an existing local Nexus user when one exists. If the local Nexus user does not exist, mapped Okta roles are still used.

Do not bake the Okta API token into a published Docker image. Prefer mounting or otherwise injecting `nexus-okta-auth.properties` at runtime for real deployments.

## Follow-Up When 3.92.0 Artifacts Are Public

1. Check Maven metadata for `org.sonatype.nexus.plugins:nexus-plugins`.
2. Replace the parent version in `pom.xml`.
3. Re-run `mvn test` and `mvn package`.
4. Rebuild and boot the Docker image.
5. Update this document with the exact artifact version and test result.
