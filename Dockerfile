FROM onap-repo/openjdk:17
ARG JAR_FILE=/app/build/libs/app-*.jar
COPY ${JAR_FILE} app.jar
EXPOSE 9080
ENTRYPOINT [ "java","-jar","app.jar" ]