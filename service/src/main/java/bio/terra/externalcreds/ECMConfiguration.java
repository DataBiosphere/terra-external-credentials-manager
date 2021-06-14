package bio.terra.externalcreds;

import java.util.ArrayList;
import java.util.Collection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ECMConfiguration {
  @Bean
  public ArrayList<String> getProviders() {
    return new ArrayList<String>();
  }
}
