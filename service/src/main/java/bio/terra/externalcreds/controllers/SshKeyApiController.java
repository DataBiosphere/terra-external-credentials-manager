package bio.terra.externalcreds.controllers;

import static bio.terra.externalcreds.controllers.UserStatusInfoUtils.getUserIdFromSam;

import bio.terra.common.exception.BadRequestException;
import bio.terra.externalcreds.generated.api.SshKeyPairApi;
import bio.terra.externalcreds.generated.model.SshKeyPair;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.services.SamService;
import bio.terra.externalcreds.services.SshKeyPairService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public record SshKeyApiController(
    HttpServletRequest request, SamService samService, SshKeyPairService sshKeyPairService,
    ObjectMapper objectMapper)
    implements SshKeyPairApi {

  @Override
  public ResponseEntity<Void> deleteSshKeyPair(SshKeyPairType type) {
    sshKeyPairService.deleteSshKeyPair(getUserIdFromSam(request, samService), type);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<SshKeyPair> getSshKeyPair(SshKeyPairType type) {
    var sshKeyPair = sshKeyPairService.getSshKeyPair(getUserIdFromSam(request, samService), type);
    return ResponseEntity.of(sshKeyPair.map(OpenApiConverters.Output::convert));
  }

  @Override
  public ResponseEntity<SshKeyPair> putSshKeyPair(SshKeyPairType type, SshKeyPair body) {
    var sshKeyPair =
        sshKeyPairService.putSshKeyPair(getUserIdFromSam(request, samService), type, body);
    return ResponseEntity.ok(OpenApiConverters.Output.convert(sshKeyPair));
  }

  @Override
  public ResponseEntity<SshKeyPair> generateSshKeyPair(SshKeyPairType type, String email) {
    try {
      String userEmail = objectMapper.readValue(email, String.class);
      var sshKeyPair =
          sshKeyPairService.generateSshKeyPair(
              getUserIdFromSam(request, samService), userEmail, type);
      return ResponseEntity.ok(OpenApiConverters.Output.convert(sshKeyPair));
    } catch (JsonProcessingException e) {
      throw new BadRequestException("Cannot parse the email input", e);
    }
  }
}
