package bio.terra.externalcreds.models;

public record NihCredentialsManifestEntry(
    String name, String sourceFile, String outputFile, String consentCode) {

  public static NihCredentialsManifestEntry fromManifestLine(String line) {
    String[] splitted = line.split("\t");
    return new NihCredentialsManifestEntry(splitted[0], splitted[1], splitted[2], splitted[3]);
  }
}
