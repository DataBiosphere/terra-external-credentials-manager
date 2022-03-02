package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.SshKeyApi;
import bio.terra.externalcreds.generated.model.SshKeyCommonFields;
import bio.terra.externalcreds.generated.model.SshKeyType;
import bio.terra.externalcreds.generated.model.Sshkey;
import bio.terra.externalcreds.generated.model.UpdateSshKeyRequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class SshKeyApiController implements SshKeyApi {

  public SshKeyApiController() {}

  @Override
  public ResponseEntity<Void> deleteSshKey(SshKeyType provider) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public ResponseEntity<Sshkey> getSshKey(SshKeyType provider) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public ResponseEntity<Void> storeSshKey(
      SshKeyType provider, byte[] key, SshKeyCommonFields keyInfo) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public ResponseEntity<Sshkey> updateSshKey(SshKeyType provider, UpdateSshKeyRequestBody body) {
    throw new UnsupportedOperationException("not implemented");
  }
}
