package net.stickycode.plugin.bounds;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Node;
import nu.xom.Nodes;
import nu.xom.ParentNode;
import nu.xom.ParsingException;
import nu.xom.Serializer;
import nu.xom.ValidityException;
import nu.xom.XPathContext;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  private Pattern range = Pattern.compile("\\[[0-9.\\-A-Za-z]+\\s*,\\s*([0-9.\\-A-Za-z]+)?\\)");

  /**
   * The project's remote repositories to use for the resolution.
   * 
   * @parameter default-value="${project.remoteProjectRepositories}"
   * @readonly
   */
  private List<RemoteRepository> repositories;

  /**
   * @parameter property="includeSnapshots" default-value="false"
   */
  private Boolean includeSnapshots = false;

  /**
   * @parameter property="updateProperties" default-value="false"
   */
  private Boolean updateProperties = false;

  /**
   * @parameter property="failImmediately" default-value="false"
   */
  private Boolean failImmediately = false;

  /**
   * The line separator used when rewriting the pom, this to defaults to your platform encoding but if you fix your encoding despite
   * platform then you should use that.
   * 
   * @parameter property="lineSeparator"
   */
  private LineSeparator lineSeparator = LineSeparator.defaultValue();

  Matcher matchVersion(String version) {
    return range.matcher(version);
  }

  public void execute() throws MojoExecutionException {
    Document pom = load();
    boolean changed = false;

    changed |= processProperties(pom);

    changed |= processDependencies(pom);

    changed |= processDependencyManagement(pom);

    if (changed) {
      writeChanges(pom);
    }
  }

  private boolean processDependencyManagement(Document pom)
      throws MojoExecutionException {
    boolean changed = false;
    if (project.getDependencyManagement() != null) {
      for (Dependency dependency : project.getDependencyManagement().getDependencies()) {
        try {
          String version = dependency.getVersion();
          Artifact artifact = resolveLatestVersionRange(dependency, dependency.getVersion());

          if (!artifact.getVersion().equals(version)) {
            updateDependencyManagement(pom, artifact, artifact.getVersion());
            changed |= true;
          }
        }
        catch (MojoExecutionException e) {
          fail(e);
        }
      }
    }
    return changed;
  }

  private boolean processDependencies(Document pom)
      throws MojoExecutionException {
    boolean changed = false;
    for (Dependency dependency : project.getDependencies()) {
      try {
        String version = dependency.getVersion();
        Artifact artifact = resolveLatestVersionRange(dependency, dependency.getVersion());

        if (!artifact.getVersion().equals(version)) {
          updateDependency(pom, artifact, artifact.getVersion());
          changed |= true;
        }
      }
      catch (MojoExecutionException e) {
        fail(e);
      }
    }
    return changed;
  }

  private boolean processProperties(Document pom)
      throws MojoExecutionException {
    boolean changed = false;
    for (String propertyName : project.getProperties().stringPropertyNames()) {
      if (propertyName.endsWith(".version")) {
        try {
          final String version = project.getProperties().getProperty(propertyName);
          Dependency dependency = findDependencyUsingVersionProperty(propertyName);
          if (dependency != null) {
            Artifact artifact = resolveLatestVersionRange(dependency, version);
            if (!artifact.getVersion().equals(version)) {
              updateProperty(pom, propertyName, artifact.getVersion());
              changed |= true;
            }
          }
          else {
            getLog().warn("No dependency found using " + propertyName);
          }
        }
        catch (MojoExecutionException e) {
          fail(e);
        }
      }
    }
    return changed;
  }

  private void fail(MojoExecutionException e) throws MojoExecutionException {
    if (!failImmediately) {
      getLog().warn(e.getMessage());
    }
    else {
      throw e;
    }
  }

  private Artifact resolveLatestVersionRange(Dependency dependency, String version) throws MojoExecutionException {
    Matcher versionMatch = matchVersion(version);
    Artifact artifact = new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(),
        dependency.getType(), dependency.getClassifier(), version);

    if (versionMatch.matches()) {

      Version highestVersion = highestVersion(artifact);
      String upperVersion = versionMatch.group(1) != null
          ? versionMatch.group(1)
          : "";
      String newVersion = "[" + highestVersion.toString() + "," + upperVersion + ")";

      artifact = artifact.setVersion(newVersion);
      return artifact;
    }
    else {
      return artifact;
    }
  }

  private Dependency findDependencyUsingVersionProperty(String propertyName) {
    for (Dependency dependency : project.getDependencies()) {
      if (propertyName.equals(dependency.getArtifactId() + ".version")) {
        getLog()
            .warn(
                "If you use dependency composition then you will find that version properties "
                    + "are really not that useful. Its an extra indirection that often you don't need. "
                    + "IMO people take the magic number refactoring too far");
        return dependency;
      }
    }
    if (project.getDependencyManagement() != null) {
      for (Dependency dependency : project.getDependencyManagement().getDependencies()) {
        if (propertyName.equals(dependency.getArtifactId() + ".version")) {
          getLog()
              .warn(
                  "Dependency Management is an anti pattern, think OO or functional is doesn't matter "
                      + "dependencies should be composed NOT inherited");
          return dependency;
        }
      }
    }
    return null;
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
      if (!lineSeparator.equals(System.lineSeparator()))
        getLog().info(String.format("The line separator is configured to %s, not using system line separator", lineSeparator));

      serializer.setLineSeparator(lineSeparator.value());
      return serializer;
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
    catch (FileNotFoundException e) {
      throw new RuntimeException(e);
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

  void updateProperty(Document pom, String propertyName, String newVersion) throws MojoExecutionException {
    XPathContext context = new XPathContext("mvn", "http://maven.apache.org/POM/4.0.0");
    Nodes nodes = pom.query("//mvn:properties", context);

    if (nodes.size() > 0) {
      final Element propertiesElement = (Element) nodes.get(0);
      Elements properties = propertiesElement.getChildElements();
      for (int i = 0; i < properties.size(); i++) {
        Element property = properties.get(i);
        if (property.getLocalName().equals(propertyName)) {
          Element newRange = new Element(propertyName, "http://maven.apache.org/POM/4.0.0");
          newRange.appendChild(newVersion);
          propertiesElement.replaceChild(property, newRange);
        }
      }
    }
  }

  void updateDependency(Document pom, Artifact artifact, String newVersion) throws MojoExecutionException {
    updateDependency(pom, dependencyPath(artifact.getArtifactId()), newVersion, artifact);
  }

  void updateDependencyManagement(Document pom, Artifact artifact, String newVersion) throws MojoExecutionException {
    updateDependency(pom, dependencyManagementPath(artifact.getArtifactId()), newVersion, artifact);
  }

  private void updateDependency(Document pom, String dependencyPath, String newVersion, Artifact artifact)
      throws MojoExecutionException {
    XPathContext context = new XPathContext("mvn", "http://maven.apache.org/POM/4.0.0");
    Nodes nodes = pom.query(dependencyPath, context);

    if (nodes.size() == 0) {
      throw new MojoExecutionException(String.format("Missing <dependency> element for dependency %s, skipping.",
          artifact.getArtifactId()));
    }

    for (int i = 0; i < nodes.size(); i++) {
      Node node = nodes.get(i);
      ParentNode dependency = node.getParent();
      Nodes classifier = dependency.query("mvn:classifier", context);
      if (classifier.size() != 0)
        if (!classifier.get(0).getValue().equals(artifact.getClassifier()))
          return;

      final Nodes versionNodes = dependency.query("mvn:version", context);
      if (versionNodes.size() > 0) {
        getLog().info("Updating " + artifact.toString() + " to " + newVersion);
        Element version = (Element) versionNodes.get(0);
        if (!version.getValue().startsWith("${") || updateProperties) {
          Element newRange = new Element("version", "http://maven.apache.org/POM/4.0.0");
          newRange.appendChild(newVersion);
          dependency.replaceChild(version, newRange);
        }
      }
      else {
        throw new MojoExecutionException(String.format("Missing <version> element for dependency %s, skipping.",
            artifact.getArtifactId()));
      }
    }
  }

  private String dependencyPath(String artifactId) {
    return "//mvn:dependencies/mvn:dependency/mvn:artifactId[text()='" + artifactId + "']";
  }

  private String dependencyManagementPath(String artifactId) {
    return "//mvn:dependencyManagement/mvn:dependencies/mvn:dependency/mvn:artifactId[text()='" + artifactId + "']";
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
