package bio.terra.externalcreds.visaComparators;

import bio.terra.externalcreds.models.GA4GHVisa;

public interface VisaComparator {
  /** @return false if visas represent different authorizations */
  boolean authorizationsDiffer(GA4GHVisa visa1, GA4GHVisa visa2);

  boolean visaTypeSupported(GA4GHVisa visa);
}
