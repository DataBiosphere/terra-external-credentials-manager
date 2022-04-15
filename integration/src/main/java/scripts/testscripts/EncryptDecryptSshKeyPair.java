package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.externalcreds.api.SshKeyPairApi;
import bio.terra.externalcreds.client.ApiClient;
import bio.terra.externalcreds.model.SshKeyPair;
import bio.terra.externalcreds.model.SshKeyPairType;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import scripts.utils.ClientTestUtils;

public class EncryptDecryptSshKeyPair extends TestScript {

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    ApiClient apiClient = ClientTestUtils.getClientWithTestUserAuth(testUser, server);
    var sshKeyPairApi = new SshKeyPairApi(apiClient);

    SshKeyPair sshKeyPair = sshKeyPairApi.generateSshKeyPair(testUser.userEmail, SshKeyPairType.GITHUB);
    SshKeyPair loadedSshKeyPair = sshKeyPairApi.getSshKeyPair(SshKeyPairType.GITHUB);

    assertEquals(sshKeyPair, loadedSshKeyPair);

    sshKeyPairApi.deleteSshKeyPair(SshKeyPairType.GITHUB);
  }
}
