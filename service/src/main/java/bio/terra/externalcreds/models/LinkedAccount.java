package bio.terra.externalcreds.models;

import java.sql.Timestamp;
import lombok.Builder;

@Builder
public class LinkedAccount {
  private final int id;
  private final String userId;
  private final String providerId;
  private final String refreshToken;
  private final Timestamp expires;
  private final String externalUserId;
}
