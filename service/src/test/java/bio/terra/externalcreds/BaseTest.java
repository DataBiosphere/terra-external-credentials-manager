package bio.terra.externalcreds;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest({"DATABASE_NAME=ecm_test"})
@AutoConfigureMockMvc
@ActiveProfiles("human-readable-logging")
public class BaseTest {}
