package bio.terra.externalcreds.models;

import java.time.Instant;

public class DistributedLockFactory {
  public static DistributedLock createDistributedLock(
      String task, String userId, Instant expiresAt) {
    var lockName = String.format("%s-%s", task, userId);
    return new DistributedLock.Builder()
        .lockName(lockName)
        .userId(userId)
        .expiresAt(expiresAt)
        .build();
  }
}
