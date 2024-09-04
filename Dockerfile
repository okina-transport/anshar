FROM openjdk:11
VOLUME /tmp
ARG JAR_FILE
COPY ${JAR_FILE} app.jar


EXPOSE 8776
EXPOSE 5000

# Définition de l'encodage de l'environnement à UTF-8
ENV LANG C.UTF-8

# Commande pour lancer l'application Spring Boot
ENTRYPOINT exec java -jar /app.jar
