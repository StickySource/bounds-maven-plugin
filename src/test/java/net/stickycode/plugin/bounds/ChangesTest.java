package net.stickycode.plugin.bounds;

import static org.assertj.core.api.StrictAssertions.assertThat;

import org.junit.Test;

public class ChangesTest {

  @Test
  public void nochange() {
    nochange("1.1");
    nochange("2.1");
    nochange("1.2");
    nochange("1");
    nochange("11");
    nochange("0.1.1");
  }

  private void nochange(String version) {
    assertThat(new Changes().change(version, version)).isFalse();
  }

  @Test
  public void updates() {
    updates("1.1", "1.2");
    updates("2.1", "2.2");
    updates("1.1", "1.11");
  }

  private void updates(String v1, String v2) {
    Changes changes = new Changes();
    assertThat(changes.change(v1, v2)).isTrue();
    assertThat(changes.updated()).isTrue();
    assertThat(changes.upgraded()).isFalse();
  }

  @Test
  public void upgrades() {
    upgrades("1.1", "2.2");
    upgrades("2.1", "6.2");
    upgrades("1.1", "2.1");
    upgrades("1.11", "2.12");
    upgrades("1.11", "2.1");
  }

  private void upgrades(String v1, String v2) {
    Changes changes = new Changes();
    assertThat(changes.change(v1, v2)).isTrue();
    assertThat(changes.updated()).isTrue();
    assertThat(changes.upgraded()).isTrue();
  }
}
