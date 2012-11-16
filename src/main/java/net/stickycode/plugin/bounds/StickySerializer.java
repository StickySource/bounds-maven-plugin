package net.stickycode.plugin.bounds;

import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;

import nu.xom.Serializer;

/**
 * TODO figure out how to not reorder attributes and put them on one line
 */
public class StickySerializer
    extends Serializer {

  public StickySerializer(FileOutputStream fileOutputStream, String string) throws UnsupportedEncodingException {
    super(fileOutputStream, string);
  }

}
