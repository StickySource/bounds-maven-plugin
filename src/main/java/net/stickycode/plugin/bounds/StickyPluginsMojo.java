package net.stickycode.plugin.bounds;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
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

/**
 * Update the lower bounds of a version range to match the current version. e.g. [1.1,2) might go to [1.3,2)
 */
@Mojo(threadSafe = true, name = "update", requiresDirectInvocation = true)
public class StickyPluginsMojo
    extends AbstractMojo {

  /**
   * The Maven Project.
   */
  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  private MavenProject project;

  /**
   * The entry point to Aether, i.e. the component doing all the work.
   * 
   */
  @Component
  private RepositorySystem repository;

  /**
   * The current repository/network configuration of Maven.
   */
  @Parameter(defaultValue = "${repositorySystemSession}", required = true, readonly = true)
  private RepositorySystemSession session;

  private Pattern versionPattern = Pattern.compile("[0-9.\\-A-Za-z]+");

  /**
   * The project's remote repositories to use for the resolution.
   */
  @Parameter(defaultValue = "${project.remoteProjectRepositories}", required = true, readonly = true)
  private List<RemoteRepository> repositories;

  @Parameter(defaultValue = "false")
  private Boolean includeSnapshots = false;

  @Parameter(defaultValue = "false")
  private Boolean updateProperties = false;

  @Parameter(defaultValue = "false")
  private Boolean failImmediately = false;

  /**
   * The line separator used when rewriting the pom, this to defaults to your platform encoding but if you fix your encoding despite
   * platform then you should use that.
   */
  @Parameter
  private LineSeparator lineSeparator = LineSeparator.defaultValue();

  Matcher matchVersion(String version) {
    return versionPattern.matcher(version);
  }

  @Override
  public void execute() throws MojoExecutionException {
    Document pom = load();
    boolean changed = false;

    changed |= processPlugins(pom);

    if (changed) {
      writeChanges(pom);
    }
  }

  private boolean processPlugins(Document pom)
      throws MojoExecutionException {
    boolean changed = false;
    for (Plugin plugin : project.getBuild().getPlugins()) {
      try {
        String version = plugin.getVersion();
        Artifact artifact = resolveLatestVersionRange(plugin, plugin.getVersion());

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

  private void fail(MojoExecutionException e) throws MojoExecutionException {
    if (!failImmediately) {
      getLog().warn(e.getMessage());
    }
    else {
      throw e;
    }
  }

  private Artifact resolveLatestVersionRange(Plugin plugin, String version) throws MojoExecutionException {
    Matcher versionMatch = matchVersion(version);
    Artifact artifact = new DefaultArtifact(plugin.getGroupId(), plugin.getArtifactId(),
      null, null, version);

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
      if (!System.lineSeparator().equals(lineSeparator.value()))
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
      return new Builder().build(new File("tile.xml"));
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
