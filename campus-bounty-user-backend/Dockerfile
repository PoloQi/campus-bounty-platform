FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src/main ./src/main
RUN mvn -B -Dmaven.test.skip=true package \
    && cp target/campus-bounty-backend-*.jar /bounty.jar

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system bounty \
    && useradd --system --gid bounty --no-create-home bounty \
    && mkdir /app/uploads \
    && chown bounty:bounty /app/uploads
COPY --from=build /bounty.jar ./bounty.jar
USER bounty
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/bounty.jar"]
