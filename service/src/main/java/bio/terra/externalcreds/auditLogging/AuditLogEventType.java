package bio.terra.externalcreds.auditLogging;

public enum AuditLogEventType {
  LinkCreated,
  LinkCreationFailed,
  LinkDeleted,
  LinkExpired,
  GetPassport,
  GetServiceAccountKey,
  LinkRefreshed,
  GetProviderAccessToken,
  SshKeyPairCreated,
  SshKeyPairCreationFailed,
  GetSshKeyPairSucceeded,
  GetSshKeyPairFailed,
  SshKeyPairDeleted,
  SshKeyPairDeletionFailed,
  PutSshKeyPair,
}
