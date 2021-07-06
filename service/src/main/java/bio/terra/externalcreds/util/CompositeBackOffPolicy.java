package bio.terra.externalcreds.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.backoff.BackOffPolicy;

/**
 * BackOffPolicy that delegates to a number of nested BackOffPolicies. The BackOffPolicy chosen is
 * determined by the exception being handled. If more than one policy matches, the first is used.
 */
@Slf4j
public class CompositeBackOffPolicy implements BackOffPolicy {
  // LinkedHashMap to ensure consistent behavior
  private final LinkedHashMap<BinaryExceptionClassifier, BackOffPolicy> backOffPoliciesByClassifier;

  public CompositeBackOffPolicy(
      LinkedHashMap<Class<? extends Throwable>, BackOffPolicy> backOffPolicies) {
    this.backOffPoliciesByClassifier = new LinkedHashMap<>();
    backOffPolicies.forEach(
        (throwableClass, backOffPolicy) ->
            this.backOffPoliciesByClassifier.put(
                new BinaryExceptionClassifier(Map.of(throwableClass, true)), backOffPolicy));
  }

  @Override
  public BackOffContext start(RetryContext context) {
    Map<BinaryExceptionClassifier, BackOffContext> backOffContextMap = new HashMap<>();
    this.backOffPoliciesByClassifier.forEach(
        (classifier, policy) -> backOffContextMap.put(classifier, policy.start(context)));
    return new BackOffContextByClassifier(context, backOffContextMap);
  }

  @Override
  public void backOff(BackOffContext backOffContext) throws BackOffInterruptedException {
    BackOffContextByClassifier backOffContextByClassifier =
        (BackOffContextByClassifier) backOffContext;

    for (BinaryExceptionClassifier classifier :
        backOffContextByClassifier.backOffContexts.keySet()) {
      if (classifier.classify(backOffContextByClassifier.retryContext.getLastThrowable())) {
        log.debug(
            "retrying",
            Map.of(
                "exception",
                backOffContextByClassifier.retryContext.getLastThrowable().getClass().getName(),
                "exceptionMessage",
                backOffContextByClassifier.retryContext.getLastThrowable().getMessage(),
                "failedTrialCount",
                backOffContextByClassifier.retryContext.getRetryCount()));

        backOffPoliciesByClassifier
            .get(classifier)
            .backOff(backOffContextByClassifier.backOffContexts.get(classifier));
        break; // don't back off for any further matching back off policies
      }
    }
  }

  /**
   * Class to keep track of retry context and a back off context for each nested back off policy.
   */
  private static class BackOffContextByClassifier implements BackOffContext {
    private final RetryContext retryContext;
    private final Map<BinaryExceptionClassifier, BackOffContext> backOffContexts;

    private BackOffContextByClassifier(
        RetryContext retryContext, Map<BinaryExceptionClassifier, BackOffContext> backOffContexts) {
      this.retryContext = retryContext;
      this.backOffContexts = backOffContexts;
    }
  }
}
