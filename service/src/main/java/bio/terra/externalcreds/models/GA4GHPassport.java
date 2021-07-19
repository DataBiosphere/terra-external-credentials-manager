package bio.terra.externalcreds.models;

import java.sql.Timestamp;
import lombok.Builder;
import lombok.Data;
import lombok.With;

@Builder
@Data
@With
public class GA4GHPassport {
  private final int id;
  private final int linkedAccountId;
  private final String jwt;
  private final Timestamp expires;
}
