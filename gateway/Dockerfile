FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY service/pom.xml service/pom.xml
COPY gateway/pom.xml gateway/pom.xml
COPY service/src service/src
COPY gateway/src gateway/src
RUN apk add --no-cache maven && mvn -q -pl gateway -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /workspace/gateway/target/url-shortener-gateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
