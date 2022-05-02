package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.springframework.stereotype.Service;

@Service
public class SamService {
  private final ExternalCredsConfig externalCredsConfig;

  public SamService(ExternalCredsConfig externalCredsConfig) {
    this.externalCredsConfig = externalCredsConfig;
  }

  public UsersApi samUsersApi(String accessToken) {
    var client = new ApiClient();
    //    client.setHttpClient(
    //        client
    //            .getHttpClient()
    //            .newBuilder()
    //            .addInterceptor(
    //                chain -> {
    //                  var span =
    //                      Tracing.getTracer()
    //                          .spanBuilder(
    //                              String.format(
    //                                  "%s %s", chain.request().method(), chain.request().url()));
    //                  try (var scope = span.startScopedSpan()) {
    //                    Span currentSpan = Tracing.getTracer().getCurrentSpan();
    //                    var x = currentSpan.getContext().isValid();
    //                    log.info("context valid: " + x);
    //                    currentSpan.putAttribute("foo",
    // AttributeValue.stringAttributeValue("bar"));
    //
    //                    SpanContext context = currentSpan.getContext();
    //                    var request =
    //                        chain
    //                            .request()
    //                            .newBuilder()
    //                            .header(
    //                                "X-B3-TraceId",
    //                                currentSpan.getContext().getTraceId().toLowerBase16())
    //                            .header("X-B3-SpanId", context.getSpanId().toLowerBase16())
    //                            //                            .header(
    //                            //                                "X-B3-SpanId",
    //                            //                                SpanId.generateRandomId(new
    //                            // Random()).toLowerBase16())
    //                            //                            .header("X-B3-Sampled", "1")
    //                            .build();
    //                    log.info(currentSpan.getContext().getTraceId().toLowerBase16());
    //                    return chain.proceed(request);
    //                  }
    //                })
    //            .build());
    client.setAccessToken(accessToken);
    return new UsersApi(client.setBasePath(this.externalCredsConfig.getSamBasePath()));
  }
}
