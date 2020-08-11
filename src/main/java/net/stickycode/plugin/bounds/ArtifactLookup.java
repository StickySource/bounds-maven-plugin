package net.stickycode.plugin.bounds;

import org.eclipse.aether.artifact.DefaultArtifact;

public class ArtifactLookup {

  private DefaultArtifact artifact;

  private String gav;

  private String propertyName;

  public ArtifactLookup() {
  }

  public ArtifactLookup with(DefaultArtifact defaultArtifact) {
    this.artifact = defaultArtifact;
    if (propertyName == null)
      this.propertyName = defaultArtifact.getArtifactId() + ".version";

    return this;
  }

  public DefaultArtifact getArtifact() {
    return artifact;
  }

  public ArtifactLookup withGav(String gav) {
    this.gav = gav;
    return this;
  }

  public ArtifactLookup withPropertyName(String property) {
    this.propertyName = property;
    return this;
  }

  public String getGav() {
    return gav;
  }

  public String getPropertyName() {
    return propertyName;
  }
}
