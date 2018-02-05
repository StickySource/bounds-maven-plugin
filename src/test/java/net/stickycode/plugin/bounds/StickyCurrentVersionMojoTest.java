package net.stickycode.plugin.bounds;

import java.util.HashMap;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.Test;

public class StickyCurrentVersionMojoTest {
  
  @Test
  public void sanity() throws MojoExecutionException, MojoFailureException {
    new StickyCurrentVersionMojo().extractVersions(new HashMap<>());
  }
  
}
