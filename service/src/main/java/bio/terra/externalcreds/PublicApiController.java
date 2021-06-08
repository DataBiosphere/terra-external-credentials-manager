package bio.terra.externalcreds;

import bio.terra.externalcreds.generated.api.PublicApi;
import bio.terra.externalcreds.generated.model.SubsystemStatus;
import bio.terra.externalcreds.generated.model.SystemStatus;
import java.sql.SQLException;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PublicApiController implements PublicApi {

  final JdbcTemplate jdbcTemplate;

  public PublicApiController(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private boolean getPostgresStatus() {
    try {
      return Objects.requireNonNull(this.jdbcTemplate.getDataSource()).getConnection().isValid(0);
    } catch (SQLException e) {
      return false;
    }
  }

  @Override
  @GetMapping("/status")
  public ResponseEntity<SystemStatus> getStatus() {
    SubsystemStatus subsystems = new SubsystemStatus();

    subsystems.put("postgres", getPostgresStatus());

    Boolean isOk = !subsystems.containsValue(false);

    return new ResponseEntity<>(
        new SystemStatus().ok(isOk).systems(subsystems),
        isOk ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
