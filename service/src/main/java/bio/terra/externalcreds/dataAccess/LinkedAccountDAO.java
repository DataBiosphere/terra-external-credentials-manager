package bio.terra.externalcreds.dataAccess;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LinkedAccountDAO {

    final JdbcTemplate jdbcTemplate;

    public LinkedAccountDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LinkedAccount getLinkedAccount(String userId, String providerId)
}
