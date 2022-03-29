package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ExternalCredsConfigInterface;
import bio.terra.externalcreds.dataAccess.dataEncryption.AES256Utils;
import bio.terra.externalcreds.dataAccess.dataEncryption.DecryptSymmetric;
import bio.terra.externalcreds.dataAccess.dataEncryption.EncryptSymmetric;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPairInternal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class SshKeyPairDAO {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ExternalCredsConfigInterface externalCredsConfigInterface;

  public SshKeyPairDAO(
      NamedParameterJdbcTemplate jdbcTemplate,
      ExternalCredsConfigInterface externalCredsConfigInterface) {
    this.jdbcTemplate = jdbcTemplate;
    this.externalCredsConfigInterface = externalCredsConfigInterface;
  }

  public Optional<SshKeyPairInternal> getSshKeyPair(String userId, SshKeyPairType type) {
    var namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("type", type.name());
    var resourceSelectSql =
        "SELECT id, user_id, type, external_user_email, private_key, public_key, data_encrypt_key, iv"
            + " FROM ssh_key_pair WHERE user_id = :userId AND type = :type";
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(
                resourceSelectSql,
                namedParameters,
                new SshKeyPairInternalRowMapper(externalCredsConfigInterface))));
  }

  public boolean deleteSshKeyPair(String userId, SshKeyPairType type) {
    var query = "DELETE FROM ssh_key_pair WHERE user_id = :userId and type = :type";
    var namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("type", type.name());

    return jdbcTemplate.update(query, namedParameters) > 0;
  }

  public SshKeyPairInternal upsertSshKeyPair(SshKeyPairInternal sshKeyPairInternal)
      throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, NoSuchPaddingException,
          IllegalBlockSizeException, BadPaddingException, InvalidKeyException, IOException {
    String query;
    if (externalCredsConfigInterface.enableEncryption()) {
      query =
          "INSERT INTO ssh_key_pair (user_id, type, private_key, public_key, external_user_email, data_encrypt_key, iv)"
              + " VALUES (:userId, :type, :privateKey, :publicKey, :externalUserEmail, :dataEncryptKey, :iv)"
              + " ON CONFLICT (type, user_id) DO UPDATE SET"
              + " private_key = excluded.private_key,"
              + " public_key = excluded.public_key,"
              + " external_user_email = excluded.external_user_email,"
              + " data_encrypt_key = excluded.data_encrypt_key,"
              + " iv = excluded.iv"
              + " RETURNING id";
    } else {
      query =
          "INSERT INTO ssh_key_pair (user_id, type, private_key, public_key, external_user_email)"
              + " VALUES (:userId, :type, :privateKey, :publicKey, :externalUserEmail)"
              + " ON CONFLICT (type, user_id) DO UPDATE SET"
              + " private_key = excluded.private_key,"
              + " public_key = excluded.public_key,"
              + " external_user_email = excluded.external_user_email"
              + " RETURNING id";
    }

    MapSqlParameterSource namedParameters;
    if (externalCredsConfigInterface.enableEncryption()) {
      SecretKey secretKey = AES256Utils.generateAES256Key();
      IvParameterSpec iv = AES256Utils.generateIv();
      var cipheredPrivateKey =
          AES256Utils.encrypt(sshKeyPairInternal.getPrivateKey(), secretKey, iv);
      var encryptedDEK =
          EncryptSymmetric.encryptSymmetric(
              externalCredsConfigInterface.getServiceGoogleProject().get(),
              externalCredsConfigInterface.getKeyRingLocation().get(),
              externalCredsConfigInterface.getKeyRingId().get(),
              externalCredsConfigInterface.getKeyId().get(),
              secretKey.getEncoded());
      namedParameters =
          new MapSqlParameterSource()
              .addValue("userId", sshKeyPairInternal.getUserId())
              .addValue("type", sshKeyPairInternal.getType().name())
              .addValue("privateKey", cipheredPrivateKey)
              .addValue("publicKey", sshKeyPairInternal.getPublicKey())
              .addValue("externalUserEmail", sshKeyPairInternal.getExternalUserEmail())
              .addValue("dataEncryptKey", encryptedDEK)
              .addValue("iv", iv.getIV());
    } else {
      namedParameters =
          new MapSqlParameterSource()
              .addValue("userId", sshKeyPairInternal.getUserId())
              .addValue("type", sshKeyPairInternal.getType().name())
              .addValue("privateKey", sshKeyPairInternal.getPrivateKey())
              .addValue("publicKey", sshKeyPairInternal.getPublicKey())
              .addValue("externalUserEmail", sshKeyPairInternal.getExternalUserEmail());
    }
    // generatedKeyHolder will hold the id returned by the query as specified by the RETURNING
    // clause
    var generatedKeyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(query, namedParameters, generatedKeyHolder);

    return sshKeyPairInternal.withId(
        Objects.requireNonNull(generatedKeyHolder.getKey()).intValue());
  }

  private static class SshKeyPairInternalRowMapper implements RowMapper<SshKeyPairInternal> {

    private ExternalCredsConfigInterface externalCredsConfigInterface;

    SshKeyPairInternalRowMapper(ExternalCredsConfigInterface externalCredsConfigInterface) {
      this.externalCredsConfigInterface = externalCredsConfigInterface;
    }

    @Override
    public SshKeyPairInternal mapRow(ResultSet rs, int rowNum) throws SQLException {
      if (externalCredsConfigInterface.enableEncryption()) {
        String secretkey = rs.getString("data_encrypt_key");
        byte[] iv = rs.getBytes("iv");
        try {
          var decryptedDEK =
              DecryptSymmetric.decryptSymmetric(
                  externalCredsConfigInterface.getServiceGoogleProject().get(),
                  externalCredsConfigInterface.getKeyRingLocation().get(),
                  externalCredsConfigInterface.getKeyRingId().get(),
                  externalCredsConfigInterface.getKeyId().get(),
                  secretkey.getBytes(StandardCharsets.UTF_8));
          return new SshKeyPairInternal.Builder()
              .id(rs.getInt("id"))
              .userId(rs.getString("user_id"))
              .type(SshKeyPairType.valueOf(rs.getString("type")))
              .externalUserEmail(rs.getString("external_user_email"))
              .privateKey(AES256Utils.decrypt(rs.getString("private_key"), decryptedDEK, iv))
              .publicKey(rs.getString("public_key"))
              .build();
        } catch (NoSuchPaddingException
            | NoSuchAlgorithmException
            | InvalidAlgorithmParameterException
            | InvalidKeyException
            | BadPaddingException
            | IllegalBlockSizeException
            | IOException e) {
          throw new ExternalCredsException(e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
      }
      return new SshKeyPairInternal.Builder()
          .id(rs.getInt("id"))
          .userId(rs.getString("user_id"))
          .type(SshKeyPairType.valueOf(rs.getString("type")))
          .externalUserEmail(rs.getString("external_user_email"))
          .privateKey(rs.getString("private_key"))
          .publicKey(rs.getString("public_key"))
          .build();
    }
  }
}
