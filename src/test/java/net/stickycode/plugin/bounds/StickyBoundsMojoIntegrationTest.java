package net.stickycode.plugin.bounds;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Node;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import nu.xom.Serializer;
import nu.xom.ValidityException;
import nu.xom.XPathContext;

public class StickyBoundsMojoIntegrationTest {

  @Test
  public void matchVersionRanges() {
    StickyBoundsMojo mojo = new StickyBoundsMojo();
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
  public void updateShiftyPlugin()
      throws ValidityException, ParsingException, IOException, MojoExecutionException {
    XPathContext context = new XPathContext("mvn", "http://maven.apache.org/POM/4.0.0");
    Document pom = new Builder().build(new File(new File("src/it/update-shifty"), "pom.xml"));

    Nodes before = pom.query("//mvn:artifact", context);
    assertThat(before.size()).isEqualTo(1);
    assertThat(before.get(0).getValue()).isEqualTo("net.stickycode.tile:sticky-tile-testing:[2,3)");

    new StickyBoundsMojo().updatePluginConfiguration(pom, "shifty-maven-plugin", "artifact", "net.stickycode.tile:sticky-tile-testing:[2,3)", "net.stickycode.tile:sticky-tile-testing:[2.1,3)");

    Nodes after = pom.query("//mvn:artifact", context);
    assertThat(after.size()).isEqualTo(1);
    assertThat(after.get(0).getValue()).isEqualTo("net.stickycode.tile:sticky-tile-testing:[2.1,3)");
  }

  @Test
  public void updateBoundsPlugin()
      throws ValidityException, ParsingException, IOException, MojoExecutionException {
    XPathContext context = new XPathContext("mvn", "http://maven.apache.org/POM/4.0.0");
    Document pom = new Builder().build(new File(new File("src/it/update-bounds"), "pom.xml"));

    Nodes before = pom.query("//mvn:artifact", context);
    assertThat(before.size()).isEqualTo(1);
    assertThat(before.get(0).getValue()).isEqualTo("net.stickycode.tile:sticky-tile-testing:[2,3)");

    new StickyBoundsMojo().updatePluginConfiguration(pom, "bounds-maven-plugin", "artifact", "net.stickycode.tile:sticky-tile-testing:[2,3)", "net.stickycode.tile:sticky-tile-testing:[2.1,3)");

    Nodes after = pom.query("//mvn:artifact", context);
    assertThat(after.size()).isEqualTo(1);
    assertThat(after.get(0).getValue()).isEqualTo("net.stickycode.tile:sticky-tile-testing:[2.1,3)");
  }

  @Test
  public void update()
      throws ValidityException, ParsingException, IOException, MojoExecutionException {
    Document pom = new Builder().build(new File(new File("src/it/update"), "pom.xml"));
    Artifact artifact = new DefaultArtifact(
      "net.stickycode",
      "sticky-coercion",
      "jar",
      "",
      "[3.6,4)");

    new StickyBoundsMojo().updateDependency(pom, artifact, "[3.1,4)");
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

    new StickyBoundsMojo().updateDependency(pom, artifact, "[2.1,3)");
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

    new StickyBoundsMojo().updateDependency(pom, artifact, "[2.1,3)");
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
    Document pom = new Builder().build(new File(new File("src/it/update"), "pom.xml"));
    Serializer s = new StickySerializer(new FileOutputStream(new File("target/tmp.xml")), "UTF-8");
    s.write(pom);
  }
}
