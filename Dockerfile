FROM eclipse-temurin:21 as builder
COPY . ./preferences
WORKDIR /preferences
RUN ./gradlew assemble

FROM eclipse-temurin:21-jre-alpine
USER nobody
ARG JAR_FILE=/preferences/app/build/libs/app-*.jar
COPY --from=builder ${JAR_FILE} app.jar
EXPOSE 9001
ENTRYPOINT [ "java","-jar","app.jar" ]
