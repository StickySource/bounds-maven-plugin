package net.stickycode.plugin.bounds;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.aether.version.Version;

public class RangeVersionMatch {

  private Pattern rangePattern = Pattern.compile("\\[[0-9\\.\\-A-Za-z]+\\s*(,\\s*[0-9\\.\\-A-Za-z]*)?\\)");

  private Pattern fixedPattern = Pattern.compile("\\[(([0-9]+)[0-9\\.\\-A-Za-z]*)\\]");

  private Matcher rangeMatcher;

  private Matcher fixedMatcher;

  private String version;

  private boolean allowFixedContractBumps = false;

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
      if (allowFixedContractBumps)
        return "[" + fixedMatcher.group(1) + ",)";
      else
        return "[" + fixedMatcher.group(1) + "," + majorVersionPlusOne(fixedMatcher.group(1)) + ")";

    if (rangeMatcher.matches())
      return version.replaceFirst(rangeMatcher.group(1), ",");

    throw new VersionWasNotARangeException(version);
  }

  private Integer majorVersionPlusOne(Version highestVersion) {
    return majorVersionPlusOne(highestVersion.toString());
  }

  private Integer majorVersionPlusOne(String highestVersion) {
    String[] split = highestVersion.split("\\.");
    return Integer.valueOf(split[0]) + 1;
  }

  public RangeVersionMatch allowFixedContractBump() {
    this.allowFixedContractBumps = true;
    return this;
  }
}
