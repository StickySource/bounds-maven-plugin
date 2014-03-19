package net.stickycode.plugin.bounds;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Node;
import nu.xom.Nodes;
import nu.xom.ParentNode;
import nu.xom.ParsingException;
import nu.xom.Serializer;
import nu.xom.ValidityException;
import nu.xom.XPathContext;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
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

/**
 * @description Update the lower bounds of a version range to match the current version. e.g. [1.1,2) might go to [1.3,2)
 * @goal update
 * @requiresDirectInvocation true
 * @threadSafe
 */
public class StickyBoundsMojo
    extends AbstractMojo {

  /**
   * The Maven Project.
   * 
   * @parameter property="project"
   * @required
   * @readonly
   */
  private MavenProject project;

  /**
   * The entry point to Aether, i.e. the component doing all the work.
   * 
   * @component
   */
  private RepositorySystem repository;

  /**
   * The current repository/network configuration of Maven.
   * 
   * @parameter default-value="${repositorySystemSession}"
   * @readonly
   */
  private RepositorySystemSession session;

  private Pattern range = Pattern.compile("\\[[0-9.\\-A-Za-z]+,([0-9.\\-A-Za-z]+)?\\)");

  /**
   * The project's remote repositories to use for the resolution.
   * 
   * @parameter default-value="${project.remoteProjectRepositories}"
   * @readonly
   */
  private List<RemoteRepository> repositories;

  Matcher matchVersion(String version) {
    return range.matcher(version);
  }

  public void execute() {
    Document pom = load();
    boolean changed = false;

    for (Dependency dependency : project.getDependencies()) {
      String version = dependency.getVersion();
      Matcher versionMatch = matchVersion(version);
      if (versionMatch.matches()) {
        Artifact artifact = new DefaultArtifact(
            dependency.getGroupId(),
            dependency.getArtifactId(),
            dependency.getType(),
            dependency.getClassifier(),
            version);
        Version highestVersion = highestVersion(version, artifact);
        String newVersion = "[" + highestVersion.toString() + "," + versionMatch.group(1) + ")";
        if (!newVersion.equals(version)) {
          getLog().info("Updating " + artifact.toString() + " to " + newVersion);
          update(pom, artifact, newVersion);
          changed |= true;
        }
      }
    }

    if (changed)
      writeChanges(pom);
  }

  private void writeChanges(Document pom) {
    Serializer serializer = createSerialiser();
    try {
      serializer.write(pom);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Serializer createSerialiser() {
    try {
      Serializer serializer = new StickySerializer(new FileOutputStream(project.getFile()), "UTF-8");
      return serializer;
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
    catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private Version highestVersion(String version, Artifact artifact) {
    VersionRangeRequest request = new VersionRangeRequest(artifact, repositories, null);
    VersionRangeResult v = resolve(request);
    if (v.getHighestVersion() == null)
      throw new RuntimeException("Failed to resolve " + artifact.toString(), v.getExceptions().get(0));

    return v.getHighestVersion();
  }

  void update(Document pom, Artifact artifact, String newVersion) {
    XPathContext context = new XPathContext("mvn", "http://maven.apache.org/POM/4.0.0");
    Nodes nodes = pom.query(dependencyPath(artifact.getArtifactId()), context);

    if (nodes.size() == 0)
      throw new RuntimeException("Got none but was expected one matching dependency " + artifact.getArtifactId());

    for (int i = 0; i < nodes.size(); i++) {
      Node node = nodes.get(i);
      ParentNode dependency = node.getParent();
      Nodes classifier = dependency.query("mvn:classifier", context);
      if (classifier.size() != 0)
        if (!classifier.get(0).getValue().equals(artifact.getClassifier()))
          return;

      Node version = dependency.query("mvn:version", context).get(0);
      Element newRange = new Element("version", "http://maven.apache.org/POM/4.0.0");
      newRange.appendChild(newVersion);
      dependency.replaceChild(version, newRange);
    }
  }

  private String dependencyPath(String artifactId) {
    return "//mvn:dependencies/mvn:dependency/mvn:artifactId[text()='" + artifactId + "']";
  }

  private Document load() {
    try {
      return new Builder().build(project.getFile());
    }
    catch (ValidityException e) {
      throw new RuntimeException(e);
    }
    catch (ParsingException e) {
      throw new RuntimeException(e);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private VersionRangeResult resolve(VersionRangeRequest request) {
    try {
      return repository.resolveVersionRange(session, request);
    }
    catch (VersionRangeResolutionException e) {
      throw new RuntimeException(e);
    }
  }

}
