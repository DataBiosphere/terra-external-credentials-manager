package bio.terra.externalcreds.models;

import bio.terra.externalcreds.generated.model.Provider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableAuthorizationChangeEvent.class)
public interface AuthorizationChangeEvent extends WithAuthorizationChangeEvent {
  String getUserId();

  @Value.Derived
  default String getProviderName() {
    return provider().toString();
  }

  Provider provider();

  class Builder extends ImmutableAuthorizationChangeEvent.Builder {}
}
