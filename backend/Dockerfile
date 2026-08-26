FROM maven:3.9.11-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package \
    && find target -maxdepth 1 -type f -name 'geosapiens-backend-*.jar' ! -name '*.original' -exec cp {} /tmp/app.jar \;

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S geosapiens && adduser -S -G geosapiens geosapiens

WORKDIR /app
COPY --from=build --chown=geosapiens:geosapiens /tmp/app.jar ./app.jar

USER geosapiens
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
