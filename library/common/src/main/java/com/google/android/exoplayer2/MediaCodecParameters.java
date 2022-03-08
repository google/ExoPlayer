package com.google.android.exoplayer2;

import android.os.Build;
import androidx.annotation.Nullable;
import java.util.HashMap;

/**
 * MediaCodec parameters configuring the {@link android.media.MediaCodec} instances.
 *
 * once instantiated, the application can add key-value pairs calling {@link MediaCodecParameters#set}
 * and send a message of type {@code Renderer#MSG_SET_CODEC_PARAMETERS} to the renderers.
 *
 */
public class MediaCodecParameters {

  public static final String KEY_DIALOG_ENHANCEMENT = "dialog-enhancement-gain";
  public static final String DIALOG_ENHANCEMENT_OFF = "Off";
  public static final String DIALOG_ENHANCEMENT_LEVEL_LOW = "Low";
  public static final String DIALOG_ENHANCEMENT_LEVEL_MID = "Mid";
  public static final String DIALOG_ENHANCEMENT_LEVEL_HIGH = "High";

  private HashMap<String, Object> mediaCodecParameters;

  public MediaCodecParameters() {
    mediaCodecParameters = new HashMap<String, Object>();
  }

  public void set(HashMap<String, Object> parameters) {
    mediaCodecParameters.putAll(parameters);
  }
  public void set(String key, Object value) {
    mediaCodecParameters.put(key, value);
  }
  public HashMap<String, Object> get() { return mediaCodecParameters; }

  public Object getOrDefault(String key, Object def) {
    if (Build.VERSION.SDK_INT >= 24) {
      return mediaCodecParameters.getOrDefault(key, def);
    } else {
      @Nullable Object o = mediaCodecParameters.get(key);
      return (o == null)  ? def : o;
    }
  }
}
