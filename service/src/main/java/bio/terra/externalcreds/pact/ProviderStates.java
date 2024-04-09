package bio.terra.externalcreds.pact;

// Note: This is a regular class, not an Enum, because Java doesn't consider enum values to be
// constant at compile time, which is a problem when trying to use these states in annotations in
// TestVerification.java.

public class ProviderStates {
  public static final String USER_IS_REGISTERED = "test_user@test.com is registered with ECM";
  public static final String ECM_IS_OK = "ECM is ok";
}
