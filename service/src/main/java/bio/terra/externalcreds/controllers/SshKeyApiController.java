package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.SshKeyPairApi;
import bio.terra.externalcreds.generated.model.SshKeyPair;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
// TODO(PF-1354): implement the service.
public class SshKeyApiController implements SshKeyPairApi {

  public SshKeyApiController() {}

  @Override
  public ResponseEntity<Void> deleteSshKeyPair(SshKeyPairType type) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public ResponseEntity<SshKeyPair> getSshKeyPair(SshKeyPairType type) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public ResponseEntity<Void> putSshKeyPair(SshKeyPairType type, SshKeyPair sshKeyPair) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
