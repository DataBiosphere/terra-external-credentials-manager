package bio.terra.externalcreds.auditLogging;

public enum AuditLogEventType {
  LinkCreated,
  LinkCreationFailed,
  LinkDeleted,
  LinkExpired,
  GetPassport,
  LinkRefreshed,
  SshKeyPairCreated,
  SshKeyPairCreationFailed,
  GetSshKeyPairSucceeded,
  GetSshKeyPairFailed,
  SshKeyPairDeleted,
  SshKeyPairDeletionFailed,
  PutSshKeyPair,
}
