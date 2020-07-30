package net.stickycode.plugin.bounds;

import static org.assertj.core.api.StrictAssertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.version.Version;
import org.junit.Test;

public class StickyNextVersionMojoComponentTest {

  private final class VersionImplementation
      implements Version {

    private final String value;

    private VersionImplementation(String highestVersion) {
      this.value = highestVersion;
    }

    @Override
    public String toString() {
      return value;
    }

    @Override
    public int compareTo(Version o) {
      return value.compareTo(o.toString());
    }
  }
  @Test
  public void noMetadata() {
    check(VersionIncrementRule.major, false, "1.999-SNAPSHOT", "1.1", new MetadataNotFoundException(null, null, "Nothing"));
    check(VersionIncrementRule.minor, false,"1.999-SNAPSHOT", "1.1", new MetadataNotFoundException(null, null, "Nothing"));
    check(VersionIncrementRule.patch, false,"1.999-SNAPSHOT", "1.1.1", new MetadataNotFoundException(null, null, "Nothing"));
    check(VersionIncrementRule.patchDatetime, false, "1.999-SNAPSHOT", "1.1.123456789", new MetadataNotFoundException(null, null, "Nothing"));
    check(VersionIncrementRule.major, false, "2.999-SNAPSHOT", "2.1", new MetadataNotFoundException(null, null, "Nothing"));
    check(VersionIncrementRule.minor, false,"2.999-SNAPSHOT", "2.1", new MetadataNotFoundException(null, null, "Nothing"));
    check(VersionIncrementRule.patch, false,"2.999-SNAPSHOT", "2.1.1", new MetadataNotFoundException(null, null, "Nothing"));
    check(VersionIncrementRule.patchDatetime, false, "2.999-SNAPSHOT", "2.1.123456789", new MetadataNotFoundException(null, null, "Nothing"));
  }

  @Test
  public void snapshots() {
    check(VersionIncrementRule.major, false, "1.999-SNAPSHOT", "2.1", null,"1.999-SNAPSHOT");
    check(VersionIncrementRule.minor, false, "1.999-SNAPSHOT", "1.1000", null,"1.999-SNAPSHOT");
    check(VersionIncrementRule.patch, false, "1.999-SNAPSHOT", "1.999.1", null,"1.999-SNAPSHOT");
    check(VersionIncrementRule.patchDatetime, false, "1.999-SNAPSHOT", "1.999.123456789", null,"1.999-SNAPSHOT");
  }

  @Test
  public void noResolvedValues() {
    check(VersionIncrementRule.major, "1.999-SNAPSHOT", "1.1");
    check(VersionIncrementRule.minor, "1.999-SNAPSHOT", "1.1");
    check(VersionIncrementRule.patch, "1.999-SNAPSHOT", "1.1.1");
    check(VersionIncrementRule.patchDatetime, "1.999-SNAPSHOT", "1.1.123456789");
    check(VersionIncrementRule.major, "2.999-SNAPSHOT", "2.1");
    check(VersionIncrementRule.minor, "2.999-SNAPSHOT", "2.1");
    check(VersionIncrementRule.minor, "2.9", "2.9", "2.1", "2.2", "2.8");
    check(VersionIncrementRule.patch, "2.999-SNAPSHOT", "2.1.1");
    check(VersionIncrementRule.patchDatetime, "2.999-SNAPSHOT", "2.1.123456789");
  }

  @Test
  public void major() {
    check(VersionIncrementRule.major, "1.999-SNAPSHOT", "2.1", "1.5");
    check(VersionIncrementRule.major, "1.999-SNAPSHOT", "11.1", "10.5");
    check(VersionIncrementRule.major, "1.2", "2.1", "1.115");
    check(VersionIncrementRule.major, "1.999-SNAPSHOT", "2.1", "1.115", "1.999-SNAPSHOT");
    check(VersionIncrementRule.major, "1.2", "2.1", "1.115", "1.999-SNAPSHOT");
  }

  @Test
  public void minor() {
    check(VersionIncrementRule.minor, "1.999-SNAPSHOT", "1.6", "1.5");
    check(VersionIncrementRule.minor, "1.999-SNAPSHOT", "1.10", "1.9");
    check(VersionIncrementRule.minor, "5.6-SNAPSHOT", "5.10", "5.9");
    check(VersionIncrementRule.minor, "2.9", "2.9", "2.8");
    check(VersionIncrementRule.minor, "1.999-SNAPSHOT", "1.1000", "1.999", "1.999-SNAPSHOT");
  }

  @Test
  public void patch() {
    check(VersionIncrementRule.patch, "1.999-SNAPSHOT", "1.5.1", "1.5");
    check(VersionIncrementRule.patch, "1.2.999-SNAPSHOT", "1.2.2", "1.2.1");
    check(VersionIncrementRule.patch, "5.6-SNAPSHOT", "5.9.1", "5.9");
    check(VersionIncrementRule.patch, "1.999-SNAPSHOT", "1.8.1", "1.8", "1.999-SNAPSHOT");
  }

  @Test
  public void patchDatetime() {
    check(VersionIncrementRule.patchDatetime, "1.999-SNAPSHOT", "1.5.123456789", "1.5");
    check(VersionIncrementRule.patchDatetime, "1.2.999-SNAPSHOT", "1.2.123456789", "1.2.1");
    check(VersionIncrementRule.patchDatetime, "1.2.999-SNAPSHOT", "1.4.123456789", "1.4");
    check(VersionIncrementRule.patchDatetime, "5.6-SNAPSHOT", "5.9.123456789", "5.9");
    check(VersionIncrementRule.patchDatetime, "5.6-SNAPSHOT", "5.9.123456789", "5.9", "1.999-SNAPSHOT");
  }

  private void check(VersionIncrementRule versionIncrement, String projectVersion, String expectation,
      String... resolvedVersions) {
    check(versionIncrement, true, projectVersion, expectation, null, resolvedVersions);
  }

  private void check(VersionIncrementRule versionIncrement, boolean ignoreSnapshots, String projectVersion, String expectation, Exception exception,
      String... resolvedVersions) {
    StickyNextVersionMojo mojo = new StickyNextVersionMojo() {

      @Override
      VersionIncrementRule getVersionIncrement() {
        return versionIncrement;
      }

      @Override
      VersionRangeResult resolve(VersionRangeRequest request) {
        VersionRangeResult result = new VersionRangeResult(request);

        for (String resolvedVersion : resolvedVersions)
          result.addVersion(new VersionImplementation(resolvedVersion));

        if (exception != null)
          result.addException(exception);

        return result;
      }

      @Override
      DefaultArtifact projectArtifact(String versionRange) {
        return new DefaultArtifact("net.stickycode", "sticky-coercion", "jar", versionRange);
      }

      @Override
      Clock getClock() {
        return Clock.fixed(Instant.ofEpochMilli(123456789000L), ZoneId.of("Pacific/Auckland"));
      }

      @Override
      boolean ignoreSnapshots() {
        return ignoreSnapshots;
      }
    };

    assertThat(mojo.nextVersion(projectVersion)).isEqualTo(expectation);
  }

}
