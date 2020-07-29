package net.stickycode.plugin.bounds;

import static org.assertj.core.api.StrictAssertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.Test;

public class StickyNextVersionMojoTest {

  @Test
  public void incrementMajor() {
    checkIncrementMajor("1", "2.1");
    checkIncrementMajor("1.1", "2.1");
    checkIncrementMajor("2.6", "3.1");
    checkIncrementMajor("6.6", "7.1");
    checkIncrementMajor("20", "21.1");
    checkIncrementMajor("20.10.1.3.FINAL", "21.1");
  }

  @Test
  public void incrementMinor() {
    checkIncrementMinor("1", "1.1");
    checkIncrementMinor("1.0", "1.1");
    checkIncrementMinor("1.1", "1.2");
    checkIncrementMinor("1.1.1", "1.2");
    checkIncrementMinor("2.7", "2.8");
    checkIncrementMinor("2.7.1", "2.8");
    checkIncrementMinor("20.135", "20.136");
    checkIncrementMinor("20.9", "20.10");
    checkIncrementMinor("20.10", "20.11");
    checkIncrementMinor("20.10.1.3.FINAL", "20.11");
  }

  @Test
  public void incrementPatch() {
    checkIncrementPatch("1.0", "1.0.1");
    checkIncrementPatch("1.1", "1.1.1");
    checkIncrementPatch("2.1.1", "2.1.2");
    checkIncrementPatch("20.10", "20.10.1");
    checkIncrementPatch("20.10.1", "20.10.2");
  }

  @Test
  public void incrementPatchDatetime() {
    checkIncrementPatchDatetime("1.2", "1.2.123456789");
    checkIncrementPatchDatetime("1", "1.1.123456789");
    checkIncrementPatchDatetime("1.2.1", "1.2.123456789");
    checkIncrementPatchDatetime("20.10.1.3.FINAL", "20.10.123456789");
  }

  private void checkIncrementMajor(String version, String expectation) {
    checkIncrement(version, expectation, VersionIncrementRule.major);
  }

  private void checkIncrementMinor(String version, String expectation) {
    checkIncrement(version, expectation, VersionIncrementRule.minor);
  }

  private void checkIncrementPatch(String version, String expectation) {
    checkIncrement(version, expectation, VersionIncrementRule.patch);
  }

  private void checkIncrementPatchDatetime(String version, String expectation) {
    checkIncrement(version, expectation, VersionIncrementRule.patchDatetime);
  }

  private void checkIncrement(String version, String expectation, VersionIncrementRule versionIncrement) {
    StickyNextVersionMojo mojo = new StickyNextVersionMojo() {

      @Override
      VersionIncrementRule getVersionIncrement() {
        return versionIncrement;
      }

      @Override
      Clock getClock() {
        return Clock.fixed(Instant.ofEpochMilli(123456789000L), ZoneId.of("Pacific/Auckland"));
      }
    };
    assertThat(mojo.increment(version)).isEqualTo(expectation);
  }

  @Test
  public void versionRangeMajor() {
    checkRangeMajor("1.999-SNAPSHOT", "[1,)");
    checkRangeMajor("2.999-SNAPSHOT", "[2,)");
    checkRangeMajor("2.1.999-SNAPSHOT", "[2,)");
    checkRangeMajor("3.999-SNAPSHOT", "[3,)");
    checkRangeMajor("3.1-SNAPSHOT", "[3,)");
    checkRangeMajor("5.23123-SNAPSHOT", "[5,)");
  }

  @Test
  public void versionRangeMinor() {
    checkRangeMinor("1.999-SNAPSHOT", "[1,2)");
    checkRangeMinor("2.999-SNAPSHOT", "[2,3)");
    checkRangeMinor("2.1.999-SNAPSHOT", "[2,3)");
    checkRangeMinor("3.999-SNAPSHOT", "[3,4)");
    checkRangeMinor("3.1-SNAPSHOT", "[3,4)");
  }

  @Test
  public void versionRangePatch() {
    checkRangePatch("2.1.999-SNAPSHOT", "[2.1,2.2)");
    checkRangePatch("3.1-SNAPSHOT", "[3.1,3.2)");
  }

  @Test
  public void versionRangePatchDatetime() {
    checkRangePatchDatetime("1.999-SNAPSHOT", "[1,2)");
    checkRangePatchDatetime("2.999-SNAPSHOT", "[2,3)");
    checkRangePatchDatetime("2.1.999-SNAPSHOT", "[2,3)");
    checkRangePatchDatetime("3.999-SNAPSHOT", "[3,4)");
    checkRangePatchDatetime("3.1-SNAPSHOT", "[3,4)");
  }

  private void checkRangeMajor(String version, String expectation) {
    checkRange(version, expectation, VersionIncrementRule.major);
  }

  private void checkRangeMinor(String version, String expectation) {
    checkRange(version, expectation, VersionIncrementRule.minor);
  }

  private void checkRangePatch(String version, String expectation) {
    checkRange(version, expectation, VersionIncrementRule.patch);
  }

  private void checkRangePatchDatetime(String version, String expectation) {
    checkRange(version, expectation, VersionIncrementRule.patchDatetime);
  }

  private void checkRange(String version, String expectation, VersionIncrementRule versionIncrement) {
    StickyNextVersionMojo mojo = new StickyNextVersionMojo() {

      @Override
      VersionIncrementRule getVersionIncrement() {
        return versionIncrement;
      }
    };
    assertThat(mojo.versionRange(version)).isEqualTo(expectation);
  }

}
