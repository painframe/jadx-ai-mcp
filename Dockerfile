FROM maven:3.9.9-eclipse-temurin-17@sha256:f58d59b6273e785ac0a4477f6e9b5ba1d7731c75b906c0f7b34076f1851318cc AS build
ADD --checksum=sha256:38a5766d3c8170c41566b4b13ea0ede2430e3008421af4927235c2880234d51a https://github.com/skylot/jadx/releases/download/v1.5.5/jadx-1.5.5.zip /tmp/jadx.zip
RUN mkdir /opt/jadx && cd /opt/jadx && jar xf /tmp/jadx.zip
RUN mvn -B org.apache.maven.plugins:maven-install-plugin:3.1.3:install-file -Dfile=/opt/jadx/lib/jadx-1.5.5-all.jar -DgroupId=io.github.skylot -DartifactId=jadx-all -Dversion=1.5.5 -Dpackaging=jar
WORKDIR /build
COPY pom.xml ./
COPY src ./src
COPY tests/fixtures ./tests/fixtures
RUN mvn -B package

FROM scratch AS artifact
COPY --from=build /build/target/jadx-ai-mcp-6.4.1.jar /jadx-ai-mcp-6.4.1.jar

FROM eclipse-temurin:17-jre-jammy@sha256:8993f1aed8b25fcea7a7047a7949c1866fa558fc6830d938c22c4f13b26be9d7 AS runtime
COPY --from=build /opt/jadx/lib/jadx-1.5.5-all.jar /opt/jadx/lib/jadx-1.5.5-all.jar
COPY --from=build /build/target/jadx-ai-mcp-6.4.1.jar /opt/plugin/jadx-ai-mcp-6.4.1.jar
ENV HOME=/tmp
USER 10001:10001
WORKDIR /tmp
ENTRYPOINT ["java", "-cp", "/opt/plugin/jadx-ai-mcp-6.4.1.jar:/opt/jadx/lib/jadx-1.5.5-all.jar", "com.zin.jadxaimcp.headless.HeadlessServer"]
