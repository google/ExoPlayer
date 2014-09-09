package com.google.android.exoplayer.hls;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Created by martin on 29/08/14.
 */
public class DefaultPacketAllocator {
  static final ArrayList<LinkedList<Packet>> recycledSampleList;

  public static class DefaultPacket extends Packet {
    public DefaultPacket(int type) {
      this.type = type;
    }

    public void release() {
      // do nothing. we will be garbage collected
    }
  }

  static {
    recycledSampleList = new ArrayList<LinkedList<Packet>>(2);
    recycledSampleList.add(new LinkedList<Packet>());
    recycledSampleList.add(new LinkedList<Packet>());
  }

  static public synchronized Packet getPacket(int type) {
    LinkedList<Packet> list = recycledSampleList.get(type);
    if (list.size() > 0) {
      Packet packet = list.removeFirst();
      packet.data.position(0);
      packet.data.limit(packet.data.capacity());
      return packet;
    } else {
      Packet packet = new DefaultPacket(type);
      packet.data = ByteBuffer.allocateDirect(2 * 1024);
      return packet;
    }
  }

  static public synchronized void resizePacket(Packet s, int newSize) {
    ByteBuffer newData = ByteBuffer.allocateDirect(newSize);
    s.data.limit(s.data.position());
    s.data.position(0);
    newData.put(s.data);
    s.data = newData;
  }
}
