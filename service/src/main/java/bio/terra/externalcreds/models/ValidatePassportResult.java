package bio.terra.externalcreds.models;

import bio.terra.externalcreds.visaComparators.VisaCriterion;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface ValidatePassportResult extends WithValidatePassportResult {
  Boolean getValid();

  Optional<VisaCriterion> getMatchedCriterion();

  Optional<Map<String, String>> getAuditInfo();

  class Builder extends ImmutableValidatePassportResult.Builder {}
}