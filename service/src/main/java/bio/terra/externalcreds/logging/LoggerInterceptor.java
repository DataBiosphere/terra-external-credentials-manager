package bio.terra.externalcreds.logging;

import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.AuthenticatedUserRequest;
import bio.terra.common.iam.AuthenticatedUserRequestFactory;
import com.google.gson.Gson;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

@Component
@Slf4j
public record LoggerInterceptor(AuthenticatedUserRequestFactory authenticatedUserRequestFactory)
    implements HandlerInterceptor {

  // Constants for requests coming in that aren't authenticated
  private static final String UNAUTHED_USER_ID = "N/A";
  private static final String UNAUTHED_EMAIL = "N/A";

  // Don't log requests for URLs that end with any of   the following paths
  private static final Set<String> LOG_EXCLUDE_LIST = Set.of("/status", "/version");

  private static final String REQUEST_START_ATTRIBUTE = "x-request-start";
  private static final long NOT_FOUND_DURATION = -1;

  // A Java char is 16 bits, and Stackdiver's limit is 256kb.
  // Although this works out to 128,000 chars, we limit to 100,000 to allow for the rest of
  // the log message
  private static final int STACKDRIVER_MAX_CHARS = 100000;

  @Autowired
  public LoggerInterceptor {}

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    request.setAttribute(REQUEST_START_ATTRIBUTE, System.currentTimeMillis());
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {

    String userId;
    String userEmail;
    try {
      AuthenticatedUserRequest userReq = authenticatedUserRequestFactory.from(request);
      userId = userReq.getSubjectId();
      userEmail = userReq.getEmail();
    } catch (UnauthorizedException e) {
      userId = UNAUTHED_USER_ID;
      userEmail = UNAUTHED_EMAIL;
    }

    String url = request.getServletPath();
    String method = request.getMethod();
    Map<String, String[]> paramMap = request.getParameterMap();
    Gson gson = new Gson();
    String paramString = gson.toJson(paramMap);
    String responseStatus = Integer.toString(response.getStatus());
    long requestDuration;
    Long requestStartTime = (Long) request.getAttribute(REQUEST_START_ATTRIBUTE);
    if (requestStartTime != null) {
      requestDuration = System.currentTimeMillis() - requestStartTime;
    } else {
      requestDuration = NOT_FOUND_DURATION;
    }
    // skip logging the status endpoint
    if (LOG_EXCLUDE_LIST.stream().noneMatch(url::equals)) {

      String requestPath;
      try {
        URI uri = new URI(request.getRequestURI());
        requestPath = uri.getPath();
      } catch (URISyntaxException e) {
        log.error("Error parsing request path. Logging the full URI instead.", e);
        requestPath = request.getRequestURI();
      }

      Map<String, String> stackDriverPayload = new HashMap<>();
      if (RequestMethod.POST.name().equalsIgnoreCase(method)
          || RequestMethod.PUT.name().equalsIgnoreCase(method)) {
        stackDriverPayload.put("userId", userId);
        stackDriverPayload.put("userEmail", userEmail);
        stackDriverPayload.put("params", paramString);
        stackDriverPayload.put("duration", Long.toString(requestDuration));
      }
      // Log the message, and include the supplementary JSON as an additional arg.
      // If GoogleJsonLayout has been loaded, it will merge the JSON into the structured log output
      // for ingestion by Cloud Logging. If the default logback layout is being used, the JSON
      // argument will be ignored.
      String message = String.format("%s %s %s", method, requestPath, responseStatus);
      if (response.getStatus() >= 400) {
        log.warn(message, stackDriverPayload);
      } else {
        log.info(message, stackDriverPayload);
      }
    } else {
      log.debug("Received request at {}", url);
    }

    if (ex != null) {
      log.error("An error occurred processing this request: ", ex);
    }
  }
}
