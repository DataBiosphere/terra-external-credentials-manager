package bio.terra.externalcreds.models;

import org.immutables.value.Value;

@Value.Immutable
public interface VisaVerificationDetails {
  Integer getLinkedAccountId();

  String getProviderName();

  String getVisaJwt();

  int getVisaId();

  class Builder extends ImmutableVisaVerificationDetails.Builder {}
}
