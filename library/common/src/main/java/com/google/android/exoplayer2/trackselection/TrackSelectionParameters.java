/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.trackselection;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.Context;
import android.graphics.Point;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.accessibility.CaptioningManager;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Locale;

/** Constraint parameters for track selection. */
public class TrackSelectionParameters implements Parcelable {

  /**
   * A builder for {@link TrackSelectionParameters}. See the {@link TrackSelectionParameters}
   * documentation for explanations of the parameters that can be configured using this builder.
   */
  public static class Builder {
    // Video
    private int maxVideoWidth;
    private int maxVideoHeight;
    private int maxVideoFrameRate;
    private int maxVideoBitrate;
    private int minVideoWidth;
    private int minVideoHeight;
    private int minVideoFrameRate;
    private int minVideoBitrate;
    private int viewportWidth;
    private int viewportHeight;
    private boolean viewportOrientationMayChange;
    private ImmutableList<String> preferredVideoMimeTypes;
    // Audio
    private ImmutableList<String> preferredAudioLanguages;
    @C.RoleFlags private int preferredAudioRoleFlags;
    private int maxAudioChannelCount;
    private int maxAudioBitrate;
    private ImmutableList<String> preferredAudioMimeTypes;
    // Text
    private ImmutableList<String> preferredTextLanguages;
    @C.RoleFlags private int preferredTextRoleFlags;
    private boolean selectUndeterminedTextLanguage;
    // General
    private boolean forceLowestBitrate;
    private boolean forceHighestSupportedBitrate;

    /**
     * @deprecated {@link Context} constraints will not be set using this constructor. Use {@link
     *     #Builder(Context)} instead.
     */
    @Deprecated
    public Builder() {
      // Video
      maxVideoWidth = Integer.MAX_VALUE;
      maxVideoHeight = Integer.MAX_VALUE;
      maxVideoFrameRate = Integer.MAX_VALUE;
      maxVideoBitrate = Integer.MAX_VALUE;
      viewportWidth = Integer.MAX_VALUE;
      viewportHeight = Integer.MAX_VALUE;
      viewportOrientationMayChange = true;
      preferredVideoMimeTypes = ImmutableList.of();
      // Audio
      preferredAudioLanguages = ImmutableList.of();
      preferredAudioRoleFlags = 0;
      maxAudioChannelCount = Integer.MAX_VALUE;
      maxAudioBitrate = Integer.MAX_VALUE;
      preferredAudioMimeTypes = ImmutableList.of();
      // Text
      preferredTextLanguages = ImmutableList.of();
      preferredTextRoleFlags = 0;
      selectUndeterminedTextLanguage = false;
      // General
      forceLowestBitrate = false;
      forceHighestSupportedBitrate = false;
    }

    /**
     * Creates a builder with default initial values.
     *
     * @param context Any context.
     */
    @SuppressWarnings({"deprecation", "method.invocation"}) // Methods invoked are setter only.
    public Builder(Context context) {
      this();
      setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(context);
      setViewportSizeToPhysicalDisplaySize(context, /* viewportOrientationMayChange= */ true);
    }

    /**
     * @param initialValues The {@link TrackSelectionParameters} from which the initial values of
     *     the builder are obtained.
     */
    protected Builder(TrackSelectionParameters initialValues) {
      // Video
      maxVideoWidth = initialValues.maxVideoWidth;
      maxVideoHeight = initialValues.maxVideoHeight;
      maxVideoFrameRate = initialValues.maxVideoFrameRate;
      maxVideoBitrate = initialValues.maxVideoBitrate;
      minVideoWidth = initialValues.minVideoWidth;
      minVideoHeight = initialValues.minVideoHeight;
      minVideoFrameRate = initialValues.minVideoFrameRate;
      minVideoBitrate = initialValues.minVideoBitrate;
      viewportWidth = initialValues.viewportWidth;
      viewportHeight = initialValues.viewportHeight;
      viewportOrientationMayChange = initialValues.viewportOrientationMayChange;
      preferredVideoMimeTypes = initialValues.preferredVideoMimeTypes;
      // Audio
      preferredAudioLanguages = initialValues.preferredAudioLanguages;
      preferredAudioRoleFlags = initialValues.preferredAudioRoleFlags;
      maxAudioChannelCount = initialValues.maxAudioChannelCount;
      maxAudioBitrate = initialValues.maxAudioBitrate;
      preferredAudioMimeTypes = initialValues.preferredAudioMimeTypes;
      // Text
      preferredTextLanguages = initialValues.preferredTextLanguages;
      preferredTextRoleFlags = initialValues.preferredTextRoleFlags;
      selectUndeterminedTextLanguage = initialValues.selectUndeterminedTextLanguage;
      // General
      forceLowestBitrate = initialValues.forceLowestBitrate;
      forceHighestSupportedBitrate = initialValues.forceHighestSupportedBitrate;
    }

    // Video

    /**
     * Equivalent to {@link #setMaxVideoSize setMaxVideoSize(1279, 719)}.
     *
     * @return This builder.
     */
    public Builder setMaxVideoSizeSd() {
      return setMaxVideoSize(1279, 719);
    }

    /**
     * Equivalent to {@link #setMaxVideoSize setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)}.
     *
     * @return This builder.
     */
    public Builder clearVideoSizeConstraints() {
      return setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Sets the maximum allowed video width and height.
     *
     * @param maxVideoWidth Maximum allowed video width in pixels.
     * @param maxVideoHeight Maximum allowed video height in pixels.
     * @return This builder.
     */
    public Builder setMaxVideoSize(int maxVideoWidth, int maxVideoHeight) {
      this.maxVideoWidth = maxVideoWidth;
      this.maxVideoHeight = maxVideoHeight;
      return this;
    }

    /**
     * Sets the maximum allowed video frame rate.
     *
     * @param maxVideoFrameRate Maximum allowed video frame rate in hertz.
     * @return This builder.
     */
    public Builder setMaxVideoFrameRate(int maxVideoFrameRate) {
      this.maxVideoFrameRate = maxVideoFrameRate;
      return this;
    }

    /**
     * Sets the maximum allowed video bitrate.
     *
     * @param maxVideoBitrate Maximum allowed video bitrate in bits per second.
     * @return This builder.
     */
    public Builder setMaxVideoBitrate(int maxVideoBitrate) {
      this.maxVideoBitrate = maxVideoBitrate;
      return this;
    }

    /**
     * Sets the minimum allowed video width and height.
     *
     * @param minVideoWidth Minimum allowed video width in pixels.
     * @param minVideoHeight Minimum allowed video height in pixels.
     * @return This builder.
     */
    public Builder setMinVideoSize(int minVideoWidth, int minVideoHeight) {
      this.minVideoWidth = minVideoWidth;
      this.minVideoHeight = minVideoHeight;
      return this;
    }

    /**
     * Sets the minimum allowed video frame rate.
     *
     * @param minVideoFrameRate Minimum allowed video frame rate in hertz.
     * @return This builder.
     */
    public Builder setMinVideoFrameRate(int minVideoFrameRate) {
      this.minVideoFrameRate = minVideoFrameRate;
      return this;
    }

    /**
     * Sets the minimum allowed video bitrate.
     *
     * @param minVideoBitrate Minimum allowed video bitrate in bits per second.
     * @return This builder.
     */
    public Builder setMinVideoBitrate(int minVideoBitrate) {
      this.minVideoBitrate = minVideoBitrate;
      return this;
    }

    /**
     * Equivalent to calling {@link #setViewportSize(int, int, boolean)} with the viewport size
     * obtained from {@link Util#getCurrentDisplayModeSize(Context)}.
     *
     * @param context Any context.
     * @param viewportOrientationMayChange Whether the viewport orientation may change during
     *     playback.
     * @return This builder.
     */
    public Builder setViewportSizeToPhysicalDisplaySize(
        Context context, boolean viewportOrientationMayChange) {
      // Assume the viewport is fullscreen.
      Point viewportSize = Util.getCurrentDisplayModeSize(context);
      return setViewportSize(viewportSize.x, viewportSize.y, viewportOrientationMayChange);
    }

    /**
     * Equivalent to {@link #setViewportSize setViewportSize(Integer.MAX_VALUE, Integer.MAX_VALUE,
     * true)}.
     *
     * @return This builder.
     */
    public Builder clearViewportSizeConstraints() {
      return setViewportSize(Integer.MAX_VALUE, Integer.MAX_VALUE, true);
    }

    /**
     * Sets the viewport size to constrain adaptive video selections so that only tracks suitable
     * for the viewport are selected.
     *
     * @param viewportWidth Viewport width in pixels.
     * @param viewportHeight Viewport height in pixels.
     * @param viewportOrientationMayChange Whether the viewport orientation may change during
     *     playback.
     * @return This builder.
     */
    public Builder setViewportSize(
        int viewportWidth, int viewportHeight, boolean viewportOrientationMayChange) {
      this.viewportWidth = viewportWidth;
      this.viewportHeight = viewportHeight;
      this.viewportOrientationMayChange = viewportOrientationMayChange;
      return this;
    }

    /**
     * Sets the preferred sample MIME type for video tracks.
     *
     * @param mimeType The preferred MIME type for video tracks, or {@code null} to clear a
     *     previously set preference.
     * @return This builder.
     */
    public Builder setPreferredVideoMimeType(@Nullable String mimeType) {
      return mimeType == null ? setPreferredVideoMimeTypes() : setPreferredVideoMimeTypes(mimeType);
    }

    /**
     * Sets the preferred sample MIME types for video tracks.
     *
     * @param mimeTypes The preferred MIME types for video tracks in order of preference, or an
     *     empty list for no preference.
     * @return This builder.
     */
    public Builder setPreferredVideoMimeTypes(String... mimeTypes) {
      preferredVideoMimeTypes = ImmutableList.copyOf(mimeTypes);
      return this;
    }

    // Audio

    /**
     * Sets the preferred language for audio and forced text tracks.
     *
     * @param preferredAudioLanguage Preferred audio language as an IETF BCP 47 conformant tag, or
     *     {@code null} to select the default track, or the first track if there's no default.
     * @return This builder.
     */
    public Builder setPreferredAudioLanguage(@Nullable String preferredAudioLanguage) {
      return preferredAudioLanguage == null
          ? setPreferredAudioLanguages()
          : setPreferredAudioLanguages(preferredAudioLanguage);
    }

    /**
     * Sets the preferred languages for audio and forced text tracks.
     *
     * @param preferredAudioLanguages Preferred audio languages as IETF BCP 47 conformant tags in
     *     order of preference, or an empty array to select the default track, or the first track if
     *     there's no default.
     * @return This builder.
     */
    public Builder setPreferredAudioLanguages(String... preferredAudioLanguages) {
      ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
      for (String language : checkNotNull(preferredAudioLanguages)) {
        listBuilder.add(Util.normalizeLanguageCode(checkNotNull(language)));
      }
      this.preferredAudioLanguages = listBuilder.build();
      return this;
    }

    /**
     * Sets the preferred {@link C.RoleFlags} for audio tracks.
     *
     * @param preferredAudioRoleFlags Preferred audio role flags.
     * @return This builder.
     */
    public Builder setPreferredAudioRoleFlags(@C.RoleFlags int preferredAudioRoleFlags) {
      this.preferredAudioRoleFlags = preferredAudioRoleFlags;
      return this;
    }

    /**
     * Sets the maximum allowed audio channel count.
     *
     * @param maxAudioChannelCount Maximum allowed audio channel count.
     * @return This builder.
     */
    public Builder setMaxAudioChannelCount(int maxAudioChannelCount) {
      this.maxAudioChannelCount = maxAudioChannelCount;
      return this;
    }

    /**
     * Sets the maximum allowed audio bitrate.
     *
     * @param maxAudioBitrate Maximum allowed audio bitrate in bits per second.
     * @return This builder.
     */
    public Builder setMaxAudioBitrate(int maxAudioBitrate) {
      this.maxAudioBitrate = maxAudioBitrate;
      return this;
    }

    /**
     * Sets the preferred sample MIME type for audio tracks.
     *
     * @param mimeType The preferred MIME type for audio tracks, or {@code null} to clear a
     *     previously set preference.
     * @return This builder.
     */
    public Builder setPreferredAudioMimeType(@Nullable String mimeType) {
      return mimeType == null ? setPreferredAudioMimeTypes() : setPreferredAudioMimeTypes(mimeType);
    }

    /**
     * Sets the preferred sample MIME types for audio tracks.
     *
     * @param mimeTypes The preferred MIME types for audio tracks in order of preference, or an
     *     empty list for no preference.
     * @return This builder.
     */
    public Builder setPreferredAudioMimeTypes(String... mimeTypes) {
      preferredAudioMimeTypes = ImmutableList.copyOf(mimeTypes);
      return this;
    }

    // Text

    /**
     * Sets the preferred language and role flags for text tracks based on the accessibility
     * settings of {@link CaptioningManager}.
     *
     * <p>Does nothing for API levels &lt; 19 or when the {@link CaptioningManager} is disabled.
     *
     * @param context A {@link Context}.
     * @return This builder.
     */
    public Builder setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(
        Context context) {
      if (Util.SDK_INT >= 19) {
        setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettingsV19(context);
      }
      return this;
    }

    /**
     * Sets the preferred language for text tracks.
     *
     * @param preferredTextLanguage Preferred text language as an IETF BCP 47 conformant tag, or
     *     {@code null} to select the default track if there is one, or no track otherwise.
     * @return This builder.
     */
    public Builder setPreferredTextLanguage(@Nullable String preferredTextLanguage) {
      return preferredTextLanguage == null
          ? setPreferredTextLanguages()
          : setPreferredTextLanguages(preferredTextLanguage);
    }

    /**
     * Sets the preferred languages for text tracks.
     *
     * @param preferredTextLanguages Preferred text languages as IETF BCP 47 conformant tags in
     *     order of preference, or an empty array to select the default track if there is one, or no
     *     track otherwise.
     * @return This builder.
     */
    public Builder setPreferredTextLanguages(String... preferredTextLanguages) {
      ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
      for (String language : checkNotNull(preferredTextLanguages)) {
        listBuilder.add(Util.normalizeLanguageCode(checkNotNull(language)));
      }
      this.preferredTextLanguages = listBuilder.build();
      return this;
    }

    /**
     * Sets the preferred {@link C.RoleFlags} for text tracks.
     *
     * @param preferredTextRoleFlags Preferred text role flags.
     * @return This builder.
     */
    public Builder setPreferredTextRoleFlags(@C.RoleFlags int preferredTextRoleFlags) {
      this.preferredTextRoleFlags = preferredTextRoleFlags;
      return this;
    }

    /**
     * Sets whether a text track with undetermined language should be selected if no track with
     * {@link #setPreferredTextLanguages(String...) a preferred language} is available, or if the
     * preferred language is unset.
     *
     * @param selectUndeterminedTextLanguage Whether a text track with undetermined language should
     *     be selected if no preferred language track is available.
     * @return This builder.
     */
    public Builder setSelectUndeterminedTextLanguage(boolean selectUndeterminedTextLanguage) {
      this.selectUndeterminedTextLanguage = selectUndeterminedTextLanguage;
      return this;
    }

    // General

    /**
     * Sets whether to force selection of the single lowest bitrate audio and video tracks that
     * comply with all other constraints.
     *
     * @param forceLowestBitrate Whether to force selection of the single lowest bitrate audio and
     *     video tracks.
     * @return This builder.
     */
    public Builder setForceLowestBitrate(boolean forceLowestBitrate) {
      this.forceLowestBitrate = forceLowestBitrate;
      return this;
    }

    /**
     * Sets whether to force selection of the highest bitrate audio and video tracks that comply
     * with all other constraints.
     *
     * @param forceHighestSupportedBitrate Whether to force selection of the highest bitrate audio
     *     and video tracks.
     * @return This builder.
     */
    public Builder setForceHighestSupportedBitrate(boolean forceHighestSupportedBitrate) {
      this.forceHighestSupportedBitrate = forceHighestSupportedBitrate;
      return this;
    }

    /** Builds a {@link TrackSelectionParameters} instance with the selected values. */
    public TrackSelectionParameters build() {
      return new TrackSelectionParameters(this);
    }

    @RequiresApi(19)
    private void setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettingsV19(
        Context context) {
      if (Util.SDK_INT < 23 && Looper.myLooper() == null) {
        // Android platform bug (pre-Marshmallow) that causes RuntimeExceptions when
        // CaptioningService is instantiated from a non-Looper thread. See [internal: b/143779904].
        return;
      }
      CaptioningManager captioningManager =
          (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
      if (captioningManager == null || !captioningManager.isEnabled()) {
        return;
      }
      preferredTextRoleFlags = C.ROLE_FLAG_CAPTION | C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND;
      Locale preferredLocale = captioningManager.getLocale();
      if (preferredLocale != null) {
        preferredTextLanguages = ImmutableList.of(Util.getLocaleLanguageTag(preferredLocale));
      }
    }
  }

  /**
   * An instance with default values, except those obtained from the {@link Context}.
   *
   * <p>If possible, use {@link #getDefaults(Context)} instead.
   *
   * <p>This instance will not have the following settings:
   *
   * <ul>
   *   <li>{@link Builder#setViewportSizeToPhysicalDisplaySize(Context, boolean) Viewport
   *       constraints} configured for the primary display.
   *   <li>{@link Builder#setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(Context)
   *       Preferred text language and role flags} configured to the accessibility settings of
   *       {@link CaptioningManager}.
   * </ul>
   */
  @SuppressWarnings("deprecation")
  public static final TrackSelectionParameters DEFAULT_WITHOUT_CONTEXT = new Builder().build();
  /**
   * @deprecated This instance is not configured using {@link Context} constraints. Use {@link
   *     #getDefaults(Context)} instead.
   */
  @Deprecated public static final TrackSelectionParameters DEFAULT = DEFAULT_WITHOUT_CONTEXT;

  public static final Creator<TrackSelectionParameters> CREATOR =
      new Creator<TrackSelectionParameters>() {

        @Override
        public TrackSelectionParameters createFromParcel(Parcel in) {
          return new TrackSelectionParameters(in);
        }

        @Override
        public TrackSelectionParameters[] newArray(int size) {
          return new TrackSelectionParameters[size];
        }
      };

  /** Returns an instance configured with default values. */
  public static TrackSelectionParameters getDefaults(Context context) {
    return new Builder(context).build();
  }

  // Video
  /**
   * Maximum allowed video width in pixels. The default value is {@link Integer#MAX_VALUE} (i.e. no
   * constraint).
   *
   * <p>To constrain adaptive video track selections to be suitable for a given viewport (the region
   * of the display within which video will be played), use ({@link #viewportWidth}, {@link
   * #viewportHeight} and {@link #viewportOrientationMayChange}) instead.
   */
  public final int maxVideoWidth;
  /**
   * Maximum allowed video height in pixels. The default value is {@link Integer#MAX_VALUE} (i.e. no
   * constraint).
   *
   * <p>To constrain adaptive video track selections to be suitable for a given viewport (the region
   * of the display within which video will be played), use ({@link #viewportWidth}, {@link
   * #viewportHeight} and {@link #viewportOrientationMayChange}) instead.
   */
  public final int maxVideoHeight;
  /**
   * Maximum allowed video frame rate in hertz. The default value is {@link Integer#MAX_VALUE} (i.e.
   * no constraint).
   */
  public final int maxVideoFrameRate;
  /**
   * Maximum allowed video bitrate in bits per second. The default value is {@link
   * Integer#MAX_VALUE} (i.e. no constraint).
   */
  public final int maxVideoBitrate;
  /** Minimum allowed video width in pixels. The default value is 0 (i.e. no constraint). */
  public final int minVideoWidth;
  /** Minimum allowed video height in pixels. The default value is 0 (i.e. no constraint). */
  public final int minVideoHeight;
  /** Minimum allowed video frame rate in hertz. The default value is 0 (i.e. no constraint). */
  public final int minVideoFrameRate;
  /**
   * Minimum allowed video bitrate in bits per second. The default value is 0 (i.e. no constraint).
   */
  public final int minVideoBitrate;
  /**
   * Viewport width in pixels. Constrains video track selections for adaptive content so that only
   * tracks suitable for the viewport are selected. The default value is the physical width of the
   * primary display, in pixels.
   */
  public final int viewportWidth;
  /**
   * Viewport height in pixels. Constrains video track selections for adaptive content so that only
   * tracks suitable for the viewport are selected. The default value is the physical height of the
   * primary display, in pixels.
   */
  public final int viewportHeight;
  /**
   * Whether the viewport orientation may change during playback. Constrains video track selections
   * for adaptive content so that only tracks suitable for the viewport are selected. The default
   * value is {@code true}.
   */
  public final boolean viewportOrientationMayChange;
  /**
   * The preferred sample MIME types for video tracks in order of preference, or an empty list for
   * no preference. The default is an empty list.
   */
  public final ImmutableList<String> preferredVideoMimeTypes;
  // Audio
  /**
   * The preferred languages for audio and forced text tracks as IETF BCP 47 conformant tags in
   * order of preference. An empty list selects the default track, or the first track if there's no
   * default. The default value is an empty list.
   */
  public final ImmutableList<String> preferredAudioLanguages;
  /**
   * The preferred {@link C.RoleFlags} for audio tracks. {@code 0} selects the default track if
   * there is one, or the first track if there's no default. The default value is {@code 0}.
   */
  @C.RoleFlags public final int preferredAudioRoleFlags;
  /**
   * Maximum allowed audio channel count. The default value is {@link Integer#MAX_VALUE} (i.e. no
   * constraint).
   */
  public final int maxAudioChannelCount;
  /**
   * Maximum allowed audio bitrate in bits per second. The default value is {@link
   * Integer#MAX_VALUE} (i.e. no constraint).
   */
  public final int maxAudioBitrate;
  /**
   * The preferred sample MIME types for audio tracks in order of preference, or an empty list for
   * no preference. The default is an empty list.
   */
  public final ImmutableList<String> preferredAudioMimeTypes;
  // Text
  /**
   * The preferred languages for text tracks as IETF BCP 47 conformant tags in order of preference.
   * An empty list selects the default track if there is one, or no track otherwise. The default
   * value is an empty list, or the language of the accessibility {@link CaptioningManager} if
   * enabled.
   */
  public final ImmutableList<String> preferredTextLanguages;
  /**
   * The preferred {@link C.RoleFlags} for text tracks. {@code 0} selects the default track if there
   * is one, or no track otherwise. The default value is {@code 0}, or {@link C#ROLE_FLAG_SUBTITLE}
   * | {@link C#ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND} if the accessibility {@link CaptioningManager}
   * is enabled.
   */
  @C.RoleFlags public final int preferredTextRoleFlags;
  /**
   * Whether a text track with undetermined language should be selected if no track with {@link
   * #preferredTextLanguages} is available, or if {@link #preferredTextLanguages} is unset. The
   * default value is {@code false}.
   */
  public final boolean selectUndeterminedTextLanguage;
  // General
  /**
   * Whether to force selection of the single lowest bitrate audio and video tracks that comply with
   * all other constraints. The default value is {@code false}.
   */
  public final boolean forceLowestBitrate;
  /**
   * Whether to force selection of the highest bitrate audio and video tracks that comply with all
   * other constraints. The default value is {@code false}.
   */
  public final boolean forceHighestSupportedBitrate;

  protected TrackSelectionParameters(Builder builder) {
    // Video
    this.maxVideoWidth = builder.maxVideoWidth;
    this.maxVideoHeight = builder.maxVideoHeight;
    this.maxVideoFrameRate = builder.maxVideoFrameRate;
    this.maxVideoBitrate = builder.maxVideoBitrate;
    this.minVideoWidth = builder.minVideoWidth;
    this.minVideoHeight = builder.minVideoHeight;
    this.minVideoFrameRate = builder.minVideoFrameRate;
    this.minVideoBitrate = builder.minVideoBitrate;
    this.viewportWidth = builder.viewportWidth;
    this.viewportHeight = builder.viewportHeight;
    this.viewportOrientationMayChange = builder.viewportOrientationMayChange;
    this.preferredVideoMimeTypes = builder.preferredVideoMimeTypes;
    // Audio
    this.preferredAudioLanguages = builder.preferredAudioLanguages;
    this.preferredAudioRoleFlags = builder.preferredAudioRoleFlags;
    this.maxAudioChannelCount = builder.maxAudioChannelCount;
    this.maxAudioBitrate = builder.maxAudioBitrate;
    this.preferredAudioMimeTypes = builder.preferredAudioMimeTypes;
    // Text
    this.preferredTextLanguages = builder.preferredTextLanguages;
    this.preferredTextRoleFlags = builder.preferredTextRoleFlags;
    this.selectUndeterminedTextLanguage = builder.selectUndeterminedTextLanguage;
    // General
    this.forceLowestBitrate = builder.forceLowestBitrate;
    this.forceHighestSupportedBitrate = builder.forceHighestSupportedBitrate;
  }

  /* package */ TrackSelectionParameters(Parcel in) {
    ArrayList<String> preferredAudioLanguages = new ArrayList<>();
    in.readList(preferredAudioLanguages, /* loader= */ null);
    this.preferredAudioLanguages = ImmutableList.copyOf(preferredAudioLanguages);
    this.preferredAudioRoleFlags = in.readInt();
    ArrayList<String> preferredTextLanguages1 = new ArrayList<>();
    in.readList(preferredTextLanguages1, /* loader= */ null);
    this.preferredTextLanguages = ImmutableList.copyOf(preferredTextLanguages1);
    this.preferredTextRoleFlags = in.readInt();
    this.selectUndeterminedTextLanguage = Util.readBoolean(in);
    // Video
    this.maxVideoWidth = in.readInt();
    this.maxVideoHeight = in.readInt();
    this.maxVideoFrameRate = in.readInt();
    this.maxVideoBitrate = in.readInt();
    this.minVideoWidth = in.readInt();
    this.minVideoHeight = in.readInt();
    this.minVideoFrameRate = in.readInt();
    this.minVideoBitrate = in.readInt();
    this.viewportWidth = in.readInt();
    this.viewportHeight = in.readInt();
    this.viewportOrientationMayChange = Util.readBoolean(in);
    ArrayList<String> preferredVideoMimeTypes = new ArrayList<>();
    in.readList(preferredVideoMimeTypes, /* loader= */ null);
    this.preferredVideoMimeTypes = ImmutableList.copyOf(preferredVideoMimeTypes);
    // Audio
    this.maxAudioChannelCount = in.readInt();
    this.maxAudioBitrate = in.readInt();
    ArrayList<String> preferredAudioMimeTypes = new ArrayList<>();
    in.readList(preferredAudioMimeTypes, /* loader= */ null);
    this.preferredAudioMimeTypes = ImmutableList.copyOf(preferredAudioMimeTypes);
    // General
    this.forceLowestBitrate = Util.readBoolean(in);
    this.forceHighestSupportedBitrate = Util.readBoolean(in);
  }

  /** Creates a new {@link Builder}, copying the initial values from this instance. */
  public Builder buildUpon() {
    return new Builder(this);
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    TrackSelectionParameters other = (TrackSelectionParameters) obj;
    // Video
    return maxVideoWidth == other.maxVideoWidth
        && maxVideoHeight == other.maxVideoHeight
        && maxVideoFrameRate == other.maxVideoFrameRate
        && maxVideoBitrate == other.maxVideoBitrate
        && minVideoWidth == other.minVideoWidth
        && minVideoHeight == other.minVideoHeight
        && minVideoFrameRate == other.minVideoFrameRate
        && minVideoBitrate == other.minVideoBitrate
        && viewportOrientationMayChange == other.viewportOrientationMayChange
        && viewportWidth == other.viewportWidth
        && viewportHeight == other.viewportHeight
        && preferredVideoMimeTypes.equals(other.preferredVideoMimeTypes)
        // Audio
        && preferredAudioLanguages.equals(other.preferredAudioLanguages)
        && preferredAudioRoleFlags == other.preferredAudioRoleFlags
        && maxAudioChannelCount == other.maxAudioChannelCount
        && maxAudioBitrate == other.maxAudioBitrate
        && preferredAudioMimeTypes.equals(other.preferredAudioMimeTypes)
        && preferredTextLanguages.equals(other.preferredTextLanguages)
        && preferredTextRoleFlags == other.preferredTextRoleFlags
        && selectUndeterminedTextLanguage == other.selectUndeterminedTextLanguage
        // General
        && forceLowestBitrate == other.forceLowestBitrate
        && forceHighestSupportedBitrate == other.forceHighestSupportedBitrate;
  }

  @Override
  public int hashCode() {
    int result = 1;
    // Video
    result = 31 * result + maxVideoWidth;
    result = 31 * result + maxVideoHeight;
    result = 31 * result + maxVideoFrameRate;
    result = 31 * result + maxVideoBitrate;
    result = 31 * result + minVideoWidth;
    result = 31 * result + minVideoHeight;
    result = 31 * result + minVideoFrameRate;
    result = 31 * result + minVideoBitrate;
    result = 31 * result + (viewportOrientationMayChange ? 1 : 0);
    result = 31 * result + viewportWidth;
    result = 31 * result + viewportHeight;
    result = 31 * result + preferredVideoMimeTypes.hashCode();
    // Audio
    result = 31 * result + preferredAudioLanguages.hashCode();
    result = 31 * result + preferredAudioRoleFlags;
    result = 31 * result + maxAudioChannelCount;
    result = 31 * result + maxAudioBitrate;
    result = 31 * result + preferredAudioMimeTypes.hashCode();
    // Text
    result = 31 * result + preferredTextLanguages.hashCode();
    result = 31 * result + preferredTextRoleFlags;
    result = 31 * result + (selectUndeterminedTextLanguage ? 1 : 0);
    // General
    result = 31 * result + (forceLowestBitrate ? 1 : 0);
    result = 31 * result + (forceHighestSupportedBitrate ? 1 : 0);
    return result;
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeList(preferredAudioLanguages);
    dest.writeInt(preferredAudioRoleFlags);
    dest.writeList(preferredTextLanguages);
    dest.writeInt(preferredTextRoleFlags);
    Util.writeBoolean(dest, selectUndeterminedTextLanguage);
    // Video
    dest.writeInt(maxVideoWidth);
    dest.writeInt(maxVideoHeight);
    dest.writeInt(maxVideoFrameRate);
    dest.writeInt(maxVideoBitrate);
    dest.writeInt(minVideoWidth);
    dest.writeInt(minVideoHeight);
    dest.writeInt(minVideoFrameRate);
    dest.writeInt(minVideoBitrate);
    dest.writeInt(viewportWidth);
    dest.writeInt(viewportHeight);
    Util.writeBoolean(dest, viewportOrientationMayChange);
    dest.writeList(preferredVideoMimeTypes);
    // Audio
    dest.writeInt(maxAudioChannelCount);
    dest.writeInt(maxAudioBitrate);
    dest.writeList(preferredAudioMimeTypes);
    // General
    Util.writeBoolean(dest, forceLowestBitrate);
    Util.writeBoolean(dest, forceHighestSupportedBitrate);
  }
}
