package bio.terra.externalcreds.models;

import com.google.cloud.storage.Blob;

public record NihCredentialsBlob(NihCredentialsManifestEntry entry, Blob blob) {}
