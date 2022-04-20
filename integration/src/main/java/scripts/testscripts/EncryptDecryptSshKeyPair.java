package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.externalcreds.api.SshKeyPairApi;
import bio.terra.externalcreds.client.ApiClient;
import bio.terra.externalcreds.model.SshKeyPairType;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import scripts.utils.ClientTestUtils;

public class EncryptDecryptSshKeyPair extends TestScript {

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    ApiClient apiClient = ClientTestUtils.getClientWithTestUserAuth(testUser, server);
    var sshKeyPairApi = new SshKeyPairApi(apiClient);

    // generate a key for each ssh key pair type
    var githubSshKeyPair =
        sshKeyPairApi.generateSshKeyPair('"' + testUser.userEmail + '"', SshKeyPairType.GITHUB);
    var gitlabSshKeyPair =
        sshKeyPairApi.generateSshKeyPair('"' + testUser.userEmail + '"', SshKeyPairType.GITLAB);
    var azureSshKeyPair =
        sshKeyPairApi.generateSshKeyPair('"' + testUser.userEmail + '"', SshKeyPairType.AZURE);

    // get the key for each ssh key pair type
    var githubLoadedSshKeyPair = sshKeyPairApi.getSshKeyPair(SshKeyPairType.GITHUB);
    var gitlabLoadedSshKeyPair = sshKeyPairApi.getSshKeyPair(SshKeyPairType.GITLAB);
    var azureLoadedSshKeyPair = sshKeyPairApi.getSshKeyPair(SshKeyPairType.AZURE);

    assertEquals(githubSshKeyPair, githubLoadedSshKeyPair);
    assertEquals(gitlabSshKeyPair, gitlabLoadedSshKeyPair);
    assertEquals(azureSshKeyPair, azureLoadedSshKeyPair);

    sshKeyPairApi.deleteSshKeyPair(SshKeyPairType.GITHUB);
    var notFoundException =
        assertThrows(
            HttpStatusCodeException.class,
            () -> sshKeyPairApi.getSshKeyPair(SshKeyPairType.GITHUB));
    assertEquals(HttpStatus.NOT_FOUND, notFoundException.getStatusCode());

    // clean up
    sshKeyPairApi.deleteSshKeyPair(SshKeyPairType.GITLAB);
    sshKeyPairApi.deleteSshKeyPair(SshKeyPairType.AZURE);
  }
}
