package bio.terra.externalcreds.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.backoff.BackOffPolicy;

public class CompositeBackoffPolicy implements BackOffPolicy {
  private final LinkedHashMap<BinaryExceptionClassifier, BackOffPolicy> backOffPolicies;

  public CompositeBackoffPolicy(
      LinkedHashMap<Class<? extends Throwable>, BackOffPolicy> backOffPolicies) {
    this.backOffPolicies = new LinkedHashMap<>();
    backOffPolicies.forEach(
        (throwableClass, backOffPolicy) ->
            this.backOffPolicies.put(
                new BinaryExceptionClassifier(Map.of(throwableClass, true)), backOffPolicy));
  }

  @Override
  public BackOffContext start(RetryContext context) {
    Map<BinaryExceptionClassifier, BackOffContext> backOffContextMap = new HashMap<>();
    for (Map.Entry<BinaryExceptionClassifier, BackOffPolicy> classifierAndPolicy :
        this.backOffPolicies.entrySet()) {
      backOffContextMap.put(
          classifierAndPolicy.getKey(), classifierAndPolicy.getValue().start(context));
    }

    return new BackOffContextByClassifier(context, backOffContextMap);
  }

  @Override
  public void backOff(BackOffContext backOffContext) throws BackOffInterruptedException {
    BackOffContextByClassifier backOffContextByClassifier =
        (BackOffContextByClassifier) backOffContext;

    for (BinaryExceptionClassifier classifier :
        backOffContextByClassifier.backOffContexts.keySet()) {
      if (classifier.classify(backOffContextByClassifier.retryContext.getLastThrowable())) {
        backOffPolicies
            .get(classifier)
            .backOff(backOffContextByClassifier.backOffContexts.get(classifier));
      }
    }
  }

  private class BackOffContextByClassifier implements BackOffContext {
    private final RetryContext retryContext;
    private final Map<BinaryExceptionClassifier, BackOffContext> backOffContexts;

    private BackOffContextByClassifier(
        RetryContext retryContext, Map<BinaryExceptionClassifier, BackOffContext> backOffContexts) {
      this.retryContext = retryContext;
      this.backOffContexts = backOffContexts;
    }
  }
}
