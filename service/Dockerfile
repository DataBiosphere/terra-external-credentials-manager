# Set the base/parent image
FROM externalcreds:local

# download and untar the cloud profiler
RUN mkdir -p /opt/cprof && \
  curl -q O https://storage.googleapis.com/cloud-profiler/java/latest/profiler_java_agent.tar.gz \
  | tar xzv -C /opt/cprof
