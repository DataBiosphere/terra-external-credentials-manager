package bio.terra.externalcreds.pact;

// Note: This is a regular class, not an Enum, because Java doesn't consider enum values to be
// constant at compile time, which is a problem when trying to use these states in test annotations

public class ProviderStates {
  public static final String USER_IS_REGISTERED = "a user is registered";
  public static final String ECM_IS_OK = "ECM is ok";
}
