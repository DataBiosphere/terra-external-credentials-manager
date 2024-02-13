package bio.terra.externalcreds.config;

import bio.terra.externalcreds.logging.LoggerInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.format.FormatterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class WebConfig implements WebMvcConfigurer {
  @Autowired private LoggerInterceptor loggerInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(loggerInterceptor);
  }

  @Override
  public void addFormatters(FormatterRegistry registry) {
    ApplicationConversionService.configure(registry);
  }
}
