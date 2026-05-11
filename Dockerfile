FROM sonatype/nexus3:3.92.0

USER root

# install plugins
COPY target/nexus-okta-auth-plugin-0-SNAPSHOT.jar /tmp/nexus-okta-auth-plugin.jar
COPY docker/run-with-okta-config.sh /opt/sonatype/nexus/bin/run-with-okta-config.sh
RUN mkdir -p /tmp/nexus-boot-patch/BOOT-INF/lib && \
    cd /tmp/nexus-boot-patch && \
    jar xf /opt/sonatype/nexus/bin/sonatype-nexus-repository-3.92.0-03.jar BOOT-INF/classpath.idx && \
    cp /tmp/nexus-okta-auth-plugin.jar BOOT-INF/lib/nexus-okta-auth-plugin.jar && \
    printf '%s\n' '- "BOOT-INF/lib/nexus-okta-auth-plugin.jar"' >> BOOT-INF/classpath.idx && \
    jar uf0 /opt/sonatype/nexus/bin/sonatype-nexus-repository-3.92.0-03.jar \
      BOOT-INF/lib/nexus-okta-auth-plugin.jar BOOT-INF/classpath.idx && \
    rm -rf /tmp/nexus-boot-patch /tmp/nexus-okta-auth-plugin.jar && \
    touch /opt/sonatype/nexus/etc/nexus-okta-auth.properties && \
    chmod 750 /opt/sonatype/nexus/bin/run-with-okta-config.sh && \
    chown nexus:nexus -R /opt/sonatype/nexus

USER nexus
CMD ["/opt/sonatype/nexus/bin/run-with-okta-config.sh"]
