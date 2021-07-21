package bio.terra.externalcreds.models;

import java.util.Collections;
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
  @Builder.Default private final List<GA4GHVisa> visas = Collections.emptyList();
}
