package net.stickycode.plugin.bounds;

import static org.assertj.core.api.StrictAssertions.assertThat;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.Test;

public class StickyNextVersionMojoTest {

  @Test
  public void versionRangeMajor() throws MojoExecutionException, MojoFailureException {
    checkMajor("1.999-SNAPSHOT", "[1,)");
    checkMajor("2.999-SNAPSHOT", "[2,)");
    checkMajor("2.1.999-SNAPSHOT", "[2,)");
    checkMajor("3.999-SNAPSHOT", "[3,)");
    checkMajor("3.1-SNAPSHOT", "[3,)");
    checkMajor("5.23123-SNAPSHOT", "[5,)");
  }

  @Test
  public void versionRangeMinor() throws MojoExecutionException, MojoFailureException {
    checkMinor("1.999-SNAPSHOT", "[1,2)");
    checkMinor("2.999-SNAPSHOT", "[2,3)");
    checkMinor("2.1.999-SNAPSHOT", "[2,3)");
    checkMinor("3.999-SNAPSHOT", "[3,4)");
    checkMinor("3.1-SNAPSHOT", "[3,4)");
  }

  @Test
  public void versionRangePatch() throws MojoExecutionException, MojoFailureException {
    checkPatch("2.1.999-SNAPSHOT", "[2.1,2.2)");
    checkPatch("3.1-SNAPSHOT", "[3.1,3.2)");
  }

  @Test
  public void versionRangePatchDatetime() throws MojoExecutionException, MojoFailureException {
    checkPatchDatetime("1.999-SNAPSHOT", "[1,2)");
    checkPatchDatetime("2.999-SNAPSHOT", "[2,3)");
    checkPatchDatetime("2.1.999-SNAPSHOT", "[2,3)");
    checkPatchDatetime("3.999-SNAPSHOT", "[3,4)");
    checkPatchDatetime("3.1-SNAPSHOT", "[3,4)");
  }

  private void checkMajor(String version, String expectation) {
    check(version, expectation, VersionIncrement.major);
  }

  private void checkMinor(String version, String expectation) {
    check(version, expectation, VersionIncrement.minor);
  }

  private void checkPatch(String version, String expectation) {
    check(version, expectation, VersionIncrement.patch);
  }

  private void checkPatchDatetime(String version, String expectation) {
    check(version, expectation, VersionIncrement.patchDatetime);
  }

  private void check(String version, String expectation, VersionIncrement versionIncrement) {
    StickyNextVersionMojo mojo = new StickyNextVersionMojo() {

      @Override
      VersionIncrement getVersionIncrement() {
        return versionIncrement;
      }
    };
    assertThat(mojo.versionRange(version)).isEqualTo(expectation);
  }

}
