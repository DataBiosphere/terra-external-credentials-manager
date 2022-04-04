# Set the base/parent image
# see https://github.com/broadinstitute/dsp-appsec-blessed-images/tree/main/jre
# Google's distroless images are openjdk, this is the simplest with adoptopenjdk
FROM us.gcr.io/broad-dsp-gcr-public/base/jre:17-debian

# download and untar the cloud profiler
RUN mkdir -p /opt/cprof && \
  curl -q O https://storage.googleapis.com/cloud-profiler/java/latest/profiler_java_agent.tar.gz \
  | tar xzv -C /opt/cprof
