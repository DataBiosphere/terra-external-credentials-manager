package bio.terra.externalcreds.models;

import org.immutables.value.Value;

@Value.Immutable
public interface PassportVerificationDetails {
  Integer getLinkedAccountId();

  String getProviderName();

  String getPassportJwt();

  class Builder extends ImmutablePassportVerificationDetails.Builder {}
}
