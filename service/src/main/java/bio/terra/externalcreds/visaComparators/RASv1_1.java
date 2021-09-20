package bio.terra.externalcreds.visaComparators;

import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.models.GA4GHVisa;
import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONCompare;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class RASv1_1 implements VisaComparator {
  public static final String RAS_VISAS_V_1_1 = "https://ras.nih.gov/visas/v1.1";
  public static final String DBGAP_CLAIM = "ras_dbgap_permissions";

  @Override
  public boolean authorizationsDiffer(GA4GHVisa visa1, GA4GHVisa visa2) {
    if (!visaTypeSupported(visa1)) {
      throw new IllegalArgumentException(
          String.format("visa type not supported: [%s]", visa1.getVisaType()));
    }
    if (!visa1.getVisaType().equalsIgnoreCase(visa2.getVisaType())) {
      return false;
    }

    try {
      var visa1Claim =
          JWTParser.parse(visa1.getJwt()).getJWTClaimsSet().getClaim(DBGAP_CLAIM).toString();
      var visa2Claim =
          JWTParser.parse(visa2.getJwt()).getJWTClaimsSet().getClaim(DBGAP_CLAIM).toString();

      return JSONCompare.compareJSON(visa1Claim, visa2Claim, JSONCompareMode.NON_EXTENSIBLE)
          .passed();
    } catch (JSONException | ParseException e) {
      throw new ExternalCredsException("error parsing RAS v1.1 visa", e);
    }
  }

  @Override
  public boolean visaTypeSupported(GA4GHVisa visa) {
    return visa.getVisaType().equalsIgnoreCase(RAS_VISAS_V_1_1);
  }
}
