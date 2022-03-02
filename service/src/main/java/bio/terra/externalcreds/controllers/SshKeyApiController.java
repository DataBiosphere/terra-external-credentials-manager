package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.SshKeyApi;
import bio.terra.externalcreds.generated.model.SshKeyInfo;
import bio.terra.externalcreds.generated.model.SshKeyType;
import bio.terra.externalcreds.generated.model.UpdateSshKeyRequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
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
      SshKeyType provider,
      String name,
      String description,
      byte[] key,
      String externalUserName,
      String externalUserEmail) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public ResponseEntity<SshKeyInfo> updateSshKey(
      SshKeyType provider, UpdateSshKeyRequestBody body) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
