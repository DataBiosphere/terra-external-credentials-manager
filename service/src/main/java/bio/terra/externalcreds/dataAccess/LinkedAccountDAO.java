package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.models.LinkedAccount;
import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class LinkedAccountDAO {

  final JdbcTemplate jdbcTemplate;

  public LinkedAccountDAO(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public LinkedAccount getLinkedAccount(ResultSet rs) throws SQLException {
    return LinkedAccount.builder()
        .id(rs.getInt("id"))
        .userId(rs.getString("user_id"))
        .providerId(rs.getString("provider_id"))
        .refreshToken(rs.getString("refresh_token"))
        .expires(rs.getTimestamp("expires"))
        .externalUserId(rs.getString("external_user_id"))
        .build();
  }

  public LinkedAccount getLinkedAccount(String userId, String providerId) throws SQLException {
    String query = "SELECT * FROM linked_account WHERE user_id = ? and provider_id = ?";
    // TODO: used named parameters here and create a RowMapper to pass into QueryForObject?
    return jdbcTemplate.queryForObject(
        query, (rs, rowNum) -> getLinkedAccount(rs), userId, providerId);
  }
}
