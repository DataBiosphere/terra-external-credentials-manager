package bio.terra.externalcreds;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {"DATABASE_NAME=ecm_test"},
    classes = ExternalCredsWebApplication.class)
@ActiveProfiles({"test", "human-readable-logging"})
@Transactional
@Rollback
// We need this because Spring Boot doesn't re-use contexts when @MockBean is used in a test,
// meaning a new connection pool is spun up per test class, causing us to blow through the max
// allowable connections. Using the DirtiesContext annotation, we can tell Spring not to cache
// the test context, meaning the connection pool will be shut down after each class, freeing up
// connections. https://github.com/spring-projects/spring-boot/issues/7174
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class BaseTest {
  public void assertEmpty(Optional<?> optional) {
    assertTrue(optional.isEmpty(), "expected empty optional");
  }

  public void assertPresent(Optional<?> optional) {
    assertTrue(optional.isPresent(), "expected non-empty optional");
  }
}
