package bio.terra.externalcreds.visaComparators;

import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.TokenTypeEnum;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;

public class RASv1_1Test extends BaseTest {
  private final String testVisaJwt =
      "ew0KICAidHlwIjogIkpXVCIsDQogICJhbGciOiAiUlMyNTYiLA0KICAia2lkIjogImRlZmF1bHRfc3NsX2tleSINCn0.ew0KICAiaXNzIjogImh0dHBzOi8vc3Rzc3RnLm5paC5nb3YiLA0KICAic3ViIjogIkFDU2dVS01MazFqS3pWN3NFcUZPU3NIYWoxdENtY2t4Z0l2aElOY1pDamMiLCANCiAgImlhdCI6IDE2MzIxMzE4NzUsDQogICJleHAiOiAxNjMyMTc1MDc1LA0KICAic2NvcGUiOiAib3BlbmlkIGdhNGdoX3Bhc3Nwb3J0X3YxIiwNCiAgImp0aSI6ICI4YmZiYTFjMy1kZGVjLTQ2ZjMtOGI2Mi1lN2QxMTE3ZDE3OGIiLA0KICAidHhuIjogImI2MmM2ODc3MGJlNDdmNWQuOTZhZTgxYTM3YjUzMDE1YiIsDQogICJnYTRnaF92aXNhX3YxIjogeyANCiAgICAgInR5cGUiOiAiaHR0cHM6Ly9yYXMubmloLmdvdi92aXNhcy92MS4xIiwgDQogICAgICJhc3NlcnRlZCI6IDE2MzIxMzE4NzUsDQogICAgICJ2YWx1ZSI6ICJodHRwczovL3N0c3N0Zy5uaWguZ292L3Bhc3Nwb3J0L2RiZ2FwL3YxLjEiLA0KICAgICAic291cmNlIjogImh0dHBzOi8vbmNiaS5ubG0ubmloLmdvdi9nYXAiLA0KICAgICAiYnkiOiAiZGFjIn0sDQogICAgICJyYXNfZGJnYXBfcGVybWlzc2lvbnMiOiBbDQogICAgICAgICANCiAgICAgXSANCn0.FlqNv9Wltg5wnpdwasI0myvda-rz6eDAgZdxng9iq9dMswqFQSDjrtRRkkADJEllbYRN6TFHkLbe5uMvhon-W1FH5E1bt8_KMWgjUFmLOC76FiTJDz8FFZ064xBaCxHlMzGSZgr8Kf2ccjD8gcjavyNWUdd5iduRLEUsvOyR11LLtmWLofmRbSLObhfHmzeDw2yuH4UCMoAg2N9z4579Dqb7D1oHNoRDeq4GEqZOcV9_MLPYq_VXWKcWFBMpC-HoSViBu4ebEpT_2jwYtmWZCC5RGZwSL_B9HoO8ySh1VAhnkK31JWpekfEs2FgUxm8uB9A5o8I3AHbjIVeVinyf0g";

  @Test
  void testEqualJwt() {
    var comparator = new RASv1_1();

    var visa =
        new GA4GHVisa.Builder()
            .jwt(testVisaJwt)
            .visaType(RASv1_1.RAS_VISAS_V_1_1)
            .tokenType(TokenTypeEnum.access_token)
            .expires(new Timestamp(0))
            .issuer("https://stsstg.nih.gov")
            .build();

    assertTrue(comparator.authorizationsDiffer(visa, visa));
  }
}
