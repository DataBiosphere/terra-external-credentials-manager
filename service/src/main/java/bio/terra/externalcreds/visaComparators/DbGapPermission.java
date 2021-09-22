package bio.terra.externalcreds.visaComparators;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableDbGapPermission.class)
public interface DbGapPermission extends WithDbGapPermission {
  @JsonProperty("phs_id")
  String getPhsId();

  @JsonProperty("consent_group")
  String getConsentGroup();

  class Builder extends ImmutableDbGapPermission.Builder {}
}
