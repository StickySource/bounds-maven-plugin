package net.stickycode.plugin.bounds;

import static java.lang.Integer.valueOf;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

@Mojo(name = "next-version", threadSafe = true, defaultPhase = LifecyclePhase.VALIDATE)
public class StickyNextVersionMojo
    extends AbstractMojo {

  @Parameter(defaultValue = "minor", readonly = true)
  private VersionIncrement versionIncrement = VersionIncrement.minor;

  @Parameter(defaultValue = "nextVersion", readonly = true)
  private String nextVersionProperty;

  /**
   * The current repository/network configuration of Maven.
   */
  @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
  private RepositorySystemSession session;

  @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
  private List<RemoteRepository> repositories;

  @Parameter(readonly = true, defaultValue = "${project}")
  private MavenProject project;

  @Component
  private RepositorySystem repository;

  @Override
  public void execute()
      throws MojoExecutionException, MojoFailureException {
    Version version = highestVersion();

    String nextVersion = increment(version.toString());
    project.getProperties().setProperty(nextVersionProperty, version.toString());
    log("set %s to %s", nextVersionProperty, nextVersion);
  }

  String increment(String version) throws MojoFailureException {
    String[] components = version.toString().split("\\.");
    switch (getVersionIncrement()) {
      case major:
        return String.join(".", Integer.toString(valueOf(components[0]) + 1), "1");
      case minor:
        return String.join(".", components[0], Integer.toString(valueOf(components[1]) + 1));
      case patch:
        return String.join(".", components[0], components[1], Integer.toString(valueOf(components[2]) + 1));
      case patchDatetime:
        return String.join(".", components[0], components[1], Long.toString(Instant.now(getClock()).getEpochSecond()));

    }

    throw new MojoFailureException("Unknown version increment " + getVersionIncrement());
  }

  VersionIncrement getVersionIncrement() {
    return versionIncrement;
  }

  Clock getClock() {
    return Clock.systemDefaultZone();
  }

  String versionRange(String version) {
    String[] components = version.toString().split("[-\\.]");
    switch (getVersionIncrement()) {
      case major:
        return "[" + components[0] + ",)";
      case minor:
        return "[" + components[0] + "," + (valueOf(components[0]) + 1) + ")";
      case patch:
        return "[" + components[0] + "." + components[1] + "," + components[0] + "." + (valueOf(components[1]) + 1) + ")";
      case patchDatetime:
        return "[" + components[0] + "," + (valueOf(components[0]) + 1) + ")";

    }
    // String[] components = project.getVersion().split(":");
    // if (components.length < 1)
    // throw new MojoFailureException("Version " + project.getVersion() + " does not have an obvious major version component");
    //
    return components[0];
  }

  Version highestVersion() {
    DefaultArtifact artifact = new DefaultArtifact(project.getGroupId(), project.getArtifactId(), project.getPackaging(),
      versionRange(project.getVersion()));
    VersionRangeRequest request = new VersionRangeRequest(artifact, repositories, null);
    VersionRangeResult v = resolve(request);

    if (v.getHighestVersion() == null) {
      throw (v.getExceptions().isEmpty())
        ? new RuntimeException("Failed to resolve " + artifact.toString())
        : new RuntimeException("Failed to resolve " + artifact.toString(), v.getExceptions().get(0));
    }

    return v.getHighestVersion();
  }

  private VersionRangeResult resolve(VersionRangeRequest request) {
    try {
      return repository.resolveVersionRange(session, request);
    }
    catch (VersionRangeResolutionException e) {
      throw new RuntimeException(e);
    }
  }

  private void log(String message, Object... parameters) {
    getLog().info(String.format(message, parameters));
  }

}
