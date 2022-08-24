package bio.terra.externalcreds.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.generated.model.OneOfValidatePassportRequestCriteriaItems;
import bio.terra.externalcreds.generated.model.RASv1Dot1VisaCriterion;
import bio.terra.externalcreds.generated.model.ValidatePassportRequest;
import bio.terra.externalcreds.generated.model.ValidatePassportResult;
import bio.terra.externalcreds.models.ValidatePassportResultInternal;
import bio.terra.externalcreds.services.PassportService;
import bio.terra.externalcreds.visaComparators.RASv1Dot1VisaCriterionInternal.Builder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@Tag("unit")
@AutoConfigureMockMvc
public class PassportApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private PassportService passportServiceMock;

  @Test
  void testValidatePassport() throws Exception {
    var criteria = new ArrayList<OneOfValidatePassportRequestCriteriaItems>();
    var criterion = new RASv1Dot1VisaCriterion().consentCode("c1").phsId("phs001234");
    criterion.issuer("visa issuer");
    criteria.add(criterion);
    var passportJwts = List.of("I am a passport");
    var req = new ValidatePassportRequest().passports(passportJwts).criteria(criteria);

    var internalCriterion =
        new Builder()
            .phsId(criterion.getPhsId())
            .consentCode(criterion.getConsentCode())
            .issuer(criterion.getIssuer())
            .build();

    var resultInternal =
        new ValidatePassportResultInternal.Builder()
            .valid(true)
            .matchedCriterion(internalCriterion)
            .auditInfo(Map.of("foo", "bar"))
            .build();

    when(passportServiceMock.validatePassport(passportJwts, List.of(internalCriterion)))
        .thenReturn(resultInternal);

    var expected =
        new ValidatePassportResult()
            .auditInfo(resultInternal.getAuditInfo().orElse(Map.of()))
            .valid(resultInternal.getValid())
            .matchedCriterion(criterion);

    mvc.perform(
            post("/passport/v1/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(expected)));
  }
}
