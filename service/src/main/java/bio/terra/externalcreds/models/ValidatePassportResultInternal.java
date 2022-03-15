package bio.terra.externalcreds.models;

import bio.terra.externalcreds.visaComparators.VisaCriterionInternal;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface ValidatePassportResultInternal extends WithValidatePassportResultInternal {
  Boolean getValid();

  Optional<VisaCriterionInternal> getMatchedCriterion();

  Optional<Map<String, String>> getAuditInfo();

  class Builder extends ImmutableValidatePassportResultInternal.Builder {}
}
