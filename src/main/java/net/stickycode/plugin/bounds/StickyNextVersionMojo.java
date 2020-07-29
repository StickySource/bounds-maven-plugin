package net.stickycode.plugin.bounds;

import static java.lang.Integer.valueOf;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.execution.MavenSession;
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

  /**
   * Choose the rule to use in incrementing the version:
   *
   * <dl>
   * <dt>major</dt>
   * <dd>Increment the major version e.g. 1.2 -> 2.1, 20 -> 21.1</dd>
   * <dt>minor</dt>
   * <dd>Increment the second component e.g. 1.2 -> 1.3, 1 -> 1.1, 20.50 -> 20.51</dd>
   * <dt>patch</dt>
   * <dd>Increment the third component e.g. 1.2 -> 1.2.1, 1.2.1 -> 1.2.2</dd>
   * <dt>patchDatetime</dt>
   * <dd>Set the third component to current time in seconds e.g. 1.2 -> 1.2.123456789, 1.2.1 -> 1.2.123456789, 1 ->
   * 1.1.123456789</dd>
   * </dl>
   */
  @Parameter(defaultValue = "minor", required = true)
  private VersionIncrementRule incrementRule = VersionIncrementRule.minor;

  /**
   * The name of the property in the session to set to the next version
   */
  @Parameter
  private String nextVersionProperty;

  /**
   * If the current maven project should be set to the next version, only affects the build session not the pom.xml.
   */
  @Parameter(defaultValue = "true", required = true)
  private boolean updateProjectVersion;

  @Parameter(defaultValue = "false")
  private Boolean includeSnapshots = false;

  /**
   * The current repository/network configuration of Maven.
   */
  @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
  private RepositorySystemSession repositorySession;

  @Parameter(defaultValue = "${session}")
  protected MavenSession mavenSession;

  @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
  private List<RemoteRepository> repositories;

  @Parameter(readonly = true, defaultValue = "${project}")
  private MavenProject project;

  @Component
  private RepositorySystem repository;

  @Override
  public void execute()
      throws MojoExecutionException, MojoFailureException {
    String nextVersion = nextVersion(project.getVersion());

    if (nextVersionProperty != null) {
      project.getProperties().setProperty(nextVersionProperty, nextVersion);
      log("set property %s to %s", nextVersionProperty, nextVersion);
    }

    if (updateProjectVersion) {
      project.setVersion(nextVersion);
      project.getBuild().setFinalName(project.getArtifactId() + "-" + nextVersion);
      log("set project version to %s", nextVersion);
    }
  }

  String nextVersion(String projectVersion) {
    String versionRange = versionRange(projectVersion);

    Version version = highestVersion(versionRange);

    return increment(version.toString());
  }

  /**
   * Increment the given version based on the selected rule
   */
  String increment(String version) {
    String[] components = version.toString().replaceFirst("-SNAPSHOT", "").split("[-\\.]");
    switch (getVersionIncrement()) {
      case major:
        return String.join(".", Integer.toString(valueOf(components[0]) + 1), "1");

      case minor:
        if (components.length < 2)
          return String.join(".", components[0], "1");

        return String.join(".", components[0], Integer.toString(valueOf(components[1]) + 1));

      case patch:
        if (components.length < 3)
          return String.join(".", components[0], components[1], "1");

        return String.join(".", components[0], components[1], Integer.toString(valueOf(components[2]) + 1));

      case patchDatetime:
        if (components.length < 2)
          return String.join(".", components[0], "1", Long.toString(Instant.now(getClock()).getEpochSecond()));

        return String.join(".", components[0], components[1], Long.toString(Instant.now(getClock()).getEpochSecond()));
    }

    throw new RuntimeException("Unknown version increment " + getVersionIncrement());
  }

  VersionIncrementRule getVersionIncrement() {
    return incrementRule;
  }

  Clock getClock() {
    return Clock.systemDefaultZone();
  }

  /**
   * Derive the relevant range to search for to facilitate the correct increment based on the selected rule
   */
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

    throw new RuntimeException("Unknown version increment " + getVersionIncrement());
  }

  /**
   * Get the highest previously released for the current project within the selected increment rule
   */
  Version highestVersion(String versionRange) {
    DefaultArtifact artifact = projectArtifact(versionRange);

    VersionRangeRequest request = new VersionRangeRequest(artifact, repositories, null);
    VersionRangeResult v = resolve(request);

    if (ignoreSnapshots()) {
      List<Version> filtered = new ArrayList<Version>();
      for (Version aVersion : v.getVersions()) {
        if (!aVersion.toString().endsWith("SNAPSHOT")) {
          filtered.add(aVersion);
        }
      }
      v.setVersions(filtered);
    }

    if (v.getHighestVersion() == null) {
      throw (v.getExceptions().isEmpty())
        ? new RuntimeException("Failed to resolve " + artifact.toString())
        : new RuntimeException("Failed to resolve " + artifact.toString(), v.getExceptions().get(0));
    }

    log("resolved %s:%s:%s to %s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), v.getHighestVersion());
    return v.getHighestVersion();
  }

  boolean ignoreSnapshots() {
    return !includeSnapshots;
  }

  /** Get the artifact representing this project **/
  DefaultArtifact projectArtifact(String versionRange) {
    DefaultArtifact artifact = new DefaultArtifact(
      project.getGroupId(),
      project.getArtifactId(),
      project.getPackaging(),
      versionRange);
    return artifact;
  }

  VersionRangeResult resolve(VersionRangeRequest request) {
    try {
      return repository.resolveVersionRange(repositorySession, request);
    }
    catch (VersionRangeResolutionException e) {
      throw new RuntimeException(e);
    }
  }

  private void log(String message, Object... parameters) {
    getLog().info(String.format(message, parameters));
  }

}
