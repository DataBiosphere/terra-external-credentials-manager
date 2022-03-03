package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.SshKeyApi;
import bio.terra.externalcreds.generated.model.SshKeyPairInfo;
import bio.terra.externalcreds.generated.model.SshKeyType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
// TODO(PF-1354): implement the service.
public class SshKeyApiController implements SshKeyApi {

  public SshKeyApiController() {}

  @Override
  public ResponseEntity<Void> deleteSshKeyPair(SshKeyType provider) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public ResponseEntity<SshKeyPairInfo> getSshKeyPair(SshKeyType provider) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public ResponseEntity<Void> storeSshKeyPair(
      SshKeyType sshkeyType, byte[] privateKey, byte[] publicKey, String externalUserEmail) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
