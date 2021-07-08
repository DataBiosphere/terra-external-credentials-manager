package bio.terra.externalcreds.models;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class LinkedAccountWithPassportAndVisas {
  private final LinkedAccount linkedAccount;
  private final GA4GHPassport passport;
  private final List<GA4GHVisa> visas;
}
