package bio.terra.externalcreds.models;

import java.sql.Timestamp;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

@Builder
@Getter
@With
public class GA4GHPassport {
  private final int id;
  private final int linkedAccountId;
  private final String jwt;
  private final Timestamp expires;
}
