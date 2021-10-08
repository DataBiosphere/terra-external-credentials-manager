package bio.terra.externalcreds.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.externalcreds.BaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
public class GlobalExceptionHandlerTest extends BaseTest {
  @MockBean PublicApiController publicApiControllerMock;
  @Autowired private MockMvc mvc;

  @Test
  void testBadRequest() throws Exception {
    when(publicApiControllerMock.getStatus()).thenThrow(new IllegalArgumentException("bad"));
    mvc.perform(get("/status")).andExpect(status().isBadRequest());
  }

  // TODO make this work :)
  @Test
  void testInternalServerError() throws Exception {
    when(publicApiControllerMock.getStatus()).thenThrow(Exception.class);
    mvc.perform(get("/status")).andExpect(status().isInternalServerError());
  }
}
