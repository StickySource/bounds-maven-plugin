package net.stickycode.plugin.bounds;

public enum LineSeparator {
  Mac {

    @Override
    public String value() {
      return "\r";
    }
  },
  Unix {

    @Override
    public String value() {
      return "\n";
    }
  },
  Windows {

    @Override
    public String value() {
      return "\r\n";
    }
  };

  abstract public String value();

  public static LineSeparator defaultValue() {
    if (System.lineSeparator().equals("\n"))
      return Unix;

    if (System.lineSeparator().equals("\r"))
      return Mac;

    return Windows;
  }
}
