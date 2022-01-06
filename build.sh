#!/bin/bash


echo Building docker image

PARAMS=""
while (( "$#" )); do
  case "$1" in
    -s|--skip-tests)
      SKIP_TESTS=true
      shift
      ;;
    -*|--*=) # unsupported flags
      echo "Error: Unsupported flag $1" >&2
      exit 1
      ;;
    *) # preserve positional arguments
      PARAMS="$PARAMS $1"
      shift
      ;;
  esac
done
# set positional arguments in their proper place
eval set -- "$PARAMS"

echo Skipping tests : ${SKIP_TESTS:=false}

# Back
VERSION_BACK=$(mvn -q \
    -Dexec.executable=echo \
    -Dexec.args='${project.version}' \
    --non-recursive \
    exec:exec)
BACK_IMAGE_NAME=registry.okina.fr/mobiiti/anshar:"${VERSION_BACK}"
#mvn spring-boot:build-image -Dspring-boot.build-image.imageName="${BACK_IMAGE_NAME}" -D${SKIP_TESTS}
mvn clean package -D${SKIP_TESTS}
docker build -t "${BACK_IMAGE_NAME}" --build-arg JAR_FILE=target/anshar-${VERSION_BACK}.jar .
docker push "${BACK_IMAGE_NAME}"
