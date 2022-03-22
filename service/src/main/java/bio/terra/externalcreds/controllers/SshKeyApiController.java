package bio.terra.externalcreds.controllers;

import static bio.terra.externalcreds.controllers.OpenApiConverters.Output.convert;
import static bio.terra.externalcreds.controllers.UserStatusInfoUtils.getUserIdFromSam;

import bio.terra.externalcreds.generated.api.SshKeyPairApi;
import bio.terra.externalcreds.generated.model.SshKeyPair;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.services.SamService;
import bio.terra.externalcreds.services.SshKeyPairService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class SshKeyApiController implements SshKeyPairApi {

  private final HttpServletRequest request;
  private final SamService samService;
  private final SshKeyPairService sshKeyPairService;

  public SshKeyApiController(
      HttpServletRequest request, SamService samService, SshKeyPairService sshKeyPairService) {
    this.request = request;
    this.samService = samService;
    this.sshKeyPairService = sshKeyPairService;
  }

  @Override
  public ResponseEntity<Void> deleteSshKeyPair(SshKeyPairType type) {
    sshKeyPairService.deleteSshKeyPair(getUserIdFromSam(request, samService), type);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<SshKeyPair> generateSshKeyPair(SshKeyPairType type, String email) {
    return new ResponseEntity(
        convert(
            sshKeyPairService.generateSshKeyPair(
                getUserIdFromSam(request, samService), email, type)),
        HttpStatus.OK);
  }

  @Override
  public ResponseEntity<SshKeyPair> getSshKeyPair(SshKeyPairType type) {
    var sshKeyPair = sshKeyPairService.getSshKeyPair(getUserIdFromSam(request, samService), type);
    return ResponseEntity.of(sshKeyPair.map(keyPair -> convert(keyPair)));
  }
}
