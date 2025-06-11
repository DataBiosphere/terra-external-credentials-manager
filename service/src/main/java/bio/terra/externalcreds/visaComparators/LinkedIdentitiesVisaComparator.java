package bio.terra.externalcreds.visaComparators;

import static bio.terra.externalcreds.services.JwtUtils.GA4GH_VISA_V1_CLAIM;

import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.models.GA4GHVisa;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.util.Objects;
import org.immutables.value.Value;
import org.springframework.stereotype.Component;

@Component
public class LinkedIdentitiesVisaComparator implements VisaComparator {
  public static final String LINKED_IDENTITIES_VISA_TYPE = "LinkedIdentities";

  private final ObjectMapper objectMapper;

  public LinkedIdentitiesVisaComparator(ObjectMapper objectMapper) {
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

    var visa1Data = getVisaData(visa1);
    var visa2Data = getVisaData(visa2);

    // Compare relevant fields, excluding 'asserted', type is checked above
    return Objects.equals(visa1Data.value(), visa2Data.value())
        && Objects.equals(visa1Data.source(), visa2Data.source());
  }

  @Override
  public boolean matchesCriterion(GA4GHVisa visa, VisaCriterionInternal criterion) {
    // LinkedIdentities visas don't support criteria matching in this implementation
    return false;
  }

  @Override
  public boolean visaTypeSupported(GA4GHVisa visa) {
    return LINKED_IDENTITIES_VISA_TYPE.equalsIgnoreCase(visa.getVisaType());
  }

  @Override
  public boolean criterionTypeSupported(VisaCriterionInternal criterion) {
    // This visa type doesn't support any criteria
    return false;
  }

  private LinkedIdentitiesData getVisaData(GA4GHVisa visa) {
    try {
      var visaClaim =
          JWTParser.parse(visa.getJwt()).getJWTClaimsSet().getClaim(GA4GH_VISA_V1_CLAIM);
      return objectMapper.convertValue(visaClaim, new TypeReference<>() {});
    } catch (ParseException e) {
      throw new ExternalCredsException("Error parsing LinkedIdentities visa data", e);
    }
  }

  @Value.Immutable
  @JsonDeserialize(as = ImmutableLinkedIdentitiesData.class)
  public interface LinkedIdentitiesData {
    @JsonProperty("value")
    String value();

    @JsonProperty("source")
    String source();

    class Builder extends ImmutableLinkedIdentitiesData.Builder {}
  }
}
