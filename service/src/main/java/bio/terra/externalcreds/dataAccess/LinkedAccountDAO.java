package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.models.LinkedAccount;
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

  public LinkedAccount getLinkedAccount(String userId, String providerId) throws SQLException {
    String query = "SELECT * FROM linked_account WHERE user_id = ? and provider_id = ?";
    // TODO: used named parameters here

    //    System.out.println(query);
    //    PreparedStatement ps = connection.prepareStatement(query);
    //    ps.setString(1, userId);
    //    ps.setString(2, providerId);
    //    ResultSet rs = ps.executeQuery();
    //
    //    LinkedAccount link =
    //        LinkedAccount.builder()
    //            .id(rs.getInt("id"))
    //            .userId(rs.getString("user_id"))
    //            .providerId(rs.getString("provider_id"))
    //            .refreshToken(rs.getString("refresh_token"))
    //            .expires(rs.getTimestamp("expires"))
    //            .externalUserId(rs.getString("external_user_id"))
    //            .build();
    //    return link;

    // return jdbcTemplate.queryForObject(query, LinkedAccount.class, userId, providerId);

    // TODO create a RowMapper for this, so we have a reusable thing
    return jdbcTemplate.queryForObject(
        query,
        (rs, rowNum) ->
            LinkedAccount.builder()
                .id(rs.getInt("id"))
                .userId(rs.getString("user_id"))
                .providerId(rs.getString("provider_id"))
                .refreshToken(rs.getString("refresh_token"))
                .expires(rs.getTimestamp("expires"))
                .externalUserId(rs.getString("external_user_id"))
                .build(),
        userId,
        providerId);
  }
}
