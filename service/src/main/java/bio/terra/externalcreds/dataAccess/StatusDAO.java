package bio.terra.externalcreds.dataAccess;

import java.sql.SQLException;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StatusDAO {

  final JdbcTemplate jdbcTemplate;

  public StatusDAO(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean isPostgresOk() {
    try {
      return Objects.requireNonNull(this.jdbcTemplate.getDataSource()).getConnection().isValid(0);
    } catch (SQLException e) {
      return false;
    }
  }
}
