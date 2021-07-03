package bio.terra.externalcreds.dataAccess;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Retryable(
    include = RecoverableDataAccessException.class,
    backoff = @Backoff(delay = 1000, multiplier = 2),
    maxAttempts = 4)
@Transactional(
    isolation = Isolation.SERIALIZABLE,
    propagation = Propagation.REQUIRED,
    readOnly = true)
public @interface ReadTransaction {}
