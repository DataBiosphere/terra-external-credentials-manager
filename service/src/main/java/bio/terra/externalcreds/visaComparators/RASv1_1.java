package bio.terra.externalcreds.visaComparators;

import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.models.GA4GHVisa;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.util.Set;
import org.immutables.value.Value;
import org.springframework.stereotype.Component;

@Component
public class RASv1_1 implements VisaComparator {
  public static final String RAS_VISAS_V_1_1 = "https://ras.nih.gov/visas/v1.1";
  public static final String DBGAP_CLAIM = "ras_dbgap_permissions";

  private final ObjectMapper objectMapper;

  public RASv1_1(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean authorizationsMatch(GA4GHVisa visa1, GA4GHVisa visa2) {
    if (!visa1.getVisaType().equalsIgnoreCase(visa2.getVisaType())) {
      return false;
    }
    if (!visaTypeSupported(visa1)) {
      throw new IllegalArgumentException(
          String.format("visa type not supported: [%s]", visa1.getVisaType()));
    }

    try {
      var visa1Permissions = getVisaPermissions(visa1);
      var visa2Permissions = getVisaPermissions(visa2);

      return visa1Permissions.equals(visa2Permissions);
    } catch (ParseException | JsonProcessingException e) {
      throw new ExternalCredsException("error parsing RAS v1.1 visa", e);
    }
  }

  private Set<DbGapPermission> getVisaPermissions(GA4GHVisa visa)
      throws ParseException, JsonProcessingException {
    var visaClaim =
        JWTParser.parse(visa.getJwt()).getJWTClaimsSet().getClaim(DBGAP_CLAIM).toString();
    var visaPermissions =
        objectMapper.readValue(visaClaim, new TypeReference<Set<DbGapPermission>>() {});
    return visaPermissions;
  }

  @Override
  public boolean visaTypeSupported(GA4GHVisa visa) {
    return visa.getVisaType().equalsIgnoreCase(RAS_VISAS_V_1_1);
  }

  /**
   * Object representing fields of interest in the ras_dbgap_permissions array of a RASv1.1 visa.
   * This is a nested interface because it is specific to v1.1.
   */
  @Value.Immutable
  @JsonDeserialize(as = ImmutableDbGapPermission.class)
  interface DbGapPermission extends WithDbGapPermission {
    @JsonProperty("phs_id")
    String getPhsId();

    @JsonProperty("consent_group")
    String getConsentGroup();

    String getRole();

    class Builder extends ImmutableDbGapPermission.Builder {}
  }
}
