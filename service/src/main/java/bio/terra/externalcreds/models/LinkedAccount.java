package bio.terra.externalcreds.models;

import lombok.Builder;

@Builder
public class LinkedAccount {
    final int id;
    final String userId;
    final String providerId;
    final String refreshToken;
    final String expires;
    final String externalUserId;
}
