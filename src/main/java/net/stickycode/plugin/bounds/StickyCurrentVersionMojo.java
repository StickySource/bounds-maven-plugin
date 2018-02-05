package net.stickycode.plugin.bounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

@Mojo(name = "current-version", threadSafe = true, defaultPhase = LifecyclePhase.VALIDATE)
public class StickyCurrentVersionMojo
    extends AbstractMojo {

  /**
   * The current repository/network configuration of Maven.
   */
  @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
  private RepositorySystemSession session;

  @Parameter(required = true)
  private Map<String, String> coordinates;

  @Parameter(defaultValue = "false")
  private Boolean includeSnapshots = false;

  @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
  private List<RemoteRepository> repositories;

  @Parameter(readonly = true, defaultValue = "${project}")
  private MavenProject project;

  @Component
  private RepositorySystem repository;

  @Override
  public void execute()
      throws MojoExecutionException, MojoFailureException {
    extractVersions(coordinates);
  }

  void extractVersions(Map<String, String> coordinates) throws MojoExecutionException {
    for (String library : coordinates.keySet()) {
      String range = coordinates.get(library);
      Version version = highestVersion(new DefaultArtifact(range));
      log("setting %s to %s for %s", library, version, range);
      project.getProperties().setProperty(library, version.toString());
    }
  }

  private Version highestVersion(Artifact artifact) throws MojoExecutionException {
    VersionRangeRequest request = new VersionRangeRequest(artifact, repositories, null);
    VersionRangeResult v = resolve(request);

    if (!includeSnapshots) {
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
        ? new MojoExecutionException("Failed to resolve " + artifact.toString())
        : new MojoExecutionException("Failed to resolve " + artifact.toString(), v.getExceptions().get(0));
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
