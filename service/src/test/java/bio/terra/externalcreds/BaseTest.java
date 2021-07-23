package bio.terra.externalcreds;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest({"DATABASE_NAME=ecm_test"})
@ActiveProfiles("human-readable-logging")
@Transactional
@Rollback
public class BaseTest {}
