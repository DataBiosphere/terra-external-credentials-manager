package bio.terra.externalcreds.models;

import bio.terra.externalcreds.ExternalCredsException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.immutables.value.Value;

@Value.Immutable
public interface OAuth2State extends WithOAuth2State {
  String getProvider();

  String getRandom();

  class Builder extends ImmutableOAuth2State.Builder {}

  default String encode(ObjectMapper objectMapper) {
    try {
      return new String(
          Base64.getEncoder()
              .encode(objectMapper.writeValueAsString(this).getBytes(StandardCharsets.UTF_8)));
    } catch (JsonProcessingException e) {
      throw new ExternalCredsException(e);
    }
  }

  static OAuth2State decode(ObjectMapper objectMapper, String encodedState)
      throws CannotDecodeOAuth2State {
    try {
      var decodedState = Base64.getDecoder().decode(encodedState);
      return objectMapper.readValue(decodedState, ImmutableOAuth2State.class);
    } catch (Exception e) {
      throw new CannotDecodeOAuth2State(e);
    }
  }

  static String generateRandomState(SecureRandom secureRandom) {
    var randomStateBytes = new byte[50];
    secureRandom.nextBytes(randomStateBytes);
    return new String(Base64.getEncoder().encode(randomStateBytes));
  }
}
