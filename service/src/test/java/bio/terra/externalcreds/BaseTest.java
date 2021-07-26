package bio.terra.externalcreds;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest({"DATABASE_NAME=ecm_test"})
@ActiveProfiles("human-readable-logging")
@Transactional
@Rollback
public class BaseTest {
  public void assertEmpty(Optional<?> optional) {
    assertTrue(optional.isEmpty(), "expected empty optional");
  }

  public void assertPresent(Optional<?> optional) {
    assertTrue(optional.isPresent(), "expected non-empty optional");
  }
}
