package bio.terra.externalcreds.models;

import java.sql.Timestamp;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

@Builder
@Getter
@With
public class GA4GHVisa {
  private final int id;
  private final int passportId;
  private final String visaType;
  private final Timestamp expires;
  private final String jwt;
  private final String issuer;
  private final TokenTypeEnum tokenType;
  private final Timestamp lastValidated;
}
