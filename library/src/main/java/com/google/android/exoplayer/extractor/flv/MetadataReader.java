package com.google.android.exoplayer.extractor.flv;

import android.util.Log;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.ParsableBitArray;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by joliva on 9/28/15.
 */
public class MetadataReader extends TagReader{

  private static final int METADATA_TYPE_UNKNOWN = -1;
  private static final int METADATA_TYPE_NUMBER = 0;
  private static final int METADATA_TYPE_BOOLEAN = 1;
  private static final int METADATA_TYPE_STRING = 2;
  private static final int METADATA_TYPE_OBJECT = 3;
  private static final int METADATA_TYPE_MOVIE_CLIP = 4;
  private static final int METADATA_TYPE_NULL = 5;
  private static final int METADATA_TYPE_UNDEFINED = 6;
  private static final int METADATA_TYPE_REFERENCE = 7;
  private static final int METADATA_TYPE_ECMA_ARRAY = 8;
  private static final int METADATA_TYPE_STRICT_ARRAY = 10;
  private static final int METADATA_TYPE_DATE = 11;
  private static final int METADATA_TYPE_LONG_STRING = 12;

  public long startTime = C.UNKNOWN_TIME_US;
  public float frameRate;
  public float videoDataRate;
  public float audioDataRate;
  public int height;
  public int width;
  public boolean canSeekOnTime;
  public String httpHostHeader;

  /**
   * @param output A {@link TrackOutput} to which samples should be written.
   */
  public MetadataReader(TrackOutput output) {
    super(output);
  }

  @Override
  public void seek() {

  }

  @Override
  protected void parseHeader(ParsableByteArray data) throws UnsupportedTrack {

  }

  @Override
  protected void parsePayload(ParsableByteArray data, long timeUs) {
    Object messageName = readAMFData(data, METADATA_TYPE_UNKNOWN);
    Object obj = readAMFData(data, METADATA_TYPE_UNKNOWN);

    if(obj instanceof Map) {
      Map<String, Object> extractedMetadata = (Map<String, Object>) obj;
      for (Map.Entry<String, Object> entry : extractedMetadata.entrySet()) {
        if (entry.getValue() == null) {
          continue;
        }
        Log.d("Metadata", "Key: " + entry.getKey() + ", Value: " + entry.getValue().toString());

        switch (entry.getKey()) {
          case "totalduration":
            this.durationUs = (long)(C.MICROS_PER_SECOND * (Double)(entry.getValue()));
            break;

          case "starttime":
            this.startTime = (long)(C.MICROS_PER_SECOND * (Double)(entry.getValue()));
            break;

          case "videodatarate":
            this.videoDataRate = ((Double)entry.getValue()).floatValue();
            break;

          case "audiodatarate":
            this.audioDataRate = ((Double)entry.getValue()).floatValue();
            break;

          case "framerate":
            this.frameRate = ((Double)entry.getValue()).floatValue();
            break;

          case "width":
            this.width = Math.round(((Double) entry.getValue()).floatValue());
            break;

          case "height":
            this.height = Math.round(((Double) entry.getValue()).floatValue());
            break;

          case "canseekontime":
            this.canSeekOnTime = (boolean) entry.getValue();
            break;

          case "httphostheader":
            this.httpHostHeader = (String) entry.getValue();
            break;

          default:
            break;
        }
      }
    }
  }

  @Override
  protected boolean shouldParsePayload() {
    return true;
  }

  private Object readAMFData(ParsableByteArray data, int type) {
    if (type == METADATA_TYPE_UNKNOWN) {
      type = data.readUnsignedByte();
    }
    byte [] b;
    switch (type) {
      case METADATA_TYPE_NUMBER:
        return readAMFDouble(data);
      case METADATA_TYPE_BOOLEAN:
        return readAMFBoolean(data);
      case METADATA_TYPE_STRING:
        return readAMFString(data);
      case METADATA_TYPE_OBJECT:
        return readAMFObject(data);
      case METADATA_TYPE_ECMA_ARRAY:
        return readAMFEcmaArray(data);
      case METADATA_TYPE_STRICT_ARRAY:
        return readAMFStrictArray(data);
      case METADATA_TYPE_DATE:
        return readAMFDouble(data);
      default:
        return null;
    }
  }

  private Boolean readAMFBoolean(ParsableByteArray data) {
    return Boolean.valueOf(data.readUnsignedByte() == 1);
  }

  private Double readAMFDouble(ParsableByteArray data) {
    byte []b = new byte[8];
    data.readBytes(b, 0, b.length);
    return ByteBuffer.wrap(b).getDouble();
  }

  private String readAMFString(ParsableByteArray data) {
    int size = data.readUnsignedShort();
    byte []b = new byte[size];
    data.readBytes(b, 0, b.length);
    return new String(b);
  }

  private Object readAMFStrictArray(ParsableByteArray data) {
    long count = data.readUnsignedInt();
    ArrayList<Object> list = new ArrayList<Object>();
    for (int i = 0; i < count; i++) {
      list.add(readAMFData(data, METADATA_TYPE_UNKNOWN));
    }
    return list;
  }

  private Object readAMFObject(ParsableByteArray data) {
    HashMap<String, Object> array = new HashMap<String, Object>();
    while (true) {
      String key = readAMFString(data);
      int type = data.readUnsignedByte();
      if (type == 9) { // object end marker
        break;
      }
      array.put(key, readAMFData(data, type));
    }
    return array;
  }

  private Object readAMFEcmaArray(ParsableByteArray data) {
    long count = data.readUnsignedInt();
    HashMap<String, Object> array = new HashMap<String, Object>();
    for (int i = 0; i < count; i++) {
      String key = readAMFString(data);
      int type = data.readUnsignedByte();
      array.put(key, readAMFData(data, type));
    }
    return array;
  }

  private Date readAMFDate(ParsableByteArray data) {
    final Date date = new Date((long) readAMFDouble(data).doubleValue());
    data.readUnsignedShort();
    return date;
  }
}
