package com.google.android.exoplayer.hls;

import android.util.Log;

import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * Created by martin on 29/08/14.
 */
public class TSPacketAllocator {
  private int chunkSize;
  private Chunk writeChunk;
  private TSPacket lastPacket;

  private LinkedList<Chunk> recycledChunks = new LinkedList<Chunk>();
  private static int totalAllocated;

  private static class Chunk {
    ByteBuffer data;
    public boolean done;
    public int offset;
    public int used;

    public Chunk(int size) {
      data = ByteBuffer.allocateDirect(size);
    }
  }

  public class TSPacket extends Packet {
    private Chunk chunk;
    public TSPacket(int type) {
      this.type = type;
    }

    /*public void release() {
      chunk.used -= data.position();
      if (chunk.used == 0 && chunk.done) {
        recycledChunks.add(chunk);
      }
    }*/
  }

  public TSPacketAllocator(int chunkSize) {
    this.chunkSize = chunkSize;
  }

  private void newChunk() {
    if (writeChunk != null) {
      writeChunk.done = true;
    }
    if (recycledChunks.size() == 0) {
      writeChunk = new Chunk(chunkSize);
      totalAllocated += chunkSize;
      //Log.d("alloc", "totalAllocated=" + totalAllocated);
    } else {
      writeChunk = recycledChunks.getFirst();
    }
    writeChunk.offset = 0;
    writeChunk.used = 0;
    writeChunk.done = false;
    writeChunk.data.position(0);
  }

  public Packet allocatePacket(int type) {
    if (lastPacket != null) {
      writeChunk.offset += lastPacket.data.position();
      writeChunk.used += lastPacket.data.position();
    }

    if (writeChunk == null || writeChunk.data.remaining() == 0) {
      newChunk();
    }
    TSPacket packet = new TSPacket(type);
    packet.chunk = writeChunk;

    writeChunk.data.position(writeChunk.offset);
    packet.data = writeChunk.data.slice();

    lastPacket = packet;
    return packet;
  }

  public Packet allocateBiggerPacket(Packet packet) {
    newChunk();

    ByteBuffer data = writeChunk.data.slice();
    packet.data.limit(packet.data.position());
    packet.data.position(0);

    data.put(packet.data);
    packet.data = data;

    lastPacket = (TSPacket)packet;
    return packet;
  }
}
