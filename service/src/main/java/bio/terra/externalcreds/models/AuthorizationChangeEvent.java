package bio.terra.externalcreds.models;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableAuthorizationChangeEvent.class)
public interface AuthorizationChangeEvent extends WithAuthorizationChangeEvent {
  String getUserId();

  String getProviderName();

  class Builder extends ImmutableAuthorizationChangeEvent.Builder {}
}
