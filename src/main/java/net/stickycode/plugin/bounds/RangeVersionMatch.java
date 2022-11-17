package net.stickycode.plugin.bounds;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.model.Dependency;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.version.Version;

public class RangeVersionMatch {

  private Pattern rangePattern = Pattern.compile("\\[[0-9\\.\\-A-Za-z]+\\s*(,\\s*[0-9\\.\\-A-Za-z]*)?\\)");

  private Pattern fixedPattern = Pattern.compile("\\[(([0-9]+)[0-9\\.\\-A-Za-z]*)\\]");

  private Matcher rangeMatcher;

  private Matcher fixedMatcher;

  private String version;

  public RangeVersionMatch(String version) {
    this.version = version;
    this.fixedMatcher = matchFixedVersion(version);
    this.rangeMatcher = matchVersionRange(version);
  }

  Matcher matchVersionRange(String version) {
    return rangePattern.matcher(version);
  }

  Matcher matchFixedVersion(String version) {
    return fixedPattern.matcher(version);
  }

  public boolean matches() {
    return fixedMatcher.matches() || rangeMatcher.matches();
  }

  public String newVersionRange(Version highestVersion) {
    if (fixedMatcher.matches())
      return "[" + highestVersion + "]";
    
    return "[" + highestVersion.toString() + "," + majorVersionPlusOne(highestVersion) + ")";
  }

  public String getSearchRange() {
    if (fixedMatcher.matches())
      return "[" + fixedMatcher.group(1) + ",)";

    if (rangeMatcher.matches())
      return version.replaceFirst(rangeMatcher.group(1), ",");

    throw new VersionWasNotARangeException(version);
  }

  private Integer majorVersionPlusOne(Version highestVersion) {
    String[] split = highestVersion.toString().split("\\.");
    return Integer.valueOf(split[0]) + 1;
  }
}
