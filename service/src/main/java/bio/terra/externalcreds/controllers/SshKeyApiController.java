package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.SshKeyApi;
import bio.terra.externalcreds.generated.model.SshKeyInfo;
import bio.terra.externalcreds.generated.model.SshKeyType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
// TODO(PF-1354): implement the service.
public class SshKeyApiController implements SshKeyApi {

  public SshKeyApiController() {}

  @Override
  public ResponseEntity<Void> deleteSshKey(SshKeyType provider) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public ResponseEntity<SshKeyInfo> getSshKey(SshKeyType provider) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public ResponseEntity<Void> storeSshKey(
      SshKeyType sshkeyType, byte[] key, String externalUserEmail) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
