FROM openjdk:11-jdk as builder

ARG JAR_FILE

COPY ${JAR_FILE} application.jar
RUN java -Djarmode=layertools -jar application.jar extract

FROM adoptopenjdk:11-jre-hotspot
COPY --from=builder dependencies/ ./
COPY --from=builder snapshot-dependencies/ ./
COPY --from=builder spring-boot-loader/ ./
COPY --from=builder application/ ./

USER root
RUN apt-get install -y locales
RUN locale-gen fr_FR.utf8
ENV LANG fr_FR.UTF-8
ENV LANGUAGE fr_FR:fr
ENV LC_ALL fr_FR.UTF-8

ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]
