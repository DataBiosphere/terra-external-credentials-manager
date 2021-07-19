package bio.terra.externalcreds.models;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.With;

@Builder
@Data
@With
public class LinkedAccountWithPassportAndVisas {
  private final LinkedAccount linkedAccount;
  private final GA4GHPassport passport;
  private final List<GA4GHVisa> visas;
}
