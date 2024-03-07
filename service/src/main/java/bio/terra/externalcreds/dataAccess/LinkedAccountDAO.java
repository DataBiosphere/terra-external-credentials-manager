package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.FenceAccountKey;
import bio.terra.externalcreds.models.LinkedAccount;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class LinkedAccountDAO {
  private final ExternalCredsConfig externalCredsConfig;
  private final BondDatastoreDAO bondDatastoreDAO;
  private static final RowMapper<LinkedAccount> LINKED_ACCOUNT_ROW_MAPPER =
      ((rs, rowNum) ->
          new LinkedAccount.Builder()
              .id(rs.getInt("id"))
              .userId(rs.getString("user_id"))
              .provider(Provider.valueOf(rs.getString("provider")))
              .refreshToken(rs.getString("refresh_token"))
              .expires(rs.getTimestamp("expires"))
              .externalUserId(rs.getString("external_user_id"))
              .isAuthenticated(rs.getBoolean("is_authenticated"))
              .build());

  private final FenceAccountKeyDAO fenceAccountKeyDAO;
  final NamedParameterJdbcTemplate jdbcTemplate;

  public LinkedAccountDAO(
      ExternalCredsConfig externalCredsConfig,
      BondDatastoreDAO bondDatastoreDAO,
      FenceAccountKeyDAO fenceAccountKeyDAO,
      NamedParameterJdbcTemplate jdbcTemplate) {
    this.externalCredsConfig = externalCredsConfig;
    this.bondDatastoreDAO = bondDatastoreDAO;
    this.fenceAccountKeyDAO = fenceAccountKeyDAO;
    this.jdbcTemplate = jdbcTemplate;
  }

  private Optional<LinkedAccount> getLinkedFenceAccount(String userId, Provider provider) {
    var ecmLinkedAccount = getEcmLinkedAccount(userId, provider);
    var bondLinkedAccount = getBondLinkedAccount(userId, provider);
    if (ecmLinkedAccount.isPresent() && bondLinkedAccount.isPresent()) {
      var ecmExpires = ecmLinkedAccount.get().getExpires();
      var bondExpires = bondLinkedAccount.get().getExpires();
      if (ecmExpires.after(bondExpires) || ecmExpires.equals(bondExpires)) {
        // ECM is up to date
        return ecmLinkedAccount;
      } else {
        // ECM is out of date. Update it with the Bond data and return the new ECM Linked Account
        return updateEcmWithBondInfo(bondLinkedAccount.get());
      }
    }
    // ECM is out of date. Port the Bond data into the ECM and return the new ECM Linked Account
    return bondLinkedAccount.map(this::updateEcmWithBondInfo).orElse(ecmLinkedAccount);
  }

  private Optional<LinkedAccount> getBondLinkedAccount(String userId, Provider provider) {
    var bondRefreshTokenEntity = bondDatastoreDAO.getRefreshToken(userId, provider);
    var providerProperties = externalCredsConfig.getProviderProperties(provider);
    var bondLinkedAccount =
        bondRefreshTokenEntity.map(
            refreshTokenEntity ->
                new LinkedAccount.Builder()
                    .provider(provider)
                    .userId(userId)
                    .expires(
                        new Timestamp(
                            refreshTokenEntity
                                .getIssuedAt()
                                .plus(
                                    providerProperties.getLinkLifespan().toDays(), ChronoUnit.DAYS)
                                .toEpochMilli()))
                    .externalUserId(refreshTokenEntity.getUsername())
                    .refreshToken(refreshTokenEntity.getToken())
                    .isAuthenticated(true)
                    .build());
    return bondLinkedAccount.map(this::upsertLinkedAccount);
  }

  private Optional<LinkedAccount> updateEcmWithBondInfo(LinkedAccount bondLinkedAccount) {
    var linkedAccount = upsertLinkedAccount(bondLinkedAccount);
    var bondFenceServiceAccountKey =
        bondDatastoreDAO.getFenceServiceAccountKey(
            bondLinkedAccount.getUserId(), bondLinkedAccount.getProvider());
    var fenceAccountKey =
        bondFenceServiceAccountKey.map(
            bondKey ->
                new FenceAccountKey.Builder()
                    .linkedAccountId(linkedAccount.getId().get())
                    .keyJson(bondKey.getKeyJson())
                    .expiresAt(bondKey.getExpiresAt())
                    .build());

    fenceAccountKey.ifPresent(key -> fenceAccountKeyDAO.upsertFenceAccountKey(key));
    return Optional.of(linkedAccount);
  }

  public void deleteBondLinkedAccount(String userId, Provider provider) {
    bondDatastoreDAO.deleteRefreshToken(userId, provider);
    bondDatastoreDAO.deleteFenceServiceAccountKey(userId, provider);
  }

  @WithSpan
  public Optional<LinkedAccount> getLinkedAccount(String userId, Provider provider) {
    return switch (provider) {
      case RAS, GITHUB -> getEcmLinkedAccount(userId, provider);
      case FENCE, DCF_FENCE, KIDS_FIRST, ANVIL -> getLinkedFenceAccount(userId, provider);
    };
  }

  @WithSpan
  protected Optional<LinkedAccount> getEcmLinkedAccount(String userId, Provider provider) {
    var namedParameters =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("provider", provider.name());
    var query =
        "SELECT * FROM linked_account WHERE user_id = :userId and provider = :provider::provider_enum";
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(query, namedParameters, LINKED_ACCOUNT_ROW_MAPPER)));
  }

  @WithSpan
  public Optional<LinkedAccount> getLinkedAccount(int linkedAccountId) {
    var namedParameters = new MapSqlParameterSource().addValue("linkedAccountId", linkedAccountId);
    var query = "SELECT * FROM linked_account WHERE id = :linkedAccountId";
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(query, namedParameters, LINKED_ACCOUNT_ROW_MAPPER)));
  }

  @WithSpan
  public List<LinkedAccount> getExpiringLinkedAccounts(Timestamp expirationCutoff) {
    var namedParameters = new MapSqlParameterSource("expirationCutoff", expirationCutoff);
    var query =
        "SELECT DISTINCT la.* FROM linked_account la"
            + " JOIN ga4gh_passport passport"
            + " ON passport.linked_account_id = la.id"
            + " LEFT JOIN ga4gh_visa visa"
            + " ON visa.passport_id = passport.id"
            + " WHERE (passport.expires <= :expirationCutoff"
            + " OR visa.expires <= :expirationCutoff)"
            + " AND la.is_authenticated = true";
    return jdbcTemplate.query(query, namedParameters, LINKED_ACCOUNT_ROW_MAPPER);
  }

  @WithSpan
  public LinkedAccount upsertLinkedAccount(LinkedAccount linkedAccount) {
    var query =
        "INSERT INTO linked_account (user_id, provider, refresh_token, expires, external_user_id, is_authenticated)"
            + " VALUES (:userId, :provider::provider_enum, :refreshToken, :expires, :externalUserId, :isAuthenticated)"
            + " ON CONFLICT (user_id, provider) DO UPDATE SET"
            + " refresh_token = excluded.refresh_token,"
            + " expires = excluded.expires,"
            + " external_user_id = excluded.external_user_id,"
            + " is_authenticated = excluded.is_authenticated"
            + " RETURNING id";

    var namedParameters =
        new MapSqlParameterSource()
            .addValue("userId", linkedAccount.getUserId())
            .addValue("provider", linkedAccount.getProvider().name())
            .addValue("refreshToken", linkedAccount.getRefreshToken())
            .addValue("expires", linkedAccount.getExpires())
            .addValue("externalUserId", linkedAccount.getExternalUserId())
            .addValue("isAuthenticated", linkedAccount.isAuthenticated());

    // generatedKeyHolder will hold the id returned by the query as specified by the RETURNING
    // clause
    var generatedKeyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(query, namedParameters, generatedKeyHolder);

    return linkedAccount.withId(Objects.requireNonNull(generatedKeyHolder.getKey()).intValue());
  }

  /**
   * @param userId
   * @param provider
   * @return boolean whether or not an account was found and deleted
   */
  @WithSpan
  public boolean deleteLinkedAccountIfExists(String userId, Provider provider) {
    switch (provider) {
      case FENCE, DCF_FENCE, KIDS_FIRST, ANVIL -> deleteBondLinkedAccount(userId, provider);
    }
    var query =
        "DELETE FROM linked_account WHERE user_id = :userId and provider = :provider::provider_enum";
    var namedParameters =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("provider", provider.name());

    return jdbcTemplate.update(query, namedParameters) > 0;
  }

  public Map<String, LinkedAccount> getLinkedAccountByPassportJwtIds(Set<String> jwtIds) {
    var namedParameters = new MapSqlParameterSource("jwtIds", jwtIds);
    var query =
        "SELECT p.jwt_id, la.* FROM linked_account la"
            + " INNER JOIN ga4gh_passport p ON la.id = p.linked_account_id"
            + " WHERE p.jwt_id in (:jwtIds)";
    return jdbcTemplate
        .query(
            query,
            namedParameters,
            (rs, rowNum) ->
                Map.of(rs.getString("jwt_id"), LINKED_ACCOUNT_ROW_MAPPER.mapRow(rs, rowNum)))
        .stream()
        .reduce( // this reduce collapses a List<Map<>> to a single Map<>
            new HashMap<>(),
            (rhs, lhs) -> {
              rhs.putAll(lhs);
              return rhs;
            });
  }
}
