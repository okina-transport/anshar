FROM openjdk:11-jre
VOLUME /tmp
COPY target/*.jar app.jar


EXPOSE 8776

# Définition de l'encodage de l'environnement à UTF-8
ENV LANG C.UTF-8

# Commande pour lancer l'application Spring Boot
CMD ["java", "-jar", "app.jar"]