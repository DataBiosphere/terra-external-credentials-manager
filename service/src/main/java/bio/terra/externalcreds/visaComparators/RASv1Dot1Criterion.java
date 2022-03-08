package bio.terra.externalcreds.visaComparators;

import org.immutables.value.Value;

@Value.Immutable
public interface RASv1Dot1Criterion extends VisaCriterion, WithRASv1Dot1Criterion {
  String getPhsId();

  String getConsentCode();

  class Builder extends ImmutableRASv1Dot1Criterion.Builder {}
}
