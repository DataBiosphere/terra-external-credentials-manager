package bio.terra.externalcreds;

import bio.terra.externalcreds.generated.api.PublicApi;
import java.sql.SQLException;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
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

  @Override
  @GetMapping("/status")
  public ResponseEntity<Void> getStatus() {
    try {
      if (Objects.requireNonNull(this.jdbcTemplate.getDataSource()).getConnection().isValid(0)) {
        return new ResponseEntity<>(HttpStatus.OK);
      } else {
        return new ResponseEntity<>(HttpStatus.EXPECTATION_FAILED);
      }
    } catch (SQLException e) {
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
