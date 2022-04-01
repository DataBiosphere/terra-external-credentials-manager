package bio.terra.externalcreds.controllers;

import static bio.terra.externalcreds.controllers.UserStatusInfoUtils.getUserIdFromSam;

import bio.terra.externalcreds.generated.api.SshKeyPairApi;
import bio.terra.externalcreds.generated.model.SshKeyPair;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.services.SamService;
import bio.terra.externalcreds.services.SshKeyPairService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public record SshKeyApiController(
    HttpServletRequest request, SamService samService, SshKeyPairService sshKeyPairService)
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
    var sshKeyPair =
        sshKeyPairService.generateSshKeyPair(getUserIdFromSam(request, samService), email, type);
    return ResponseEntity.ok(OpenApiConverters.Output.convert(sshKeyPair));
  }
}
