FROM eclipse-temurin:21.0.5_11-jdk
VOLUME /tmp
ARG JAR_FILE
COPY ${JAR_FILE} app.jar


EXPOSE 8776
EXPOSE 5000

# Définition de l'encodage de l'environnement à UTF-8
ENV LANG=C.UTF-8

# Commande pour lancer l'application Spring Boot
ENTRYPOINT ["java","--add-opens", "java.desktop/java.awt.font=ALL-UNNAMED", "--add-opens", "java.base/java.util=ALL-UNNAMED","--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED","--add-opens", "java.base/java.lang=ALL-UNNAMED","--add-opens", "java.base/java.io=ALL-UNNAMED", "--add-opens", "java.base/java.text=ALL-UNNAMED","-jar","/app.jar"]

