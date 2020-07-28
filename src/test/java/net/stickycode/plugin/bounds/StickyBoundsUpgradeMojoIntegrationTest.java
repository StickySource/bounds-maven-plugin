package net.stickycode.plugin.bounds;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.version.Version;
import org.junit.Test;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Node;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import nu.xom.Serializer;
import nu.xom.ValidityException;
import nu.xom.XPathContext;

public class StickyBoundsUpgradeMojoIntegrationTest {

  @Test
  public void matchVersionRanges() {
    StickyBoundsUpgradeMojo mojo = new StickyBoundsUpgradeMojo();
    assertThat(mojo.matchVersion("1.0,2").matches()).isFalse();
    assertThat(mojo.matchVersion("[1.0,2]").matches()).isFalse();
    assertThat(mojo.matchVersion("[1.0,2)").matches()).isTrue();
    assertThat(mojo.matchVersion("[1.0,2.0)").matches()).isTrue();
    assertThat(mojo.matchVersion("[1.0,)").matches()).isTrue();
    assertThat(mojo.matchVersion("[1.0,2.3.4)").matches()).isTrue();
    assertThat(mojo.matchVersion("[1.0.4,2.3.4)").matches()).isTrue();
    assertThat(mojo.matchVersion("[1.0.4, 2.3.4)").matches()).isTrue();
    assertThat(mojo.matchVersion("[1.0.4 , 2.3.4)").matches()).isTrue();
    assertThat(mojo.matchVersion("[1.0.4 ,2.3.4)").matches()).isTrue();
    assertThat(mojo.matchVersion("[1.0.4-SNAPSHOT,2.3.4-SNAPSHOT)").matches()).isTrue();
  }

  @Test
  public void upgrade() throws MojoExecutionException {
    StickyBoundsUpgradeMojo mojo = new StickyBoundsUpgradeMojo() {

      @Override
      protected org.eclipse.aether.version.Version highestVersion(Artifact artifact)
          throws MojoExecutionException {
        return new org.eclipse.aether.version.Version() {

          @Override
          public String toString() {
            return "6.7";
          }

          @Override
          public int compareTo(Version o) {
            return 0;
          }
        };
      }
    };

    Dependency dependency = new Dependency();
    assertThat(mojo.resolveLatestVersionRange(dependency, "[1.2,2)").getVersion()).isEqualTo("[6.7,7)");
    assertThat(mojo.resolveLatestVersionRange(dependency, "[2.2,5)").getVersion()).isEqualTo("[6.7,7)");
    assertThat(mojo.resolveLatestVersionRange(dependency, "[6.7,7)").getVersion()).isEqualTo("[6.7,7)");
  }

  @Test
  public void bump()
      throws ValidityException, ParsingException, IOException, MojoExecutionException {
    Document pom = new Builder().build(new File(new File("src/it/update"), "pom.xml"));

    XPathContext context = new XPathContext("mvn", "http://maven.apache.org/POM/4.0.0");

    Nodes versions = pom.query("/mvn:project/mvn:version", context);
    assertThat(versions.size()).isEqualTo(1);

    assertThat(pom.query("/mvn:project/mvn:version[text()='1.1-SNAPSHOT']", context).size()).isEqualTo(1);
    assertThat(pom.query("/mvn:project/mvn:version[text()='2.1-SNAPSHOT']", context).size()).isEqualTo(0);

    new StickyBoundsUpgradeMojo().bumpMajorVersion(pom);

    assertThat(pom.query("/mvn:project/mvn:version[text()='1.1-SNAPSHOT']", context).size()).isEqualTo(0);
    assertThat(pom.query("/mvn:project/mvn:version[text()='2.1-SNAPSHOT']", context).size()).isEqualTo(1);
  }
  @Test
  public void update()
      throws ValidityException, ParsingException, IOException, MojoExecutionException {
    Document pom = new Builder().build(new File(new File("src/it/upgrade"), "pom.xml"));
    Artifact artifact = new DefaultArtifact(
      "net.stickycode",
      "sticky-coercion",
      "jar",
      "",
      "[3.6,4)");

    new StickyBoundsUpgradeMojo().updateDependency(pom, artifact, "[3.1,4)");
    XPathContext context = new XPathContext("mvn", "http://maven.apache.org/POM/4.0.0");

    Nodes versions = pom.query("//mvn:version", context);
    assertThat(versions.size()).isEqualTo(3);
    Nodes nodes = pom.query("//mvn:version[text()='[3.6,4)']", context);
    assertThat(nodes.size()).isEqualTo(1);
    Node node = nodes.get(0);
    assertThat(node.getValue()).isEqualTo("[3.6,4)");
  }

  @Test
  public void updateWithClassifier()
      throws ValidityException, ParsingException, IOException, MojoExecutionException {
    Document pom = new Builder()
      .build(new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("classifiers.xml"))));
    Artifact artifact = new DefaultArtifact(
      "net.stickycode",
      "sticky-coercion",
      "jar",
      "",
      "[2.6,3)");

    new StickyBoundsUpgradeMojo().updateDependency(pom, artifact, "[2.1,3)");
    XPathContext context = new XPathContext("mvn", "http://maven.apache.org/POM/4.0.0");

    Nodes versions = pom.query("//mvn:version", context);
    assertThat(versions.size()).isEqualTo(4);
    Nodes nodes = pom.query("//mvn:version[text()='[2.6,3)']", context);
    assertThat(nodes.size()).isEqualTo(1);
    Node node = nodes.get(0);
    assertThat(node.getValue()).isEqualTo("[2.6,3)");
  }

  @Test
  public void updateTheClassifier()
      throws ValidityException, ParsingException, IOException, MojoExecutionException {
    Document pom = new Builder()
      .build(new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("classifiers.xml"))));
    Artifact artifact = new DefaultArtifact(
      "net.stickycode",
      "sticky-coercion",
      "jar",
      "test-jar",
      "[2.6,3)");

    new StickyBoundsUpgradeMojo().updateDependency(pom, artifact, "[2.6,3)");
    XPathContext context = new XPathContext("mvn", "http://maven.apache.org/POM/4.0.0");

    Nodes versions = pom.query("//mvn:version", context);
    assertThat(versions.size()).isEqualTo(4);
    Nodes nodes = pom.query("//mvn:version[text()='[2.6,3)']", context);
    assertThat(nodes.size()).isEqualTo(1);
    Node node = nodes.get(0);
    assertThat(node.getValue()).isEqualTo("[2.6,3)");
  }

  @Test
  public void writeNamespacesUnchanged()
      throws ValidityException, ParsingException, IOException {
    Document pom = new Builder().build(new File(new File("src/it/upgrade"), "pom.xml"));
    Serializer s = new StickySerializer(new FileOutputStream(new File("target/tmp.xml")), "UTF-8");
    s.write(pom);
  }
}
