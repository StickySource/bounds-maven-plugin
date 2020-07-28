package net.stickycode.plugin.bounds;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
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
public class StickyBoundsMojo
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

  private Pattern range = Pattern.compile("\\[[0-9.\\-A-Za-z]+\\s*,\\s*([0-9.\\-A-Za-z]+)?\\)");

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
    return range.matcher(version);
  }

  @Override
  public void execute() throws MojoExecutionException {
    Document pom = load();
    boolean changed = false;

    changed |= processProperties(pom);

    changed |= processPlugins(pom);

    changed |= processDependencies(pom);

    changed |= processDependencyManagement(pom);

    if (changed) {
      writeChanges(pom);
    }
  }

  boolean processPlugins(Document pom) throws MojoExecutionException {
    boolean changed = false;
    for (Plugin plugin : project.getBuild().getPlugins()) {
      if ("tiles-maven-plugin".equals(plugin.getArtifactId()))
        changed |= processTiles(plugin, pom);

      if ("shifty-maven-plugin".equals(plugin.getArtifactId()))
        changed |= processPlugin(plugin, pom, "shifty-maven-plugin", "artifact");

      if ("bounds-maven-plugin".equals(plugin.getArtifactId()))
        changed |= processBounds(plugin, pom);
    }
    return changed;
  }

  private boolean processBounds(Plugin plugin, Document pom) {
    getLog().info("processing bounds plugin");
    for (String gav : lookupConfiguration("artifact", plugin))
      getLog().info(gav);
    return false;
  }

  private boolean processTiles(Plugin plugin, Document pom) {
    getLog().info("processing tiles plugin");
    for (String gav : lookupConfiguration("tile", plugin))
      getLog().info(gav);
    return false;
  }

  private boolean processPlugin(Plugin plugin, Document pom, String pluginId, String blockElement) throws MojoExecutionException {
    getLog().info("processing " + pluginId + " plugin");
    for (String gav : lookupConfiguration("artifact", plugin))
      updatePluginConfiguration(pom, pluginId, "artifact", gav, updateGavBounds(gav));
    return false;
  }

  private String updateGavBounds(String gav) throws MojoExecutionException {
    Artifact artifact = parseCoordinates(gav);
    Artifact u = resolveLatestVersionRange(artifact, artifact.getVersion());
    return String.format("%s:%s:%s:%s:%s", u.getGroupId(), u.getArtifactId(), u.getVersion(), u.getClassifier(), u.getExtension());
  }

  Artifact parseCoordinates(String gav) {
    String[] c = gav.split(":");
    if (c.length < 3)
      throw new RuntimeException("Invalid gav:" + gav);

    return new DefaultArtifact(c[0],
      c[1],
      c.length >= 4 ? c[3] : null,
      c.length == 5 ? c[4] : "jar",
      c[2]);
  }

  private List<String> lookupConfiguration(String name, Plugin plugin) {
    List<String> gavs = new ArrayList<>();
    Xpp3Dom configuration = (Xpp3Dom) plugin.getConfiguration();
    if (configuration != null) {
      Xpp3Dom[] children = configuration.getChildren(name + "s");
      if (children != null)
        for (Xpp3Dom list : children) {
          for (Xpp3Dom element : list.getChildren(name))
            gavs.add(element.getValue());
        }
    }
    return gavs;
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
    Artifact artifact = new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(),
      dependency.getClassifier(), dependency.getType(), version);
    return resolveLatestVersionRange(artifact, version);
  }

  private Artifact resolveLatestVersionRange(Artifact artifact, String version) throws MojoExecutionException {
    Matcher versionMatch = matchVersion(version);

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
              "Dependency Management is an anti pattern, think OO or functional, "
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

  void updatePluginConfiguration(Document pom, String pluginId, String elementName, String gav, String newGav)
      throws MojoExecutionException {
    XPathContext context = new XPathContext("mvn", "http://maven.apache.org/POM/4.0.0");
    Nodes nodes = pom.query(String.format("//mvn:plugin[mvn:artifactId='%s']/mvn:configuration/mvn:%ss/mvn:%s[text()='%s']", pluginId, elementName, elementName, gav),context);

    if (nodes.size() > 0) {
      final Element old = (Element) nodes.get(0);
      Element artifact = new Element(elementName, "http://maven.apache.org/POM/4.0.0");
      artifact.appendChild(newGav);
      old.getParent().replaceChild(old, artifact);
    }
  }

  void updateDependency(Document pom, Artifact artifact, String oldVersion) throws MojoExecutionException {
    updateDependency(pom, dependencyPath(artifact.getArtifactId()), oldVersion, artifact);
  }

  void updateDependencyManagement(Document pom, Artifact artifact, String oldVersion) throws MojoExecutionException {
    updateDependency(pom, dependencyManagementPath(artifact.getArtifactId()), oldVersion, artifact);
  }

  private void updateDependency(Document pom, String dependencyPath, String oldVersion, Artifact artifact)
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
        getLog().info("Updating dependency to " + artifact.toString() + " from " + oldVersion);
        Element version = (Element) versionNodes.get(0);
        if (!version.getValue().startsWith("${") || updateProperties) {
          Element newRange = new Element("version", "http://maven.apache.org/POM/4.0.0");
          newRange.appendChild(artifact.getVersion());
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
