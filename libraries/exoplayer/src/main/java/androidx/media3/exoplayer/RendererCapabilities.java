/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.annotation.SuppressLint;
import androidx.annotation.IntDef;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Defines the capabilities of a {@link Renderer}. */
@UnstableApi
public interface RendererCapabilities {

  /** Listener for renderer capabilities events. */
  interface Listener {

    /**
     * Called when the renderer capabilities are changed.
     *
     * <p>This method will be called on the playback thread.
     *
     * @param renderer The renderer that has its capabilities changed.
     */
    void onRendererCapabilitiesChanged(Renderer renderer);
  }

  /**
   * @deprecated Use {@link C.FormatSupport} instead.
   */
  @SuppressWarnings("deprecation")
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    FORMAT_HANDLED,
    FORMAT_EXCEEDS_CAPABILITIES,
    FORMAT_UNSUPPORTED_DRM,
    FORMAT_UNSUPPORTED_SUBTYPE,
    FORMAT_UNSUPPORTED_TYPE
  })
  @Deprecated
  @interface FormatSupport {}

  /** A mask to apply to {@link Capabilities} to obtain the {@link C.FormatSupport} only. */
  int FORMAT_SUPPORT_MASK = 0b111;

  /**
   * @deprecated Use {@link C#FORMAT_HANDLED} instead.
   */
  @Deprecated int FORMAT_HANDLED = C.FORMAT_HANDLED;

  /**
   * @deprecated Use {@link C#FORMAT_EXCEEDS_CAPABILITIES} instead.
   */
  @Deprecated int FORMAT_EXCEEDS_CAPABILITIES = C.FORMAT_EXCEEDS_CAPABILITIES;

  /**
   * @deprecated Use {@link C#FORMAT_UNSUPPORTED_DRM} instead.
   */
  @Deprecated int FORMAT_UNSUPPORTED_DRM = C.FORMAT_UNSUPPORTED_DRM;

  /**
   * @deprecated Use {@link C#FORMAT_UNSUPPORTED_SUBTYPE} instead.
   */
  @Deprecated int FORMAT_UNSUPPORTED_SUBTYPE = C.FORMAT_UNSUPPORTED_SUBTYPE;

  /**
   * @deprecated Use {@link C#FORMAT_UNSUPPORTED_TYPE} instead.
   */
  @Deprecated int FORMAT_UNSUPPORTED_TYPE = C.FORMAT_UNSUPPORTED_TYPE;

  /**
   * Level of renderer support for adaptive format switches. One of {@link #ADAPTIVE_SEAMLESS},
   * {@link #ADAPTIVE_NOT_SEAMLESS} or {@link #ADAPTIVE_NOT_SUPPORTED}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({ADAPTIVE_SEAMLESS, ADAPTIVE_NOT_SEAMLESS, ADAPTIVE_NOT_SUPPORTED})
  @interface AdaptiveSupport {}

  /** A mask to apply to {@link Capabilities} to obtain the {@link AdaptiveSupport} only. */
  int ADAPTIVE_SUPPORT_MASK = 0b11 << 3;

  /** The {@link Renderer} can seamlessly adapt between formats. */
  int ADAPTIVE_SEAMLESS = 0b10 << 3;

  /**
   * The {@link Renderer} can adapt between formats, but may suffer a brief discontinuity
   * (~50-100ms) when adaptation occurs.
   */
  int ADAPTIVE_NOT_SEAMLESS = 0b01 << 3;

  /** The {@link Renderer} does not support adaptation between formats. */
  int ADAPTIVE_NOT_SUPPORTED = 0;

  /**
   * Level of renderer support for tunneling. One of {@link #TUNNELING_SUPPORTED} or {@link
   * #TUNNELING_NOT_SUPPORTED}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({TUNNELING_SUPPORTED, TUNNELING_NOT_SUPPORTED})
  @interface TunnelingSupport {}

  /** A mask to apply to {@link Capabilities} to obtain {@link TunnelingSupport} only. */
  int TUNNELING_SUPPORT_MASK = 0b1 << 5;

  /** The {@link Renderer} supports tunneled output. */
  int TUNNELING_SUPPORTED = 0b1 << 5;

  /** The {@link Renderer} does not support tunneled output. */
  int TUNNELING_NOT_SUPPORTED = 0;

  /**
   * Level of renderer support for hardware acceleration. One of {@link
   * #HARDWARE_ACCELERATION_SUPPORTED} and {@link #HARDWARE_ACCELERATION_NOT_SUPPORTED}.
   *
   * <p>For video renderers, the level of support is indicated for non-tunneled output.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    HARDWARE_ACCELERATION_SUPPORTED,
    HARDWARE_ACCELERATION_NOT_SUPPORTED,
  })
  @interface HardwareAccelerationSupport {}

  /** A mask to apply to {@link Capabilities} to obtain {@link HardwareAccelerationSupport} only. */
  int HARDWARE_ACCELERATION_SUPPORT_MASK = 0b1 << 6;

  /** The renderer is able to use hardware acceleration. */
  int HARDWARE_ACCELERATION_SUPPORTED = 0b1 << 6;

  /** The renderer is not able to use hardware acceleration. */
  int HARDWARE_ACCELERATION_NOT_SUPPORTED = 0;

  /**
   * Level of decoder support. One of {@link #DECODER_SUPPORT_PRIMARY}, {@link
   * #DECODER_SUPPORT_FALLBACK}, and {@link #DECODER_SUPPORT_FALLBACK_MIMETYPE}}.
   *
   * <p>For video renderers, the level of support is indicated for non-tunneled output.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({DECODER_SUPPORT_FALLBACK_MIMETYPE, DECODER_SUPPORT_PRIMARY, DECODER_SUPPORT_FALLBACK})
  @interface DecoderSupport {}

  /** A mask to apply to {@link Capabilities} to obtain {@link DecoderSupport} only. */
  int DECODER_SUPPORT_MASK = 0b11 << 7;

  /**
   * The format's MIME type is unsupported and the renderer may use a decoder for a fallback MIME
   * type.
   */
  int DECODER_SUPPORT_FALLBACK_MIMETYPE = 0b10 << 7;

  /** The renderer is able to use the primary decoder for the format's MIME type. */
  int DECODER_SUPPORT_PRIMARY = 0b1 << 7;

  /** The format exceeds the primary decoder's capabilities but is supported by fallback decoder */
  int DECODER_SUPPORT_FALLBACK = 0;

  /**
   * Level of renderer support for audio offload.
   *
   * <p>Speed change and gapless transition support with audio offload is represented by the bit
   * mask flags {@link #AUDIO_OFFLOAD_SPEED_CHANGE_SUPPORTED} and {@link
   * #AUDIO_OFFLOAD_GAPLESS_SUPPORTED} respectively. If neither feature is supported then the value
   * will be either {@link #AUDIO_OFFLOAD_SUPPORTED} or {@link #AUDIO_OFFLOAD_NOT_SUPPORTED}.
   *
   * <p>For non-audio renderers, the level of support is always {@link
   * #AUDIO_OFFLOAD_NOT_SUPPORTED}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    AUDIO_OFFLOAD_SPEED_CHANGE_SUPPORTED,
    AUDIO_OFFLOAD_GAPLESS_SUPPORTED,
    AUDIO_OFFLOAD_SUPPORTED,
    AUDIO_OFFLOAD_NOT_SUPPORTED
  })
  @interface AudioOffloadSupport {}

  /** A mask to apply to {@link Capabilities} to obtain {@link AudioOffloadSupport} only. */
  int AUDIO_OFFLOAD_SUPPORT_MASK = 0b111 << 9;

  /** The renderer supports audio offload and speed changes with this format. */
  int AUDIO_OFFLOAD_SPEED_CHANGE_SUPPORTED = 0b100 << 9;

  /** The renderer supports audio offload and gapless transitions with this format. */
  int AUDIO_OFFLOAD_GAPLESS_SUPPORTED = 0b10 << 9;

  /** The renderer supports audio offload with this format. */
  int AUDIO_OFFLOAD_SUPPORTED = 0b1 << 9;

  /** Audio offload is not supported with this format. */
  int AUDIO_OFFLOAD_NOT_SUPPORTED = 0;

  /**
   * Combined renderer capabilities.
   *
   * <p>This is a bitwise OR of {@link C.FormatSupport}, {@link AdaptiveSupport}, {@link
   * TunnelingSupport}, {@link HardwareAccelerationSupport}, {@link DecoderSupport} and {@link
   * AudioOffloadSupport}. Use {@link #getFormatSupport}, {@link #getAdaptiveSupport}, {@link
   * #getTunnelingSupport}, {@link #getHardwareAccelerationSupport}, {@link #getDecoderSupport} and
   * {@link AudioOffloadSupport} to obtain individual components. Use {@link #create(int)}, {@link
   * #create(int, int, int)}, {@link #create(int, int, int, int)}, or {@link #create(int, int, int,
   * int, int, int)} to create combined capabilities from individual components.
   *
   * <p>Possible values:
   *
   * <ul>
   *   <li>{@link C.FormatSupport}: The level of support for the format itself. One of {@link
   *       C#FORMAT_HANDLED}, {@link C#FORMAT_EXCEEDS_CAPABILITIES}, {@link
   *       C#FORMAT_UNSUPPORTED_DRM}, {@link C#FORMAT_UNSUPPORTED_SUBTYPE} and {@link
   *       C#FORMAT_UNSUPPORTED_TYPE}.
   *   <li>{@link AdaptiveSupport}: The level of support for adapting from the format to another
   *       format of the same MIME type. One of {@link #ADAPTIVE_SEAMLESS}, {@link
   *       #ADAPTIVE_NOT_SEAMLESS} and {@link #ADAPTIVE_NOT_SUPPORTED}. Only set if the level of
   *       support for the format itself is {@link C#FORMAT_HANDLED} or {@link
   *       C#FORMAT_EXCEEDS_CAPABILITIES}.
   *   <li>{@link TunnelingSupport}: The level of support for tunneling. One of {@link
   *       #TUNNELING_SUPPORTED} and {@link #TUNNELING_NOT_SUPPORTED}. Only set if the level of
   *       support for the format itself is {@link C#FORMAT_HANDLED} or {@link
   *       C#FORMAT_EXCEEDS_CAPABILITIES}.
   *   <li>{@link HardwareAccelerationSupport}: The level of support for hardware acceleration. One
   *       of {@link #HARDWARE_ACCELERATION_SUPPORTED} and {@link
   *       #HARDWARE_ACCELERATION_NOT_SUPPORTED}.
   *   <li>{@link DecoderSupport}: The level of decoder support. One of {@link
   *       #DECODER_SUPPORT_PRIMARY}, {@link #DECODER_SUPPORT_FALLBACK}, or {@link
   *       #DECODER_SUPPORT_FALLBACK_MIMETYPE}.
   *   <li>{@link AudioOffloadSupport}: The level of offload support. Value will have the flag
   *       {@link #AUDIO_OFFLOAD_SUPPORTED} or be {@link #AUDIO_OFFLOAD_NOT_SUPPORTED}. In addition,
   *       if it is {@link #AUDIO_OFFLOAD_SUPPORTED}, then one can check for {@link
   *       #AUDIO_OFFLOAD_SPEED_CHANGE_SUPPORTED} and {@link #AUDIO_OFFLOAD_GAPLESS_SUPPORTED}.
   *       These represent speed change and gapless transition support with audio offload
   *       respectively.
   * </ul>
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  // Intentionally empty to prevent assignment or comparison with individual flags without masking.
  @Target(TYPE_USE)
  @IntDef({})
  @interface Capabilities {}

  /**
   * Returns {@link Capabilities} for the given {@link C.FormatSupport}.
   *
   * <p>{@link AdaptiveSupport} is set to {@link #ADAPTIVE_NOT_SUPPORTED}, {@link TunnelingSupport}
   * is set to {@link #TUNNELING_NOT_SUPPORTED}, {@link HardwareAccelerationSupport} is set to
   * {@link #HARDWARE_ACCELERATION_NOT_SUPPORTED}, {@link DecoderSupport} is set to {@link
   * #DECODER_SUPPORT_PRIMARY} and {@link AudioOffloadSupport} is set to {@link
   * #AUDIO_OFFLOAD_NOT_SUPPORTED}.
   *
   * @param formatSupport The {@link C.FormatSupport}.
   * @return The combined {@link Capabilities} of the given {@link C.FormatSupport}, {@link
   *     #ADAPTIVE_NOT_SUPPORTED}, {@link #TUNNELING_NOT_SUPPORTED} and {@link
   *     #AUDIO_OFFLOAD_NOT_SUPPORTED}.
   */
  static @Capabilities int create(@C.FormatSupport int formatSupport) {
    return create(
        formatSupport,
        ADAPTIVE_NOT_SUPPORTED,
        TUNNELING_NOT_SUPPORTED,
        AUDIO_OFFLOAD_NOT_SUPPORTED);
  }

  /**
   * Returns {@link Capabilities} combining the given {@link C.FormatSupport}, {@link
   * AdaptiveSupport} and {@link TunnelingSupport}.
   *
   * <p>{@link HardwareAccelerationSupport} is set to {@link #HARDWARE_ACCELERATION_NOT_SUPPORTED},
   * {@link DecoderSupport} is set to {@link #DECODER_SUPPORT_PRIMARY}, and {@link
   * AudioOffloadSupport} is set to {@link #AUDIO_OFFLOAD_NOT_SUPPORTED}.
   *
   * @param formatSupport The {@link C.FormatSupport}.
   * @param adaptiveSupport The {@link AdaptiveSupport}.
   * @param tunnelingSupport The {@link TunnelingSupport}.
   * @return The combined {@link Capabilities}.
   */
  static @Capabilities int create(
      @C.FormatSupport int formatSupport,
      @AdaptiveSupport int adaptiveSupport,
      @TunnelingSupport int tunnelingSupport) {
    return create(
        formatSupport,
        adaptiveSupport,
        tunnelingSupport,
        HARDWARE_ACCELERATION_NOT_SUPPORTED,
        DECODER_SUPPORT_PRIMARY,
        AUDIO_OFFLOAD_NOT_SUPPORTED);
  }

  /**
   * Returns {@link Capabilities} combining the given {@link C.FormatSupport}, {@link
   * AdaptiveSupport}, {@link TunnelingSupport}, and {@link AudioOffloadSupport}.
   *
   * <p>{@link HardwareAccelerationSupport} is set to {@link #HARDWARE_ACCELERATION_NOT_SUPPORTED}
   * and {@link DecoderSupport} is set to {@link #DECODER_SUPPORT_PRIMARY}.
   *
   * @param formatSupport The {@link C.FormatSupport}.
   * @param adaptiveSupport The {@link AdaptiveSupport}.
   * @param tunnelingSupport The {@link TunnelingSupport}.
   * @param audioOffloadSupport The {@link AudioOffloadSupport}.
   * @return The combined {@link Capabilities}.
   */
  static @Capabilities int create(
      @C.FormatSupport int formatSupport,
      @AdaptiveSupport int adaptiveSupport,
      @TunnelingSupport int tunnelingSupport,
      @AudioOffloadSupport int audioOffloadSupport) {
    return create(
        formatSupport,
        adaptiveSupport,
        tunnelingSupport,
        HARDWARE_ACCELERATION_NOT_SUPPORTED,
        DECODER_SUPPORT_PRIMARY,
        audioOffloadSupport);
  }

  /**
   * Returns {@link Capabilities} combining the given {@link C.FormatSupport}, {@link
   * AdaptiveSupport}, {@link TunnelingSupport}, {@link HardwareAccelerationSupport}, and {@link
   * DecoderSupport}.
   *
   * <p>{@link AudioOffloadSupport} is set to {@link #AUDIO_OFFLOAD_NOT_SUPPORTED}.
   *
   * @param formatSupport The {@link C.FormatSupport}.
   * @param adaptiveSupport The {@link AdaptiveSupport}.
   * @param tunnelingSupport The {@link TunnelingSupport}.
   * @param hardwareAccelerationSupport The {@link HardwareAccelerationSupport}.
   * @param decoderSupport The {@link DecoderSupport}.
   * @return The combined {@link Capabilities}.
   */
  static @Capabilities int create(
      @C.FormatSupport int formatSupport,
      @AdaptiveSupport int adaptiveSupport,
      @TunnelingSupport int tunnelingSupport,
      @HardwareAccelerationSupport int hardwareAccelerationSupport,
      @DecoderSupport int decoderSupport) {
    return create(
        formatSupport,
        adaptiveSupport,
        tunnelingSupport,
        hardwareAccelerationSupport,
        decoderSupport,
        AUDIO_OFFLOAD_NOT_SUPPORTED);
  }

  /**
   * Returns {@link Capabilities} combining the given {@link C.FormatSupport}, {@link
   * AdaptiveSupport}, {@link TunnelingSupport}, {@link HardwareAccelerationSupport}, {@link
   * DecoderSupport} and {@link AudioOffloadSupport}.
   *
   * @param formatSupport The {@link C.FormatSupport}.
   * @param adaptiveSupport The {@link AdaptiveSupport}.
   * @param tunnelingSupport The {@link TunnelingSupport}.
   * @param hardwareAccelerationSupport The {@link HardwareAccelerationSupport}.
   * @param decoderSupport The {@link DecoderSupport}.
   * @param audioOffloadSupport The {@link AudioOffloadSupport}
   * @return The combined {@link Capabilities}.
   */
  // Suppression needed for IntDef casting.
  @SuppressLint("WrongConstant")
  static @Capabilities int create(
      @C.FormatSupport int formatSupport,
      @AdaptiveSupport int adaptiveSupport,
      @TunnelingSupport int tunnelingSupport,
      @HardwareAccelerationSupport int hardwareAccelerationSupport,
      @DecoderSupport int decoderSupport,
      @AudioOffloadSupport int audioOffloadSupport) {
    return formatSupport
        | adaptiveSupport
        | tunnelingSupport
        | hardwareAccelerationSupport
        | decoderSupport
        | audioOffloadSupport;
  }

  /**
   * Returns the {@link C.FormatSupport} from the combined {@link Capabilities}.
   *
   * @param supportFlags The combined {@link Capabilities}.
   * @return The {@link C.FormatSupport} only.
   */
  // Suppression needed for IntDef casting.
  @SuppressLint("WrongConstant")
  static @C.FormatSupport int getFormatSupport(@Capabilities int supportFlags) {
    return supportFlags & FORMAT_SUPPORT_MASK;
  }

  /**
   * Returns the {@link AdaptiveSupport} from the combined {@link Capabilities}.
   *
   * @param supportFlags The combined {@link Capabilities}.
   * @return The {@link AdaptiveSupport} only.
   */
  // Suppression needed for IntDef casting.
  @SuppressLint("WrongConstant")
  static @AdaptiveSupport int getAdaptiveSupport(@Capabilities int supportFlags) {
    return supportFlags & ADAPTIVE_SUPPORT_MASK;
  }

  /**
   * Returns the {@link TunnelingSupport} from the combined {@link Capabilities}.
   *
   * @param supportFlags The combined {@link Capabilities}.
   * @return The {@link TunnelingSupport} only.
   */
  // Suppression needed for IntDef casting.
  @SuppressLint("WrongConstant")
  static @TunnelingSupport int getTunnelingSupport(@Capabilities int supportFlags) {
    return supportFlags & TUNNELING_SUPPORT_MASK;
  }

  /**
   * Returns the {@link HardwareAccelerationSupport} from the combined {@link Capabilities}.
   *
   * @param supportFlags The combined {@link Capabilities}.
   * @return The {@link HardwareAccelerationSupport} only.
   */
  // Suppression needed for IntDef casting.
  @SuppressLint("WrongConstant")
  static @HardwareAccelerationSupport int getHardwareAccelerationSupport(
      @Capabilities int supportFlags) {
    return supportFlags & HARDWARE_ACCELERATION_SUPPORT_MASK;
  }

  /**
   * Returns the {@link DecoderSupport} from the combined {@link Capabilities}.
   *
   * @param supportFlags The combined {@link Capabilities}.
   * @return The {@link DecoderSupport} only.
   */
  // Suppression needed for IntDef casting.
  @SuppressLint("WrongConstant")
  static @DecoderSupport int getDecoderSupport(@Capabilities int supportFlags) {
    return supportFlags & DECODER_SUPPORT_MASK;
  }

  /**
   * Returns the {@link AudioOffloadSupport} from the combined {@link Capabilities}.
   *
   * @param supportFlags The combined {@link Capabilities}.
   * @return The {@link AudioOffloadSupport} only.
   */
  // Suppression needed for IntDef casting.
  @SuppressLint("WrongConstant")
  static @AudioOffloadSupport int getAudioOffloadSupport(@Capabilities int supportFlags) {
    return supportFlags & AUDIO_OFFLOAD_SUPPORT_MASK;
  }

  /** Returns the name of the {@link Renderer}. */
  String getName();

  /**
   * Returns the track type that the {@link Renderer} handles. For example, a video renderer will
   * return {@link C#TRACK_TYPE_VIDEO}, an audio renderer will return {@link C#TRACK_TYPE_AUDIO}, a
   * text renderer will return {@link C#TRACK_TYPE_TEXT}, and so on.
   *
   * @see Renderer#getTrackType()
   * @return The {@link C.TrackType track type}.
   */
  @C.TrackType
  int getTrackType();

  /**
   * Returns the extent to which the {@link Renderer} supports a given format.
   *
   * @param format The format.
   * @return The {@link Capabilities} for this format.
   * @throws ExoPlaybackException If an error occurs.
   */
  @Capabilities
  int supportsFormat(Format format) throws ExoPlaybackException;

  /**
   * Returns the extent to which the {@link Renderer} supports adapting between supported formats
   * that have different MIME types.
   *
   * @return The {@link AdaptiveSupport} for adapting between supported formats that have different
   *     MIME types.
   * @throws ExoPlaybackException If an error occurs.
   */
  @AdaptiveSupport
  int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException;

  /**
   * Sets the {@link Listener}.
   *
   * @param listener The listener to be set.
   */
  default void setListener(Listener listener) {
    // Do nothing.
  }

  /** Clears the {@link Listener}. */
  default void clearListener() {
    // Do nothing.
  }
}
