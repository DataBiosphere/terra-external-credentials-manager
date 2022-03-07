package bio.terra.externalcreds.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import bio.terra.externalcreds.BaseTest;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    var raw =
        "{\n"
            + "  \"passports\" : [ \"I am a passport\" ],\n"
            + "  \"criteria\" : [ {\n"
            + "    \"type\" : \"ras\",\n"
            + "    \"issuer\" : \"visa issuer\",\n"
            + "    \"phsId\" : \"phs001234\",\n"
            + "    \"consentCode\" : \"c1\"\n"
            + "  } ]\n"
            + "}";

    mvc.perform(post("/passport/v1/validate").contentType(MediaType.APPLICATION_JSON).content(raw));
  }
}
