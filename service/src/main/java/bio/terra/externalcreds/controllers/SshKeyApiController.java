package bio.terra.externalcreds.controllers;

import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.BearerTokenParser;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.generated.api.SshKeyPairApi;
import bio.terra.externalcreds.generated.model.SshKeyPair;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.services.SamService;
import bio.terra.externalcreds.services.SshKeyPairService;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
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

  private String getUserIdFromSam() {
    try {
      var header =
          Optional.ofNullable(request.getHeader("authorization"))
              .orElseThrow(() -> new UnauthorizedException("User is not authorized"));
      var accessToken = BearerTokenParser.parse(header);

      return samService.samUsersApi(accessToken).getUserStatusInfo().getUserSubjectId();
    } catch (ApiException e) {
      throw new ExternalCredsException(
          e,
          e.getCode() == HttpStatus.NOT_FOUND.value()
              ? HttpStatus.FORBIDDEN
              : HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  public ResponseEntity<Void> deleteSshKeyPair(SshKeyPairType type) {
    sshKeyPairService.deleteSshKeyPair(getUserIdFromSam(), type);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<SshKeyPair> generateSshKeyPair(SshKeyPairType type, String email) {
    return new ResponseEntity(
        sshKeyPairService.generateSshKeyPair(getUserIdFromSam(), email, type),
        HttpStatus.OK);
  }

  @Override
  public ResponseEntity<SshKeyPair> getSshKeyPair(SshKeyPairType type) {
    var sshKeyPair = sshKeyPairService.getSshKeyPair(getUserIdFromSam(), type);
    return ResponseEntity.of(sshKeyPair.map(keyPair -> getSshKeyPair(keyPair)));
  }

  @Override
  public ResponseEntity<SshKeyPair> putSshKeyPair(SshKeyPairType type, SshKeyPair body) {
    var sshKeyPair =
        sshKeyPairService.putSshKeyPair(
            getUserIdFromSam(),
            type,
            body.getPrivateKey(),
            body.getPublicKey(),
            body.getExternalUserEmail());
    return new ResponseEntity(sshKeyPair, HttpStatus.OK);
  }

  private SshKeyPair getSshKeyPair(bio.terra.externalcreds.models.SshKeyPair sshKeyPair) {
    return new SshKeyPair()
        .externalUserEmail(sshKeyPair.getExternalUserEmail())
        .publicKey(sshKeyPair.getPublicKey())
        .privateKey(sshKeyPair.getPrivateKey());
  }
}
