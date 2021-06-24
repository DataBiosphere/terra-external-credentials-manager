package bio.terra.externalcreds.service;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

public class GetLinkTest extends BaseTest {

  @Autowired private MockMvc mvc;

  @MockBean private LinkedAccountDAO linkedAccountDAO;

  @Test
  void testGetLink() throws Exception {
    //        OffsetDateTime timestamp = new OffsetDateTime();
    //        LinkInfo linkInfo = new LinkInfo().externalUserId("").expirationTimestamp(timestamp);
    //
    //        when(linkedAccountDAO.getLinkedAccount(Mockito.anyString(), Mockito.anyString()))
    //                .thenReturn(linkInfo);
  }
}
