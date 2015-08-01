package net.stickycode.plugin.bounds;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Node;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import nu.xom.Serializer;
import nu.xom.ValidityException;
import nu.xom.XPathContext;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.fest.assertions.Assertions.assertThat;

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
  public void update()
      throws ValidityException, ParsingException, IOException, MojoExecutionException {
    Document pom = new Builder().build(new File(new File("src/it/reflector"), "pom.xml"));
    Artifact artifact = new DefaultArtifact(
        "net.stickycode",
        "sticky-coercion",
        "jar",
        "",
        "[3.1,4)");

    new StickyBoundsMojo().updateDependency(pom, artifact, "[3.6,4)");
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
    Document pom = new Builder().build(new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("classifiers.xml"))));
    Artifact artifact = new DefaultArtifact(
        "net.stickycode",
        "sticky-coercion",
        "jar",
        "",
        "[2.1,4)");
    
    new StickyBoundsMojo().updateDependency(pom, artifact, "[2.6,3)");
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
    Document pom = new Builder().build(new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("classifiers.xml"))));
    Artifact artifact = new DefaultArtifact(
        "net.stickycode",
        "sticky-coercion",
        "jar",
        "test-jar",
        "[2.1,4)");

    new StickyBoundsMojo().updateDependency(pom, artifact, "[2.6,3)");
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
    Document pom = new Builder().build(new File(new File("src/it/reflector"), "pom.xml"));
    Serializer s = new StickySerializer(new FileOutputStream(new File("target/tmp.xml")), "UTF-8");
    s.write(pom);
  }
}
