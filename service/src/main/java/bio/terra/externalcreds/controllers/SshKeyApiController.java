package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.SshKeyApi;
import bio.terra.externalcreds.generated.model.AddGitHubSshKeyRequestBody;
import bio.terra.externalcreds.generated.model.GitHubSshKey;
import bio.terra.externalcreds.generated.model.UpdateGitHubSshKeyRequestBody;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class SshKeyApiController implements SshKeyApi {

  public SshKeyApiController() {
  }

  @Override
  public ResponseEntity<Void> getGitHubSshKey() {
    return null;
  }

  @Override
  public ResponseEntity<List<GitHubSshKey>> enumerateSshKeys() {
    return null;
  }

  @Override
  public ResponseEntity<GitHubSshKey> storeGitHubSshKey(AddGitHubSshKeyRequestBody body) {
    return null;
  }

  @Override
  public ResponseEntity<Void> deleteGitHubSshKey() {
    return null;
  }

  @Override
  public ResponseEntity<GitHubSshKey> updateGitHubSshKey(UpdateGitHubSshKeyRequestBody body) {
    return null;
  }
}
