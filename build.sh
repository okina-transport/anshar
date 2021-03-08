echo Building docker image
# Back
VERSION_BACK=$(mvn -q \
    -Dexec.executable=echo \
    -Dexec.args='${project.version}' \
    --non-recursive \
    exec:exec)
BACK_IMAGE_NAME=registry.okina.fr/mobiiti/anshar:"${VERSION_BACK}"
mvn spring-boot:build-image -Dspring-boot.build-image.imageName="${BACK_IMAGE_NAME}" -DskipTests
docker push "${BACK_IMAGE_NAME}"
