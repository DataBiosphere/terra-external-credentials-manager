package bio.terra.externalcreds.models;

import bio.terra.externalcreds.generated.model.Provider;
import org.immutables.value.Value;

@Value.Immutable
public interface VisaVerificationDetails {
  Integer getLinkedAccountId();

  Provider getProvider();

  String getVisaJwt();

  int getVisaId();

  class Builder extends ImmutableVisaVerificationDetails.Builder {}
}
