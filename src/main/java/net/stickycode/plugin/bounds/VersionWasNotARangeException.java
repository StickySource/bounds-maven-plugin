package net.stickycode.plugin.bounds;

@SuppressWarnings("serial")
public class VersionWasNotARangeException
    extends RuntimeException {

  public VersionWasNotARangeException(String version) {
    super("Trying to derive a search range from a non range version " + version);
  }

}
