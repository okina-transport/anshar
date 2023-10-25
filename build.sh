#!/usr/bin/env bash

mvn clean install -DskipTests

MVN_VERSION=$(mvn -q \
    -Dexec.executable=echo \
    -Dexec.args='${project.version}' \
    --non-recursive \
    exec:exec)

docker build --no-cache -t registry.okina.fr/mobiiti/anshar:${MVN_VERSION} --build-arg JAR_FILE=target/anshar-${MVN_VERSION}.jar .
docker push registry.okina.fr/mobiiti/anshar:${MVN_VERSION}