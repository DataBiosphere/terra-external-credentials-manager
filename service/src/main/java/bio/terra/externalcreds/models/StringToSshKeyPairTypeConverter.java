package bio.terra.externalcreds.models;

import bio.terra.common.exception.BadRequestException;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import java.util.Arrays;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class StringToSshKeyPairTypeConverter implements Converter<String, SshKeyPairType> {
  @Override
  public SshKeyPairType convert(@NonNull String type) {
    var result = SshKeyPairType.fromValue(type);
    if (result == null) {
      throw new BadRequestException(
          String.format(
              "Invalid SSH key pair type. Valid options are: %s",
              Arrays.toString(SshKeyPairType.values())));
    }
    return result;
  }
}
