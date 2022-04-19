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

    var githubSshKeyPair =
        sshKeyPairApi.generateSshKeyPair('"' + testUser.userEmail + '"', SshKeyPairType.GITHUB);
    var githubLoadedSshKeyPair = sshKeyPairApi.getSshKeyPair(SshKeyPairType.GITHUB);

    assertEquals(githubSshKeyPair, githubLoadedSshKeyPair);

    sshKeyPairApi.deleteSshKeyPair(SshKeyPairType.GITHUB);

    var notFoundException =
        assertThrows(
            HttpStatusCodeException.class,
            () -> sshKeyPairApi.getSshKeyPair(SshKeyPairType.GITHUB));
    assertEquals(HttpStatus.NOT_FOUND, notFoundException.getStatusCode());

//    var gitlabSshKeyPair =
//        sshKeyPairApi.generateSshKeyPair('"' + testUser.userEmail + '"', SshKeyPairType.GITLAB);
//    var gitlabLoadedSshKeyPair = sshKeyPairApi.getSshKeyPair(SshKeyPairType.GITLAB);
//
//    assertEquals(gitlabSshKeyPair, gitlabLoadedSshKeyPair);
//
//    sshKeyPairApi.deleteSshKeyPair(SshKeyPairType.GITLAB);
//
//    var azureSshKeyPair =
//        sshKeyPairApi.generateSshKeyPair('"' + testUser.userEmail + '"', SshKeyPairType.AZURE);
//    var azureLoadedSshKeyPair = sshKeyPairApi.getSshKeyPair(SshKeyPairType.AZURE);
//
//    assertEquals(azureSshKeyPair, azureLoadedSshKeyPair);
//
//    sshKeyPairApi.deleteSshKeyPair(SshKeyPairType.AZURE);
  }
}
