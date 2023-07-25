FROM eclipse-temurin:17-jre-alpine
ARG JAR_FILE=/app/build/libs/app-*.jar
COPY ${JAR_FILE} app.jar
EXPOSE 9080
ENTRYPOINT [ "java","-jar","app.jar" ]