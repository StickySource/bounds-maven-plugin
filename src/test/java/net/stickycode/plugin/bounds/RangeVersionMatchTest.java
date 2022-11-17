package net.stickycode.plugin.bounds;

import static org.assertj.core.api.StrictAssertions.assertThat;

import org.apache.maven.model.Dependency;
import org.junit.Test;

public class RangeVersionMatchTest {

  @Test
  public void nomatch() {
    assertThat(new RangeVersionMatch("1.4"));
    assertThat(new RangeVersionMatch("1.17-SNAPSHOT"));
  }

  @Test
  public void matchVersionRange() {
    RangeVersionMatch mojo = new RangeVersionMatch("");
    assertThat(mojo.matchVersionRange("[1.0,2)").matches()).isTrue();
    assertThat(mojo.matchVersionRange("[1.0]").matches()).isFalse();
    assertThat(mojo.matchVersionRange("[1]").matches()).isFalse();
    assertThat(mojo.matchVersionRange("[1.2.3]").matches()).isFalse();
    assertThat(mojo.matchVersionRange("1.0,2").matches()).isFalse();
    assertThat(mojo.matchVersionRange("[1.0,2]").matches()).isFalse();
    assertThat(mojo.matchVersionRange("[1.0,2.0)").matches()).isTrue();
    assertThat(mojo.matchVersionRange("[1.0,)").matches()).isTrue();
    assertThat(mojo.matchVersionRange("[1.0,2.3.4)").matches()).isTrue();
    assertThat(mojo.matchVersionRange("[1.0.4,2.3.4)").matches()).isTrue();
    assertThat(mojo.matchVersionRange("[1.0.4, 2.3.4)").matches()).isTrue();
    assertThat(mojo.matchVersionRange("[1.0.4 , 2.3.4)").matches()).isTrue();
    assertThat(mojo.matchVersionRange("[1.0.4 ,2.3.4)").matches()).isTrue();
    assertThat(mojo.matchVersionRange("[1.0.4-SNAPSHOT,2.3.4-SNAPSHOT)").matches()).isTrue();
  }

  @Test
  public void matchFixedVersion() {
    RangeVersionMatch mojo = new RangeVersionMatch("");
    assertThat(mojo.matchFixedVersion("[1]").matches()).isTrue();
    assertThat(mojo.matchFixedVersion("[1.0]").matches()).isTrue();
    assertThat(mojo.matchFixedVersion("[1.0.4]").matches()).isTrue();
    assertThat(mojo.matchFixedVersion("[1.0.4-SNAPSHOT]").matches()).isTrue();
    assertThat(mojo.matchFixedVersion("1.0,2").matches()).isFalse();
    assertThat(mojo.matchFixedVersion("[1.0,2]").matches()).isFalse();
    assertThat(mojo.matchFixedVersion("[1.0,2)").matches()).isFalse();
    assertThat(mojo.matchFixedVersion("[1.0,2.0)").matches()).isFalse();
    assertThat(mojo.matchFixedVersion("[1.0.4, 2.3.4)").matches()).isFalse();
    assertThat(mojo.matchFixedVersion("[1.0.4 , 2.3.4)").matches()).isFalse();
    assertThat(mojo.matchFixedVersion("[1.0.4 ,2.3.4)").matches()).isFalse();
    assertThat(mojo.matchFixedVersion("[1.0.4-SNAPSHOT,2.3.4-SNAPSHOT)").matches()).isFalse();
  }

  @Test
  public void checkSearchRange() {
    checkSearchRange("[1,2)", "[1,)");
    checkSearchRange("[1.0,2)", "[1.0,)");
    checkSearchRange("[1]", "[1,)");
    checkSearchRange("[1.2]", "[1.2,)");
    checkSearchRange("[6.2.6]", "[6.2.6,)");
    checkSearchRange("[1.0.2,2)", "[1.0.2,)");
    checkSearchRange("[4,5)", "[4,)");
    checkSearchRange("[1.0.4 ,2.3.4)", "[1.0.4 ,)");
    checkSearchRange("[1.0.4-SNAPSHOT,2.3.4-SNAPSHOT)", "[1.0.4-SNAPSHOT,)");
  }

  private void checkSearchRange(String version, String expected) {
    assertThat(new RangeVersionMatch(version).getSearchRange()).isEqualTo(expected);
  }
}
