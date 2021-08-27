# Set the blessed image as the base/parent image
#ARG PARENT_IMAGE
#FROM $PARENT_IMAGE
FROM service:latest
# copied from publish:
#          ./gradlew --build-cache :service:jibDockerBuild --image=${{ steps.image-name.outputs.name }} -Djib.console=plain
#          name = name::gcr.io/${GOOGLE_PROJECT}/${SERVICE_NAME}:${{ steps.tag.outputs.tag }}
# copied from build and test:
#          ./gradlew --build-cache :service:jibDockerBuild -Djib.console=plain
# Example: ./gradlew jib --image=<your image, eg. gcr.io/my-project/spring-boot-jib>

# download and untar the cloud profiler
RUN mkdir -p /opt/cprof && \
  wget -q -O- https://storage.googleapis.com/cloud-profiler/java/latest/profiler_java_agent.tar.gz \
  | tar xzv -C /opt/cprof
