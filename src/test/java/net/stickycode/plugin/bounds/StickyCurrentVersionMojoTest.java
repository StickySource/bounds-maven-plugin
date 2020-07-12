package net.stickycode.plugin.bounds;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.assertj.core.util.Lists;
import org.junit.Test;

public class StickyCurrentVersionMojoTest {

  @Test
  public void sanity() throws MojoExecutionException, MojoFailureException {
    new StickyCurrentVersionMojo().lookupVersions(Lists.emptyList());
  }

}
