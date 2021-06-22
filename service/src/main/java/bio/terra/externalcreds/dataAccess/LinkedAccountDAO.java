package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.models.LinkedAccount;
import java.sql.Connection;
import java.sql.PreparedStatement;
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

  private Connection getConnection(Connection connection) throws SQLException {
    if (connection.isValid(1)) {
      System.out.println("Connection not valid from the beginning! ");
    }
    return connection;
  }

  public LinkedAccount getLinkedAccount(String userId, String providerId) throws SQLException {
    // Is this too hacky of a way to get this??
    Connection connection = this.jdbcTemplate.execute(this::getConnection);

    String query = "SELECT * FROM linked_account WHERE user_id = ? and provider_id = ?";
    System.out.println(query);
    PreparedStatement ps = connection.prepareStatement(query);
    ps.setString(1, userId);
    ps.setString(2, providerId);
    ResultSet rs = ps.executeQuery();

    LinkedAccount link =
        LinkedAccount.builder()
            .id(rs.getInt("id"))
            .userId(rs.getString("user_id"))
            .providerId(rs.getString("provider_id"))
            .refreshToken(rs.getString("refresh_token"))
            .expires(rs.getTimestamp("expires"))
            .externalUserId(rs.getString("external_user_id"))
            .build();
    return link;
    // return jdbcTemplate.queryForObject(query, LinkedAccount.class, userId, providerId);

    // jdbcTemplate.queryForObject, populates a single object

    // example from the interwebs
    //    @Override
    //    public Optional<Book> findById(Long id) {
    //      return jdbcTemplate.queryForObject(
    //              "select * from books where id = ?",
    //              new Object[]{id},
    //              (rs, rowNum) ->
    //                      Optional.of(new Book(
    //                              rs.getLong("id"),
    //                              rs.getString("name"),
    //                              rs.getBigDecimal("price")
    //                      ))
    //      );
    //    }
    //    return jdbcTemplate.queryForObject(
    //            query,
    //            new LinkedAccount[]{},
    //            (resultId, resultUserId, resultProviderId, resultRefreshToken, resultExpires,
    // resultExternalUserId) ->
    //                    Optional.of(LinkedAccount.builder().id(4).build())
    //    );
  }
}
