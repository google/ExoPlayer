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
package com.google.android.exoplayer2;

import android.annotation.SuppressLint;
import androidx.annotation.IntDef;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Defines the capabilities of a {@link Renderer}. */
public interface RendererCapabilities {

  /** @deprecated Use {@link C.FormatSupport} instead. */
  @SuppressWarnings("Deprecation")
  @Documented
  @Retention(RetentionPolicy.SOURCE)
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
  /** @deprecated Use {@link C#FORMAT_HANDLED} instead. */
  @Deprecated int FORMAT_HANDLED = C.FORMAT_HANDLED;
  /** @deprecated Use {@link C#FORMAT_EXCEEDS_CAPABILITIES} instead. */
  @Deprecated int FORMAT_EXCEEDS_CAPABILITIES = C.FORMAT_EXCEEDS_CAPABILITIES;
  /** @deprecated Use {@link C#FORMAT_UNSUPPORTED_DRM} instead. */
  @Deprecated int FORMAT_UNSUPPORTED_DRM = C.FORMAT_UNSUPPORTED_DRM;
  /** @deprecated Use {@link C#FORMAT_UNSUPPORTED_SUBTYPE} instead. */
  @Deprecated int FORMAT_UNSUPPORTED_SUBTYPE = C.FORMAT_UNSUPPORTED_SUBTYPE;
  /** @deprecated Use {@link C#FORMAT_UNSUPPORTED_TYPE} instead. */
  @Deprecated int FORMAT_UNSUPPORTED_TYPE = C.FORMAT_UNSUPPORTED_TYPE;

  /**
   * Level of renderer support for adaptive format switches. One of {@link #ADAPTIVE_SEAMLESS},
   * {@link #ADAPTIVE_NOT_SEAMLESS} or {@link #ADAPTIVE_NOT_SUPPORTED}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({ADAPTIVE_SEAMLESS, ADAPTIVE_NOT_SEAMLESS, ADAPTIVE_NOT_SUPPORTED})
  @interface AdaptiveSupport {}

  /** A mask to apply to {@link Capabilities} to obtain the {@link AdaptiveSupport} only. */
  int ADAPTIVE_SUPPORT_MASK = 0b11000;
  /**
   * The {@link Renderer} can seamlessly adapt between formats.
   */
  int ADAPTIVE_SEAMLESS = 0b10000;
  /**
   * The {@link Renderer} can adapt between formats, but may suffer a brief discontinuity
   * (~50-100ms) when adaptation occurs.
   */
  int ADAPTIVE_NOT_SEAMLESS = 0b01000;
  /**
   * The {@link Renderer} does not support adaptation between formats.
   */
  int ADAPTIVE_NOT_SUPPORTED = 0b00000;

  /**
   * Level of renderer support for tunneling. One of {@link #TUNNELING_SUPPORTED} or {@link
   * #TUNNELING_NOT_SUPPORTED}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TUNNELING_SUPPORTED, TUNNELING_NOT_SUPPORTED})
  @interface TunnelingSupport {}

  /** A mask to apply to {@link Capabilities} to obtain the {@link TunnelingSupport} only. */
  int TUNNELING_SUPPORT_MASK = 0b100000;
  /**
   * The {@link Renderer} supports tunneled output.
   */
  int TUNNELING_SUPPORTED = 0b100000;
  /**
   * The {@link Renderer} does not support tunneled output.
   */
  int TUNNELING_NOT_SUPPORTED = 0b000000;

  /**
   * Combined renderer capabilities.
   *
   * <p>This is a bitwise OR of {@link C.FormatSupport}, {@link AdaptiveSupport} and {@link
   * TunnelingSupport}. Use {@link #getFormatSupport(int)}, {@link #getAdaptiveSupport(int)} or
   * {@link #getTunnelingSupport(int)} to obtain the individual flags. And use {@link #create(int)}
   * or {@link #create(int, int, int)} to create the combined capabilities.
   *
   * <p>Possible values:
   *
   * <ul>
   *   <li>{@link C.FormatSupport}: The level of support for the format itself. One of {@link
   *       C#FORMAT_HANDLED}, {@link C#FORMAT_EXCEEDS_CAPABILITIES}, {@link
   *       C#FORMAT_UNSUPPORTED_DRM}, {@link C#FORMAT_UNSUPPORTED_SUBTYPE} and {@link
   *       C#FORMAT_UNSUPPORTED_TYPE}.
   *   <li>{@link AdaptiveSupport}: The level of support for adapting from the format to another
   *       format of the same mime type. One of {@link #ADAPTIVE_SEAMLESS}, {@link
   *       #ADAPTIVE_NOT_SEAMLESS} and {@link #ADAPTIVE_NOT_SUPPORTED}. Only set if the level of
   *       support for the format itself is {@link C#FORMAT_HANDLED} or {@link
   *       C#FORMAT_EXCEEDS_CAPABILITIES}.
   *   <li>{@link TunnelingSupport}: The level of support for tunneling. One of {@link
   *       #TUNNELING_SUPPORTED} and {@link #TUNNELING_NOT_SUPPORTED}. Only set if the level of
   *       support for the format itself is {@link C#FORMAT_HANDLED} or {@link
   *       C#FORMAT_EXCEEDS_CAPABILITIES}.
   * </ul>
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  // Intentionally empty to prevent assignment or comparison with individual flags without masking.
  @IntDef({})
  @interface Capabilities {}

  /**
   * Returns {@link Capabilities} for the given {@link C.FormatSupport}.
   *
   * <p>The {@link AdaptiveSupport} is set to {@link #ADAPTIVE_NOT_SUPPORTED} and {{@link
   * TunnelingSupport} is set to {@link #TUNNELING_NOT_SUPPORTED}.
   *
   * @param formatSupport The {@link C.FormatSupport}.
   * @return The combined {@link Capabilities} of the given {@link C.FormatSupport}, {@link
   *     #ADAPTIVE_NOT_SUPPORTED} and {@link #TUNNELING_NOT_SUPPORTED}.
   */
  @Capabilities
  static int create(@C.FormatSupport int formatSupport) {
    return create(formatSupport, ADAPTIVE_NOT_SUPPORTED, TUNNELING_NOT_SUPPORTED);
  }

  /**
   * Returns {@link Capabilities} combining the given {@link C.FormatSupport}, {@link
   * AdaptiveSupport} and {@link TunnelingSupport}.
   *
   * @param formatSupport The {@link C.FormatSupport}.
   * @param adaptiveSupport The {@link AdaptiveSupport}.
   * @param tunnelingSupport The {@link TunnelingSupport}.
   * @return The combined {@link Capabilities}.
   */
  // Suppression needed for IntDef casting.
  @SuppressLint("WrongConstant")
  @Capabilities
  static int create(
      @C.FormatSupport int formatSupport,
      @AdaptiveSupport int adaptiveSupport,
      @TunnelingSupport int tunnelingSupport) {
    return formatSupport | adaptiveSupport | tunnelingSupport;
  }

  /**
   * Returns the {@link C.FormatSupport} from the combined {@link Capabilities}.
   *
   * @param supportFlags The combined {@link Capabilities}.
   * @return The {@link C.FormatSupport} only.
   */
  // Suppression needed for IntDef casting.
  @SuppressLint("WrongConstant")
  @C.FormatSupport
  static int getFormatSupport(@Capabilities int supportFlags) {
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
  @AdaptiveSupport
  static int getAdaptiveSupport(@Capabilities int supportFlags) {
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
  @TunnelingSupport
  static int getTunnelingSupport(@Capabilities int supportFlags) {
    return supportFlags & TUNNELING_SUPPORT_MASK;
  }

  /** Returns the name of the {@link Renderer}. */
  String getName();

  /**
   * Returns the track type that the {@link Renderer} handles. For example, a video renderer will
   * return {@link C#TRACK_TYPE_VIDEO}, an audio renderer will return {@link C#TRACK_TYPE_AUDIO}, a
   * text renderer will return {@link C#TRACK_TYPE_TEXT}, and so on.
   *
   * @see Renderer#getTrackType()
   * @return One of the {@code TRACK_TYPE_*} constants defined in {@link C}.
   */
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
}
