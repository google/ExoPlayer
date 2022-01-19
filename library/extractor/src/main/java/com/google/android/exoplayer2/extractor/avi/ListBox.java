package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * An AVI LIST box, memory resident
 */
public class ListBox extends Box {
  public static final int LIST = 'L' | ('I' << 8) | ('S' << 16) | ('T' << 24);
  //Header List
  public static final int TYPE_HDRL = 'h' | ('d' << 8) | ('r' << 16) | ('l' << 24);

  private final int listType;

  final List<Box> children;

  ListBox(int size, int listType, List<Box> children) {
    super(LIST, size);
    this.listType = listType;
    this.children = children;
  }

  public int getListType() {
    return listType;
  }

  @NonNull
  public List<Box> getChildren() {
    return new ArrayList<>(children);
  }

  @Nullable
  public <T extends Box> T getChild(Class<T> c) {
    for (Box box : children) {
      if (box.getClass() == c) {
        return (T)box;
      }
    }
    return null;
  }

  /**
   * Assume the input is pointing to the list type
   * @param boxFactory
   * @param input
   * @return
   * @throws IOException
   */
  public static ListBox newInstance(final int listSize, BoxFactory boxFactory,
      ExtractorInput input) throws IOException {

    final List<Box> list = new ArrayList<>();
    final ByteBuffer headerBuffer = AviExtractor.allocate(8);
    byte [] bytes = headerBuffer.array();
    input.readFully(bytes, 0, 4);
    final int listType = headerBuffer.getInt();

    long endPos = input.getPosition() + listSize - 4;
    while (input.getPosition() + 8 < endPos) {
      headerBuffer.clear();
      input.readFully(bytes, 0, 8);
      final int type = headerBuffer.getInt();
      final int size = headerBuffer.getInt();
      final Box box;
      if (type == LIST) {
        box = newInstance(size, boxFactory, input);
      } else {
        box = boxFactory.createBox(type, size, input);
      }

      if (box != null) {
        list.add(box);
      }
    }
    return new ListBox(listSize, listType, list);
  }
}
