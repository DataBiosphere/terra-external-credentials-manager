package bio.terra.externalcreds.dataAccess;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Annotation used to demarcate write database transaction, specifying correct transaction semantics
 * and retry
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Retryable(interceptor = "transactionRetryInterceptor")
@Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED)
public @interface WriteTransaction {}
