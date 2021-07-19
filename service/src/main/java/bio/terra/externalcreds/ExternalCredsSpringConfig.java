package bio.terra.externalcreds;

import bio.terra.externalcreds.util.CompositeBackOffPolicy;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.UniformRandomBackOffPolicy;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.policy.CompositeRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableRetry
@EnableTransactionManagement
public class ExternalCredsSpringConfig {
  private final DataSource dataSource;

  public ExternalCredsSpringConfig(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  // This bean plus the @EnableTransactionManagement annotation above enables the use of the
  // @Transaction annotation to control the transaction properties of the data source.
  @Bean("transactionManager")
  public PlatformTransactionManager getTransactionManager() {
    return new JdbcTransactionManager(this.dataSource);
  }

  /**
   * This beans governs how database transactions are retried. TransientDataAccessExceptions,
   * generally cause by concurrency failures, are retried quickly, random 10-20ms delay, and up to
   * 100 times total. CannotCreateTransactionExceptions, such as database is down, are retried with
   * exponential back off starting at 1s delay up to 4 times total.
   */
  @Bean("transactionRetryInterceptor")
  public MethodInterceptor getTransactionRetryInterceptor() {
    return RetryInterceptorBuilder.stateless()
        .retryPolicy(createTransactionRetryPolicy())
        .backOffPolicy(createTransactionBackOffPolicy())
        .build();
  }

  /**
   * TransientDataAccessException retries with random delay between 10ms and 20ms.
   * CannotCreateTransactionException retries with exponential back off with initial 1s delay,
   * doubling each attempt.
   */
  private BackOffPolicy createTransactionBackOffPolicy() {
    var transientBackOffPolicy = new UniformRandomBackOffPolicy();
    transientBackOffPolicy.setMaxBackOffPeriod(20);
    transientBackOffPolicy.setMinBackOffPeriod(10);

    var recoverableBackOffPolicy = new ExponentialBackOffPolicy();
    recoverableBackOffPolicy.setInitialInterval(1000);
    recoverableBackOffPolicy.setMultiplier(2.0);

    var backOffPolicies = new LinkedHashMap<Class<? extends Throwable>, BackOffPolicy>();
    backOffPolicies.put(TransientDataAccessException.class, transientBackOffPolicy);
    backOffPolicies.put(CannotCreateTransactionException.class, recoverableBackOffPolicy);

    return new CompositeBackOffPolicy(backOffPolicies);
  }

  /**
   * Policy dictating TransientDataAccessException is allowed 100 attempts and
   * CannotCreateTransactionException is allowed 4 attempts
   */
  private RetryPolicy createTransactionRetryPolicy() {
    var retryPolicy = new CompositeRetryPolicy();
    retryPolicy.setOptimistic(true); // retry when any nested policy says to retry
    retryPolicy.setPolicies(
        new RetryPolicy[] {
          new SimpleRetryPolicy(100, Map.of(TransientDataAccessException.class, true), false),
          new SimpleRetryPolicy(4, Map.of(CannotCreateTransactionException.class, true), false)
        });
    return retryPolicy;
  }
}
