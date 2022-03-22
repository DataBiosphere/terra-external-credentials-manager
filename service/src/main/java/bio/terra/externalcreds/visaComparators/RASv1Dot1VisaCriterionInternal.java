package bio.terra.externalcreds.visaComparators;

import org.immutables.value.Value;

@Value.Immutable
public interface RASv1Dot1VisaCriterionInternal
    extends VisaCriterionInternal, WithRASv1Dot1VisaCriterionInternal {
  String getPhsId();

  String getConsentCode();

  class Builder extends ImmutableRASv1Dot1VisaCriterionInternal.Builder {}
}
