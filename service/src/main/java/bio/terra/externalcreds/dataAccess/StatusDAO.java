package bio.terra.externalcreds.dataAccess;

import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StatusDAO {

  final JdbcTemplate jdbcTemplate;

  public StatusDAO(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private boolean isConnectionValid(Connection connection) throws SQLException {
    return connection.isValid(1);
  }

  public Boolean isPostgresOk() {
    return this.jdbcTemplate.execute(this::isConnectionValid);
  }
}
