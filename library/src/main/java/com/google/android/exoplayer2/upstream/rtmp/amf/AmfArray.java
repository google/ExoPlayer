package com.google.android.exoplayer2.upstream.rtmp.amf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.google.android.exoplayer2.upstream.rtmp.Util;

/**
 * AMF Array
 *
 * @author francois
 */
public class AmfArray implements AmfData {

  private List<AmfData> items;
  private int size = -1;

  @Override
  public void writeTo(OutputStream out) throws IOException {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void readFrom(InputStream in) throws IOException {
    // Skip data type byte (we assume it's already read)
    int length = Util.readUnsignedInt32(in);
    size = 5; // 1 + 4
    items = new ArrayList<AmfData>(length);
    for (int i = 0; i < length; i++) {
      AmfData dataItem = AmfDecoder.readFrom(in);
      size += dataItem.getSize();
      items.add(dataItem);
    }
  }

  @Override
  public int getSize() {
    if (size == -1) {
      size = 5; // 1 + 4
      if (items != null) {
        for (AmfData dataItem : items) {
          size += dataItem.getSize();
        }
      }
    }
    return size;
  }

  /**
   * @return the amount of items in this the array
   */
  public int getLength() {
    return items != null ? items.size() : 0;
  }

  public List<AmfData> getItems() {
    if (items == null) {
      items = new ArrayList<AmfData>();
    }
    return items;
  }

  public void addItem(AmfData dataItem) {
    getItems().add(this);
  }
}
