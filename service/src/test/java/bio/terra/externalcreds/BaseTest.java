package bio.terra.externalcreds;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {"DATABASE_NAME=ecm_test"},
    classes = ExternalCredsWebApplication.class)
@ActiveProfiles({"test", "human-readable-logging"})
@Transactional
@Rollback
public abstract class BaseTest {
  public void assertEmpty(Optional<?> optional) {
    assertTrue(optional.isEmpty(), "expected empty optional");
  }

  public void assertPresent(Optional<?> optional) {
    assertTrue(optional.isPresent(), "expected non-empty optional");
  }
}
