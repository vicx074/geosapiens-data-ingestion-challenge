FROM maven:3.9.11-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package \
    && find target -maxdepth 1 -type f -name 'geosapiens-backend-*.jar' ! -name '*.original' -exec cp {} /tmp/app.jar \;

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache su-exec \
    && addgroup -g 10001 -S geosapiens \
    && adduser -u 10001 -S -D -G geosapiens geosapiens \
    && mkdir -p /data/uploads \
    && chown geosapiens:geosapiens /data/uploads

WORKDIR /app
COPY --from=build --chown=geosapiens:geosapiens /tmp/app.jar ./app.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod 0755 /usr/local/bin/docker-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["java", "-jar", "/app/app.jar"]
