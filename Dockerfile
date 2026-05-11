FROM sonatype/nexus3:3.92.0

USER root

ARG OKTA_ORG_URL=https://your-org-here.okta.com
ARG OKTA_GROUP_ROLE_MAPPING=

# install plugins
COPY target/nexus-okta-auth-plugin-0-SNAPSHOT.jar /tmp/nexus-okta-auth-plugin.jar
RUN mkdir -p /tmp/nexus-boot-patch/BOOT-INF/lib && \
    cd /tmp/nexus-boot-patch && \
    jar xf /opt/sonatype/nexus/bin/sonatype-nexus-repository-3.92.0-03.jar BOOT-INF/classpath.idx && \
    cp /tmp/nexus-okta-auth-plugin.jar BOOT-INF/lib/nexus-okta-auth-plugin.jar && \
    printf '%s\n' '- "BOOT-INF/lib/nexus-okta-auth-plugin.jar"' >> BOOT-INF/classpath.idx && \
    jar uf0 /opt/sonatype/nexus/bin/sonatype-nexus-repository-3.92.0-03.jar \
      BOOT-INF/lib/nexus-okta-auth-plugin.jar BOOT-INF/classpath.idx && \
    rm -rf /tmp/nexus-boot-patch /tmp/nexus-okta-auth-plugin.jar && \
    touch /opt/sonatype/nexus/etc/nexus-okta-auth.properties && \
    echo "okta.org.url=${OKTA_ORG_URL}" >> /opt/sonatype/nexus/etc/nexus-okta-auth.properties && \
    echo "okta.api.token=" >> /opt/sonatype/nexus/etc/nexus-okta-auth.properties && \
    echo "okta.group.role.mapping=${OKTA_GROUP_ROLE_MAPPING}" >> /opt/sonatype/nexus/etc/nexus-okta-auth.properties && \
    chown nexus:nexus -R /opt/sonatype/nexus

USER nexus
