package bio.terra.externalcreds.visaComparators;

import bio.terra.externalcreds.models.GA4GHVisa;

public interface VisaComparator {
  /** @return true if visas represent the same authorizations */
  boolean authorizationsMatch(GA4GHVisa visa1, GA4GHVisa visa2);

  boolean matchesCriterion(GA4GHVisa visa, VisaCriterion criterion);

  boolean visaTypeSupported(GA4GHVisa visa);

  boolean criterionTypeSupported(VisaCriterion criterion);
}
