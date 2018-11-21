package net.stickycode.plugin.bounds;

public class Changes {

  private boolean update;

  private boolean upgrade;

  public boolean changed() {
    return update || upgrade;
  }

  public boolean change(String version, String version2) {
    if (version.equals(version2))
      return false;

    update = true;

    String[] v1 = version.split("\\.");
    String[] v2 = version2.split("\\.");
    if (v1.length > 0 && v2.length > 0)
      if (!v1[0].equals(v2[0]))
        upgrade = true;

    return true;
  }

  public boolean updated() {
    return update || upgrade;
  }

  public boolean upgraded() {
    return upgrade;
  }

}
