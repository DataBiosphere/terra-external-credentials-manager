package bio.terra.externalcreds.controllers;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

@Configuration
public class WebMvcConfiguration extends WebMvcConfigurationSupport {
  /**
   * Unregister the default {@link StringHttpMessageConverter} as we want Strings to be handled by
   * the JSON converter.
   *
   * @param converters List of already configured converters
   * @see WebMvcConfigurationSupport#addDefaultHttpMessageConverters(List)
   */
  @Override
  protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    converters.removeIf(c -> c instanceof StringHttpMessageConverter);
  }
}
