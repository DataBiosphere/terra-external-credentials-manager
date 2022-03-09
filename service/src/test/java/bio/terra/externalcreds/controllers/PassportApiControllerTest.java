package bio.terra.externalcreds.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.generated.model.OneOfValidatePassportRequestCriteriaItems;
import bio.terra.externalcreds.generated.model.RASv11;
import bio.terra.externalcreds.generated.model.ValidatePassportRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
public class PassportApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void testValidatePassport() throws Exception {
    var criteria = new ArrayList<OneOfValidatePassportRequestCriteriaItems>();
    var criterion = new RASv11().consentCode("c1").phsId("phs001234");
    criterion.issuer("visa issuer");
    criteria.add(criterion);
    var req =
        new ValidatePassportRequest().passports(List.of("I am a passport")).criteria(criteria);

    var raw = objectMapper.writeValueAsString(req);

    mvc.perform(post("/passport/v1/validate").contentType(MediaType.APPLICATION_JSON).content(raw))
        .andExpect(status().isBadRequest());
  }
}
