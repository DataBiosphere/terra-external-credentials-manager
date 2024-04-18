/*
The purpose of this "enum" is to provide a stable dictionary of "provider states"
for contract testing purposes.
("Provider" in this context shouldn't be confused with the ECM concept of an "external provider";
here it refers to the "consumer-provider pair" in the Pact contract testing framework.)

This "enum" is implemented as a normal class, so that its values can be referenced
in Java annotations which are often used in contract testing code.
(Most values passed into annotations must be constant at compile time.)

These provider states are packaged and released as part of the ECM client module.
You may find it useful to build a local JAR of `client-resttemplate` during development.
See instructions on how to do that in the README at `client-resttemplate/README.md`
 */

package bio.terra.externalcreds.pact;

public class ProviderStates {
  public static final String USER_IS_REGISTERED = "a user is registered";
  public static final String ECM_IS_OK = "ECM is ok";
}
