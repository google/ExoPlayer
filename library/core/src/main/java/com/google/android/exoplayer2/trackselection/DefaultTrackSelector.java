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
package com.google.android.exoplayer2.trackselection;

import android.content.Context;
import android.graphics.Point;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A default {@link TrackSelector} suitable for most use cases.
 *
 * <h3>Constraint based track selection</h3>
 *
 * Whilst this selector supports setting specific track overrides, the recommended way of changing
 * which tracks are selected is by setting {@link Parameters} that constrain the track selection
 * process. For example an instance can specify a preferred language for the audio track, and impose
 * constraints on the maximum video resolution that should be selected for adaptive playbacks.
 * Modifying the parameters is simple:
 *
 * <pre>{@code
 * Parameters currentParameters = trackSelector.getParameters();
 * // Generate new parameters to prefer German audio and impose a maximum video size constraint.
 * Parameters newParameters = currentParameters
 *     .withPreferredAudioLanguage("deu")
 *     .withMaxVideoSize(1024, 768);
 * // Set the new parameters on the selector.
 * trackSelector.setParameters(newParameters);
 * }</pre>
 *
 * There are several benefits to using constraint based track selection instead of specific track
 * overrides:
 *
 * <ul>
 *   <li>You can specify constraints before knowing what tracks the media provides. This can
 *       simplify track selection code (e.g. you don't have to listen for changes in the available
 *       tracks before configuring the selector).
 *   <li>Constraints can be applied consistently across all periods in a complex piece of media,
 *       even if those periods contain different tracks. In contrast, a specific track override is
 *       only applied to periods whose tracks match those for which the override was set.
 * </ul>
 *
 * <h3>Track overrides</h3>
 *
 * This selector supports overriding of track selections for each renderer. To specify an override
 * for a renderer it's first necessary to obtain the tracks that have been mapped to it:
 *
 * <pre>{@code
 * MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
 * TrackGroupArray rendererTrackGroups = mappedTrackInfo == null ? null
 *     : mappedTrackInfo.getTrackGroups(rendererIndex);
 * }</pre>
 *
 * If {@code rendererTrackGroups} is null then there aren't any currently mapped tracks, and so
 * setting an override isn't possible. Note that a {@link Player.EventListener} registered on the
 * player can be used to determine when the current tracks (and therefore the mapping) changes. If
 * {@code rendererTrackGroups} is non-null then an override can be set. The next step is to query
 * the properties of the available tracks to determine the {@code groupIndex} of the track group you
 * want to select and the {@code trackIndices} within it. You can then create and set the override:
 *
 * <pre>{@code
 * trackSelector.setSelectionOverride(rendererIndex, rendererTrackGroups,
 *     new SelectionOverride(groupIndex, trackIndices));
 * }</pre>
 *
 * If the override is {@code null} then no tracks will be selected.
 *
 * <p>Note that an override applies only when the track groups available to the renderer match the
 * {@link TrackGroupArray} for which the override was specified. Overrides can be cleared using the
 * {@code clearSelectionOverride} methods.
 *
 * <h3>Disabling renderers</h3>
 *
 * Renderers can be disabled using {@link #setRendererDisabled(int, boolean)}. Disabling a renderer
 * differs from setting a {@code null} override because the renderer is disabled unconditionally,
 * whereas a {@code null} override is applied only when the track groups available to the renderer
 * match the {@link TrackGroupArray} for which it was specified.
 *
 * <h3>Tunneling</h3>
 *
 * Tunneled playback can be enabled in cases where the combination of renderers and selected tracks
 * support it. See {@link #setTunnelingAudioSessionId(int)} for more details.
 */
public class DefaultTrackSelector extends MappingTrackSelector {

  /**
   * A builder for {@link Parameters}.
   */
  public static final class ParametersBuilder {

    private String preferredAudioLanguage;
    private String preferredTextLanguage;
    private boolean selectUndeterminedTextLanguage;
    private int disabledTextTrackSelectionFlags;
    private boolean forceLowestBitrate;
    private boolean allowMixedMimeAdaptiveness;
    private boolean allowNonSeamlessAdaptiveness;
    private int maxVideoWidth;
    private int maxVideoHeight;
    private int maxVideoBitrate;
    private boolean exceedVideoConstraintsIfNecessary;
    private boolean exceedRendererCapabilitiesIfNecessary;
    private int viewportWidth;
    private int viewportHeight;
    private boolean viewportOrientationMayChange;

    /**
     * Creates a builder obtaining the initial values from {@link Parameters#DEFAULT}.
     */
    public ParametersBuilder() {
      this(Parameters.DEFAULT);
    }

    /**
     * @param initialValues The {@link Parameters} from which the initial values of the builder are
     *     obtained.
     */
    private ParametersBuilder(Parameters initialValues) {
      preferredAudioLanguage = initialValues.preferredAudioLanguage;
      preferredTextLanguage = initialValues.preferredTextLanguage;
      selectUndeterminedTextLanguage = initialValues.selectUndeterminedTextLanguage;
      disabledTextTrackSelectionFlags = initialValues.disabledTextTrackSelectionFlags;
      forceLowestBitrate = initialValues.forceLowestBitrate;
      allowMixedMimeAdaptiveness = initialValues.allowMixedMimeAdaptiveness;
      allowNonSeamlessAdaptiveness = initialValues.allowNonSeamlessAdaptiveness;
      maxVideoWidth = initialValues.maxVideoWidth;
      maxVideoHeight = initialValues.maxVideoHeight;
      maxVideoBitrate = initialValues.maxVideoBitrate;
      exceedVideoConstraintsIfNecessary = initialValues.exceedVideoConstraintsIfNecessary;
      exceedRendererCapabilitiesIfNecessary = initialValues.exceedRendererCapabilitiesIfNecessary;
      viewportWidth = initialValues.viewportWidth;
      viewportHeight = initialValues.viewportHeight;
      viewportOrientationMayChange = initialValues.viewportOrientationMayChange;
    }

    /**
     * See {@link Parameters#preferredAudioLanguage}.
     *
     * @return This builder.
     */
    public ParametersBuilder setPreferredAudioLanguage(String preferredAudioLanguage) {
      this.preferredAudioLanguage = preferredAudioLanguage;
      return this;
    }

    /**
     * See {@link Parameters#preferredTextLanguage}.
     *
     * @return This builder.
     */
    public ParametersBuilder setPreferredTextLanguage(String preferredTextLanguage) {
      this.preferredTextLanguage = preferredTextLanguage;
      return this;
    }

    /**
     * See {@link Parameters#selectUndeterminedTextLanguage}.
     *
     * @return This builder.
     */
    public ParametersBuilder setSelectUndeterminedTextLanguage(
        boolean selectUndeterminedTextLanguage) {
      this.selectUndeterminedTextLanguage = selectUndeterminedTextLanguage;
      return this;
    }

    /**
     * See {@link Parameters#disabledTextTrackSelectionFlags}.
     *
     * @return This builder.
     */
    public ParametersBuilder setDisabledTextTrackSelectionFlags(
        int disabledTextTrackSelectionFlags) {
      this.disabledTextTrackSelectionFlags = disabledTextTrackSelectionFlags;
      return this;
    }

    /**
     * See {@link Parameters#forceLowestBitrate}.
     *
     * @return This builder.
     */
    public ParametersBuilder setForceLowestBitrate(boolean forceLowestBitrate) {
      this.forceLowestBitrate = forceLowestBitrate;
      return this;
    }

    /**
     * See {@link Parameters#allowMixedMimeAdaptiveness}.
     *
     * @return This builder.
     */
    public ParametersBuilder setAllowMixedMimeAdaptiveness(boolean allowMixedMimeAdaptiveness) {
      this.allowMixedMimeAdaptiveness = allowMixedMimeAdaptiveness;
      return this;
    }

    /**
     * See {@link Parameters#allowNonSeamlessAdaptiveness}.
     *
     * @return This builder.
     */
    public ParametersBuilder setAllowNonSeamlessAdaptiveness(boolean allowNonSeamlessAdaptiveness) {
      this.allowNonSeamlessAdaptiveness = allowNonSeamlessAdaptiveness;
      return this;
    }

    /**
     * Equivalent to {@link #setMaxVideoSize setMaxVideoSize(1279, 719)}.
     *
     * @return This builder.
     */
    public ParametersBuilder setMaxVideoSizeSd() {
      return setMaxVideoSize(1279, 719);
    }

    /**
     * Equivalent to {@link #setMaxVideoSize setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)}.
     *
     * @return This builder.
     */
    public ParametersBuilder clearVideoSizeConstraints() {
      return setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * See {@link Parameters#maxVideoWidth} and {@link Parameters#maxVideoHeight}.
     *
     * @return This builder.
     */
    public ParametersBuilder setMaxVideoSize(int maxVideoWidth, int maxVideoHeight) {
      this.maxVideoWidth = maxVideoWidth;
      this.maxVideoHeight = maxVideoHeight;
      return this;
    }

    /**
     * See {@link Parameters#maxVideoBitrate}.
     *
     * @return This builder.
     */
    public ParametersBuilder setMaxVideoBitrate(int maxVideoBitrate) {
      this.maxVideoBitrate = maxVideoBitrate;
      return this;
    }

    /**
     * See {@link Parameters#exceedVideoConstraintsIfNecessary}.
     *
     * @return This builder.
     */
    public ParametersBuilder setExceedVideoConstraintsIfNecessary(
        boolean exceedVideoConstraintsIfNecessary) {
      this.exceedVideoConstraintsIfNecessary = exceedVideoConstraintsIfNecessary;
      return this;
    }

    /**
     * See {@link Parameters#exceedRendererCapabilitiesIfNecessary}.
     *
     * @return This builder.
     */
    public ParametersBuilder setExceedRendererCapabilitiesIfNecessary(
        boolean exceedRendererCapabilitiesIfNecessary) {
      this.exceedRendererCapabilitiesIfNecessary = exceedRendererCapabilitiesIfNecessary;
      return this;
    }

    /**
     * Equivalent to invoking {@link #setViewportSize} with the viewport size obtained from
     * {@link Util#getPhysicalDisplaySize(Context)}.
     *
     * @param context The context to obtain the viewport size from.
     * @param viewportOrientationMayChange See {@link #viewportOrientationMayChange}.
     * @return This builder.
     */
    public ParametersBuilder setViewportSizeToPhysicalDisplaySize(Context context,
        boolean viewportOrientationMayChange) {
      // Assume the viewport is fullscreen.
      Point viewportSize = Util.getPhysicalDisplaySize(context);
      return setViewportSize(viewportSize.x, viewportSize.y, viewportOrientationMayChange);
    }

    /**
     * Equivalent to
     * {@link #setViewportSize setViewportSize(Integer.MAX_VALUE, Integer.MAX_VALUE, true)}.
     *
     * @return This builder.
     */
    public ParametersBuilder clearViewportSizeConstraints() {
      return setViewportSize(Integer.MAX_VALUE, Integer.MAX_VALUE, true);
    }

    /**
     * See {@link Parameters#viewportWidth}, {@link Parameters#maxVideoHeight} and
     * {@link Parameters#viewportOrientationMayChange}.
     *
     * @return This builder.
     */
    public ParametersBuilder setViewportSize(int viewportWidth, int viewportHeight,
        boolean viewportOrientationMayChange) {
      this.viewportWidth = viewportWidth;
      this.viewportHeight = viewportHeight;
      this.viewportOrientationMayChange = viewportOrientationMayChange;
      return this;
    }

    /**
     * Builds a {@link Parameters} instance with the selected values.
     */
    public Parameters build() {
      return new Parameters(
          preferredAudioLanguage,
          preferredTextLanguage,
          selectUndeterminedTextLanguage,
          disabledTextTrackSelectionFlags,
          forceLowestBitrate,
          allowMixedMimeAdaptiveness,
          allowNonSeamlessAdaptiveness,
          maxVideoWidth,
          maxVideoHeight,
          maxVideoBitrate,
          exceedVideoConstraintsIfNecessary,
          exceedRendererCapabilitiesIfNecessary,
          viewportWidth,
          viewportHeight,
          viewportOrientationMayChange);
    }

  }

  /** Constraint parameters for {@link DefaultTrackSelector}. */
  public static final class Parameters implements Parcelable {

    /**
     * An instance with default values:
     *
     * <ul>
     *   <li>No preferred audio language.
     *   <li>No preferred text language.
     *   <li>Text tracks with undetermined language are not selected if no track with {@link
     *       #preferredTextLanguage} is available.
     *   <li>All selection flags are considered for text track selections.
     *   <li>Lowest bitrate track selections are not forced.
     *   <li>Adaptation between different mime types is not allowed.
     *   <li>Non seamless adaptation is allowed.
     *   <li>No max limit for video width/height.
     *   <li>No max video bitrate.
     *   <li>Video constraints are exceeded if no supported selection can be made otherwise.
     *   <li>Renderer capabilities are exceeded if no supported selection can be made.
     *   <li>No viewport constraints.
     * </ul>
     */
    public static final Parameters DEFAULT = new Parameters();

    // Audio
    /**
     * The preferred language for audio, as well as for forced text tracks, as an ISO 639-2/T tag.
     * {@code null} selects the default track, or the first track if there's no default.
     */
    public final String preferredAudioLanguage;

    // Text
    /**
     * The preferred language for text tracks as an ISO 639-2/T tag. {@code null} selects the
     * default track if there is one, or no track otherwise.
     */
    public final String preferredTextLanguage;
    /**
     * Whether a text track with undetermined language should be selected if no track with
     * {@link #preferredTextLanguage} is available, or if {@link #preferredTextLanguage} is unset.
     */
    public final boolean selectUndeterminedTextLanguage;
    /**
     * Bitmask of selection flags that are disabled for text track selections. See {@link
     * C.SelectionFlags}.
     */
    public final int disabledTextTrackSelectionFlags;

    // Video
    /**
     * Maximum allowed video width.
     */
    public final int maxVideoWidth;
    /**
     * Maximum allowed video height.
     */
    public final int maxVideoHeight;
    /**
     * Maximum video bitrate.
     */
    public final int maxVideoBitrate;
    /**
     * Whether to exceed video constraints when no selection can be made otherwise.
     */
    public final boolean exceedVideoConstraintsIfNecessary;
    /**
     * Viewport width in pixels. Constrains video tracks selections for adaptive playbacks so that
     * only tracks suitable for the viewport are selected.
     */
    public final int viewportWidth;
    /**
     * Viewport height in pixels. Constrains video tracks selections for adaptive playbacks so that
     * only tracks suitable for the viewport are selected.
     */
    public final int viewportHeight;
    /**
     * Whether the viewport orientation may change during playback. Constrains video tracks
     * selections for adaptive playbacks so that only tracks suitable for the viewport are selected.
     */
    public final boolean viewportOrientationMayChange;

    // General
    /**
     * Whether to force selection of the single lowest bitrate audio and video tracks that comply
     * with all other constraints.
     */
    public final boolean forceLowestBitrate;
    /**
     * Whether to allow adaptive selections containing mixed mime types.
     */
    public final boolean allowMixedMimeAdaptiveness;
    /**
     * Whether to allow adaptive selections where adaptation may not be completely seamless.
     */
    public final boolean allowNonSeamlessAdaptiveness;
    /**
     * Whether to exceed renderer capabilities when no selection can be made otherwise.
     */
    public final boolean exceedRendererCapabilitiesIfNecessary;

    private Parameters() {
      this(
          null,
          null,
          false,
          0,
          false,
          false,
          true,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          true,
          true,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          true);
    }

    /* package */ Parameters(
        String preferredAudioLanguage,
        String preferredTextLanguage,
        boolean selectUndeterminedTextLanguage,
        int disabledTextTrackSelectionFlags,
        boolean forceLowestBitrate,
        boolean allowMixedMimeAdaptiveness,
        boolean allowNonSeamlessAdaptiveness,
        int maxVideoWidth,
        int maxVideoHeight,
        int maxVideoBitrate,
        boolean exceedVideoConstraintsIfNecessary,
        boolean exceedRendererCapabilitiesIfNecessary,
        int viewportWidth,
        int viewportHeight,
        boolean viewportOrientationMayChange) {
      this.preferredAudioLanguage = Util.normalizeLanguageCode(preferredAudioLanguage);
      this.preferredTextLanguage = Util.normalizeLanguageCode(preferredTextLanguage);
      this.selectUndeterminedTextLanguage = selectUndeterminedTextLanguage;
      this.disabledTextTrackSelectionFlags = disabledTextTrackSelectionFlags;
      this.forceLowestBitrate = forceLowestBitrate;
      this.allowMixedMimeAdaptiveness = allowMixedMimeAdaptiveness;
      this.allowNonSeamlessAdaptiveness = allowNonSeamlessAdaptiveness;
      this.maxVideoWidth = maxVideoWidth;
      this.maxVideoHeight = maxVideoHeight;
      this.maxVideoBitrate = maxVideoBitrate;
      this.exceedVideoConstraintsIfNecessary = exceedVideoConstraintsIfNecessary;
      this.exceedRendererCapabilitiesIfNecessary = exceedRendererCapabilitiesIfNecessary;
      this.viewportWidth = viewportWidth;
      this.viewportHeight = viewportHeight;
      this.viewportOrientationMayChange = viewportOrientationMayChange;
    }

    /* package */ Parameters(Parcel in) {
      this.preferredAudioLanguage = in.readString();
      this.preferredTextLanguage = in.readString();
      this.selectUndeterminedTextLanguage = Util.readBoolean(in);
      this.disabledTextTrackSelectionFlags = in.readInt();
      this.forceLowestBitrate = Util.readBoolean(in);
      this.allowMixedMimeAdaptiveness = Util.readBoolean(in);
      this.allowNonSeamlessAdaptiveness = Util.readBoolean(in);
      this.maxVideoWidth = in.readInt();
      this.maxVideoHeight = in.readInt();
      this.maxVideoBitrate = in.readInt();
      this.exceedVideoConstraintsIfNecessary = Util.readBoolean(in);
      this.exceedRendererCapabilitiesIfNecessary = Util.readBoolean(in);
      this.viewportWidth = in.readInt();
      this.viewportHeight = in.readInt();
      this.viewportOrientationMayChange = Util.readBoolean(in);
    }

    /**
     * Creates a new {@link ParametersBuilder}, copying the initial values from this instance.
     */
    public ParametersBuilder buildUpon() {
      return new ParametersBuilder(this);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      Parameters other = (Parameters) obj;
      return selectUndeterminedTextLanguage == other.selectUndeterminedTextLanguage
          && disabledTextTrackSelectionFlags == other.disabledTextTrackSelectionFlags
          && forceLowestBitrate == other.forceLowestBitrate
          && allowMixedMimeAdaptiveness == other.allowMixedMimeAdaptiveness
          && allowNonSeamlessAdaptiveness == other.allowNonSeamlessAdaptiveness
          && maxVideoWidth == other.maxVideoWidth
          && maxVideoHeight == other.maxVideoHeight
          && exceedVideoConstraintsIfNecessary == other.exceedVideoConstraintsIfNecessary
          && exceedRendererCapabilitiesIfNecessary == other.exceedRendererCapabilitiesIfNecessary
          && viewportOrientationMayChange == other.viewportOrientationMayChange
          && viewportWidth == other.viewportWidth
          && viewportHeight == other.viewportHeight
          && maxVideoBitrate == other.maxVideoBitrate
          && TextUtils.equals(preferredAudioLanguage, other.preferredAudioLanguage)
          && TextUtils.equals(preferredTextLanguage, other.preferredTextLanguage);
    }

    @Override
    public int hashCode() {
      int result = selectUndeterminedTextLanguage ? 1 : 0;
      result = 31 * result + disabledTextTrackSelectionFlags;
      result = 31 * result + (forceLowestBitrate ? 1 : 0);
      result = 31 * result + (allowMixedMimeAdaptiveness ? 1 : 0);
      result = 31 * result + (allowNonSeamlessAdaptiveness ? 1 : 0);
      result = 31 * result + maxVideoWidth;
      result = 31 * result + maxVideoHeight;
      result = 31 * result + (exceedVideoConstraintsIfNecessary ? 1 : 0);
      result = 31 * result + (exceedRendererCapabilitiesIfNecessary ? 1 : 0);
      result = 31 * result + (viewportOrientationMayChange ? 1 : 0);
      result = 31 * result + viewportWidth;
      result = 31 * result + viewportHeight;
      result = 31 * result + maxVideoBitrate;
      result = 31 * result + preferredAudioLanguage.hashCode();
      result = 31 * result + preferredTextLanguage.hashCode();
      return result;
    }

    // Parcelable implementation.

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeString(preferredAudioLanguage);
      dest.writeString(preferredTextLanguage);
      Util.writeBoolean(dest, selectUndeterminedTextLanguage);
      dest.writeInt(disabledTextTrackSelectionFlags);
      Util.writeBoolean(dest, forceLowestBitrate);
      Util.writeBoolean(dest, allowMixedMimeAdaptiveness);
      Util.writeBoolean(dest, allowNonSeamlessAdaptiveness);
      dest.writeInt(maxVideoWidth);
      dest.writeInt(maxVideoHeight);
      dest.writeInt(maxVideoBitrate);
      Util.writeBoolean(dest, exceedVideoConstraintsIfNecessary);
      Util.writeBoolean(dest, exceedRendererCapabilitiesIfNecessary);
      dest.writeInt(viewportWidth);
      dest.writeInt(viewportHeight);
      Util.writeBoolean(dest, viewportOrientationMayChange);
    }

    public static final Parcelable.Creator<Parameters> CREATOR =
        new Parcelable.Creator<Parameters>() {

          @Override
          public Parameters createFromParcel(Parcel in) {
            return new Parameters(in);
          }

          @Override
          public Parameters[] newArray(int size) {
            return new Parameters[size];
          }
        };
  }

  /** A track selection override. */
  public static class SelectionOverride {

    public final TrackSelection.Factory factory;
    public final int groupIndex;
    public final int[] tracks;
    public final int length;

    /**
     * @param factory A factory for creating selections from this override.
     * @param groupIndex The overriding track group index.
     * @param tracks The overriding track indices within the track group.
     */
    public SelectionOverride(TrackSelection.Factory factory, int groupIndex, int... tracks) {
      this.factory = factory;
      this.groupIndex = groupIndex;
      this.tracks = tracks;
      this.length = tracks.length;
    }

    /**
     * Creates an selection from this override.
     *
     * @param groups The track groups whose selection is being overridden.
     * @return The selection.
     */
    public TrackSelection createTrackSelection(TrackGroupArray groups) {
      return factory.createTrackSelection(groups.get(groupIndex), tracks);
    }

    /** Returns whether this override contains the specified track index. */
    public boolean containsTrack(int track) {
      for (int overrideTrack : tracks) {
        if (overrideTrack == track) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * If a dimension (i.e. width or height) of a video is greater or equal to this fraction of the
   * corresponding viewport dimension, then the video is considered as filling the viewport (in that
   * dimension).
   */
  private static final float FRACTION_TO_CONSIDER_FULLSCREEN = 0.98f;
  private static final int[] NO_TRACKS = new int[0];
  private static final int WITHIN_RENDERER_CAPABILITIES_BONUS = 1000;

  private final TrackSelection.Factory adaptiveTrackSelectionFactory;
  private final AtomicReference<Parameters> paramsReference;
  private final SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides;
  private final SparseBooleanArray rendererDisabledFlags;

  private int tunnelingAudioSessionId;

  /**
   * Constructs an instance that does not support adaptive track selection.
   */
  public DefaultTrackSelector() {
    this((TrackSelection.Factory) null);
  }

  /**
   * Constructs an instance that supports adaptive track selection. Adaptive track selections use
   * the provided {@link BandwidthMeter} to determine which individual track should be used during
   * playback.
   *
   * @param bandwidthMeter The {@link BandwidthMeter}.
   */
  public DefaultTrackSelector(BandwidthMeter bandwidthMeter) {
    this(new AdaptiveTrackSelection.Factory(bandwidthMeter));
  }

  /**
   * Constructs an instance that uses a factory to create adaptive track selections.
   *
   * @param adaptiveTrackSelectionFactory A factory for adaptive {@link TrackSelection}s, or null if
   *     the selector should not support adaptive tracks.
   */
  public DefaultTrackSelector(TrackSelection.Factory adaptiveTrackSelectionFactory) {
    this.adaptiveTrackSelectionFactory = adaptiveTrackSelectionFactory;
    paramsReference = new AtomicReference<>(Parameters.DEFAULT);
    selectionOverrides = new SparseArray<>();
    rendererDisabledFlags = new SparseBooleanArray();
    tunnelingAudioSessionId = C.AUDIO_SESSION_ID_UNSET;
  }

  /**
   * Atomically sets the provided parameters for track selection.
   *
   * @param params The parameters for track selection.
   */
  public void setParameters(Parameters params) {
    Assertions.checkNotNull(params);
    if (!paramsReference.getAndSet(params).equals(params)) {
      invalidate();
    }
  }

  /**
   * Gets the current selection parameters.
   *
   * @return The current selection parameters.
   */
  public Parameters getParameters() {
    return paramsReference.get();
  }

  /**
   * Sets whether the renderer at the specified index is disabled. Disabling a renderer prevents the
   * selector from selecting any tracks for it.
   *
   * @param rendererIndex The renderer index.
   * @param disabled Whether the renderer is disabled.
   */
  public final void setRendererDisabled(int rendererIndex, boolean disabled) {
    if (rendererDisabledFlags.get(rendererIndex) == disabled) {
      // The disabled flag is unchanged.
      return;
    }
    rendererDisabledFlags.put(rendererIndex, disabled);
    invalidate();
  }

  /**
   * Returns whether the renderer is disabled.
   *
   * @param rendererIndex The renderer index.
   * @return Whether the renderer is disabled.
   */
  public final boolean getRendererDisabled(int rendererIndex) {
    return rendererDisabledFlags.get(rendererIndex);
  }

  /**
   * Overrides the track selection for the renderer at the specified index.
   *
   * <p>When the {@link TrackGroupArray} mapped to the renderer matches the one provided, the
   * override is applied. When the {@link TrackGroupArray} does not match, the override has no
   * effect. The override replaces any previous override for the specified {@link TrackGroupArray}
   * for the specified {@link Renderer}.
   *
   * <p>Passing a {@code null} override will cause the renderer to be disabled when the {@link
   * TrackGroupArray} mapped to it matches the one provided. When the {@link TrackGroupArray} does
   * not match a {@code null} override has no effect. Hence a {@code null} override differs from
   * disabling the renderer using {@link #setRendererDisabled(int, boolean)} because the renderer is
   * disabled conditionally on the {@link TrackGroupArray} mapped to it, where-as {@link
   * #setRendererDisabled(int, boolean)} disables the renderer unconditionally.
   *
   * <p>To remove overrides use {@link #clearSelectionOverride(int, TrackGroupArray)}, {@link
   * #clearSelectionOverrides(int)} or {@link #clearSelectionOverrides()}.
   *
   * @param rendererIndex The renderer index.
   * @param groups The {@link TrackGroupArray} for which the override should be applied.
   * @param override The override.
   */
  public final void setSelectionOverride(
      int rendererIndex, TrackGroupArray groups, SelectionOverride override) {
    Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
    if (overrides == null) {
      overrides = new HashMap<>();
      selectionOverrides.put(rendererIndex, overrides);
    }
    if (overrides.containsKey(groups) && Util.areEqual(overrides.get(groups), override)) {
      // The override is unchanged.
      return;
    }
    overrides.put(groups, override);
    invalidate();
  }

  /**
   * Returns whether there is an override for the specified renderer and {@link TrackGroupArray}.
   *
   * @param rendererIndex The renderer index.
   * @param groups The {@link TrackGroupArray}.
   * @return Whether there is an override.
   */
  public final boolean hasSelectionOverride(int rendererIndex, TrackGroupArray groups) {
    Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
    return overrides != null && overrides.containsKey(groups);
  }

  /**
   * Returns the override for the specified renderer and {@link TrackGroupArray}.
   *
   * @param rendererIndex The renderer index.
   * @param groups The {@link TrackGroupArray}.
   * @return The override, or null if no override exists.
   */
  public final SelectionOverride getSelectionOverride(int rendererIndex, TrackGroupArray groups) {
    Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
    return overrides != null ? overrides.get(groups) : null;
  }

  /**
   * Clears a track selection override for the specified renderer and {@link TrackGroupArray}.
   *
   * @param rendererIndex The renderer index.
   * @param groups The {@link TrackGroupArray} for which the override should be cleared.
   */
  public final void clearSelectionOverride(int rendererIndex, TrackGroupArray groups) {
    Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
    if (overrides == null || !overrides.containsKey(groups)) {
      // Nothing to clear.
      return;
    }
    overrides.remove(groups);
    if (overrides.isEmpty()) {
      selectionOverrides.remove(rendererIndex);
    }
    invalidate();
  }

  /**
   * Clears all track selection overrides for the specified renderer.
   *
   * @param rendererIndex The renderer index.
   */
  public final void clearSelectionOverrides(int rendererIndex) {
    Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
    if (overrides == null || overrides.isEmpty()) {
      // Nothing to clear.
      return;
    }
    selectionOverrides.remove(rendererIndex);
    invalidate();
  }

  /** Clears all track selection overrides for all renderers. */
  public final void clearSelectionOverrides() {
    if (selectionOverrides.size() == 0) {
      // Nothing to clear.
      return;
    }
    selectionOverrides.clear();
    invalidate();
  }

  /**
   * Enables or disables tunneling. To enable tunneling, pass an audio session id to use when in
   * tunneling mode. Session ids can be generated using {@link
   * C#generateAudioSessionIdV21(Context)}. To disable tunneling pass {@link
   * C#AUDIO_SESSION_ID_UNSET}. Tunneling will only be activated if it's both enabled and supported
   * by the audio and video renderers for the selected tracks.
   *
   * @param tunnelingAudioSessionId The audio session id to use when tunneling, or {@link
   *     C#AUDIO_SESSION_ID_UNSET} to disable tunneling.
   */
  public void setTunnelingAudioSessionId(int tunnelingAudioSessionId) {
    if (this.tunnelingAudioSessionId != tunnelingAudioSessionId) {
      this.tunnelingAudioSessionId = tunnelingAudioSessionId;
      invalidate();
    }
  }

  // MappingTrackSelector implementation.

  @Override
  protected final Pair<RendererConfiguration[], TrackSelection[]> selectTracks(
      RendererCapabilities[] rendererCapabilities, MappedTrackInfo mappedTrackInfo)
      throws ExoPlaybackException {
    int rendererCount = rendererCapabilities.length;
    TrackSelection[] rendererTrackSelections =
        selectAllTracks(rendererCapabilities, mappedTrackInfo);

    // Apply track disabling and overriding.
    for (int i = 0; i < rendererCount; i++) {
      if (rendererDisabledFlags.get(i)) {
        rendererTrackSelections[i] = null;
      } else {
        TrackGroupArray rendererTrackGroup = mappedTrackInfo.getTrackGroups(i);
        if (hasSelectionOverride(i, rendererTrackGroup)) {
          SelectionOverride override = selectionOverrides.get(i).get(rendererTrackGroup);
          rendererTrackSelections[i] =
              override == null ? null : override.createTrackSelection(rendererTrackGroup);
        }
      }
    }

    // Initialize the renderer configurations to the default configuration for all renderers with
    // selections, and null otherwise.
    RendererConfiguration[] rendererConfigurations =
        new RendererConfiguration[rendererCapabilities.length];
    for (int i = 0; i < rendererCount; i++) {
      boolean forceRendererDisabled = rendererDisabledFlags.get(i);
      boolean rendererEnabled =
          !forceRendererDisabled
              && (rendererCapabilities[i].getTrackType() == C.TRACK_TYPE_NONE
                  || rendererTrackSelections[i] != null);
      rendererConfigurations[i] = rendererEnabled ? RendererConfiguration.DEFAULT : null;
    }

    // Configure audio and video renderers to use tunneling if appropriate.
    maybeConfigureRenderersForTunneling(
        mappedTrackInfo,
        rendererCapabilities,
        rendererConfigurations,
        rendererTrackSelections,
        tunnelingAudioSessionId);

    return Pair.create(rendererConfigurations, rendererTrackSelections);
  }

  // Track selection prior to overrides and disabled flags being applied.

  /**
   * Called from {@link #selectTracks(RendererCapabilities[], MappedTrackInfo)} to make a track
   * selection for each renderer, prior to overrides and disabled flags being applied.
   *
   * <p>The implementation should not account for overrides and disabled flags. Track selections
   * generated by this method will be overridden to account for these properties.
   *
   * @param rendererCapabilities The {@link RendererCapabilities} of each renderer.
   * @param mappedTrackInfo Mapped track information.
   * @return Track selections for each renderer. A null selection indicates the renderer should be
   *     disabled, unless RendererCapabilities#getTrackType()} is {@link C#TRACK_TYPE_NONE}.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  protected TrackSelection[] selectAllTracks(
      RendererCapabilities[] rendererCapabilities, MappedTrackInfo mappedTrackInfo)
      throws ExoPlaybackException {
    int rendererCount = rendererCapabilities.length;
    TrackSelection[] rendererTrackSelections = new TrackSelection[rendererCount];
    Parameters params = paramsReference.get();

    boolean seenVideoRendererWithMappedTracks = false;
    boolean selectedVideoTracks = false;
    for (int i = 0; i < rendererCount; i++) {
      if (C.TRACK_TYPE_VIDEO == rendererCapabilities[i].getTrackType()) {
        if (!selectedVideoTracks) {
          rendererTrackSelections[i] =
              selectVideoTrack(
                  rendererCapabilities[i],
                  mappedTrackInfo.getTrackGroups(i),
                  mappedTrackInfo.getRendererTrackSupport(i),
                  params,
                  adaptiveTrackSelectionFactory);
          selectedVideoTracks = rendererTrackSelections[i] != null;
        }
        seenVideoRendererWithMappedTracks |= mappedTrackInfo.getTrackGroups(i).length > 0;
      }
    }

    boolean selectedAudioTracks = false;
    boolean selectedTextTracks = false;
    for (int i = 0; i < rendererCount; i++) {
      switch (rendererCapabilities[i].getTrackType()) {
        case C.TRACK_TYPE_VIDEO:
          // Already done. Do nothing.
          break;
        case C.TRACK_TYPE_AUDIO:
          if (!selectedAudioTracks) {
            rendererTrackSelections[i] =
                selectAudioTrack(
                    mappedTrackInfo.getTrackGroups(i),
                    mappedTrackInfo.getRendererTrackSupport(i),
                    params,
                    seenVideoRendererWithMappedTracks ? null : adaptiveTrackSelectionFactory);
            selectedAudioTracks = rendererTrackSelections[i] != null;
          }
          break;
        case C.TRACK_TYPE_TEXT:
          if (!selectedTextTracks) {
            rendererTrackSelections[i] =
                selectTextTrack(
                    mappedTrackInfo.getTrackGroups(i),
                    mappedTrackInfo.getRendererTrackSupport(i),
                    params);
            selectedTextTracks = rendererTrackSelections[i] != null;
          }
          break;
        default:
          rendererTrackSelections[i] =
              selectOtherTrack(
                  rendererCapabilities[i].getTrackType(),
                  mappedTrackInfo.getTrackGroups(i),
                  mappedTrackInfo.getRendererTrackSupport(i),
                  params);
          break;
      }
    }

    return rendererTrackSelections;
  }

  // Video track selection implementation.

  /**
   * Called by {@link #selectTracks(RendererCapabilities[], MappedTrackInfo)} to create a {@link
   * TrackSelection} for a video renderer.
   *
   * @param rendererCapabilities The {@link RendererCapabilities} for the renderer.
   * @param groups The {@link TrackGroupArray} mapped to the renderer.
   * @param formatSupport The result of {@link RendererCapabilities#supportsFormat} for each mapped
   *     track, indexed by track group index and track index (in that order).
   * @param params The selector's current constraint parameters.
   * @param adaptiveTrackSelectionFactory A factory for generating adaptive track selections, or
   *     null if a fixed track selection is required.
   * @return The {@link TrackSelection} for the renderer, or null if no selection was made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  protected TrackSelection selectVideoTrack(
      RendererCapabilities rendererCapabilities,
      TrackGroupArray groups,
      int[][] formatSupport,
      Parameters params,
      TrackSelection.Factory adaptiveTrackSelectionFactory)
      throws ExoPlaybackException {
    TrackSelection selection = null;
    if (!params.forceLowestBitrate && adaptiveTrackSelectionFactory != null) {
      selection = selectAdaptiveVideoTrack(rendererCapabilities, groups, formatSupport,
          params, adaptiveTrackSelectionFactory);
    }
    if (selection == null) {
      selection = selectFixedVideoTrack(groups, formatSupport, params);
    }
    return selection;
  }

  private static TrackSelection selectAdaptiveVideoTrack(RendererCapabilities rendererCapabilities,
      TrackGroupArray groups, int[][] formatSupport, Parameters params,
      TrackSelection.Factory adaptiveTrackSelectionFactory) throws ExoPlaybackException {
    int requiredAdaptiveSupport = params.allowNonSeamlessAdaptiveness
        ? (RendererCapabilities.ADAPTIVE_NOT_SEAMLESS | RendererCapabilities.ADAPTIVE_SEAMLESS)
        : RendererCapabilities.ADAPTIVE_SEAMLESS;
    boolean allowMixedMimeTypes = params.allowMixedMimeAdaptiveness
        && (rendererCapabilities.supportsMixedMimeTypeAdaptation() & requiredAdaptiveSupport) != 0;
    for (int i = 0; i < groups.length; i++) {
      TrackGroup group = groups.get(i);
      int[] adaptiveTracks = getAdaptiveVideoTracksForGroup(group, formatSupport[i],
          allowMixedMimeTypes, requiredAdaptiveSupport, params.maxVideoWidth, params.maxVideoHeight,
          params.maxVideoBitrate, params.viewportWidth, params.viewportHeight,
          params.viewportOrientationMayChange);
      if (adaptiveTracks.length > 0) {
        return adaptiveTrackSelectionFactory.createTrackSelection(group, adaptiveTracks);
      }
    }
    return null;
  }

  private static int[] getAdaptiveVideoTracksForGroup(TrackGroup group, int[] formatSupport,
      boolean allowMixedMimeTypes, int requiredAdaptiveSupport, int maxVideoWidth,
      int maxVideoHeight, int maxVideoBitrate, int viewportWidth, int viewportHeight,
      boolean viewportOrientationMayChange) {
    if (group.length < 2) {
      return NO_TRACKS;
    }

    List<Integer> selectedTrackIndices = getViewportFilteredTrackIndices(group, viewportWidth,
        viewportHeight, viewportOrientationMayChange);
    if (selectedTrackIndices.size() < 2) {
      return NO_TRACKS;
    }

    String selectedMimeType = null;
    if (!allowMixedMimeTypes) {
      // Select the mime type for which we have the most adaptive tracks.
      HashSet<String> seenMimeTypes = new HashSet<>();
      int selectedMimeTypeTrackCount = 0;
      for (int i = 0; i < selectedTrackIndices.size(); i++) {
        int trackIndex = selectedTrackIndices.get(i);
        String sampleMimeType = group.getFormat(trackIndex).sampleMimeType;
        if (seenMimeTypes.add(sampleMimeType)) {
          int countForMimeType = getAdaptiveVideoTrackCountForMimeType(group, formatSupport,
              requiredAdaptiveSupport, sampleMimeType, maxVideoWidth, maxVideoHeight,
              maxVideoBitrate, selectedTrackIndices);
          if (countForMimeType > selectedMimeTypeTrackCount) {
            selectedMimeType = sampleMimeType;
            selectedMimeTypeTrackCount = countForMimeType;
          }
        }
      }
    }

    // Filter by the selected mime type.
    filterAdaptiveVideoTrackCountForMimeType(group, formatSupport, requiredAdaptiveSupport,
        selectedMimeType, maxVideoWidth, maxVideoHeight, maxVideoBitrate, selectedTrackIndices);

    return selectedTrackIndices.size() < 2 ? NO_TRACKS : Util.toArray(selectedTrackIndices);
  }

  private static int getAdaptiveVideoTrackCountForMimeType(TrackGroup group, int[] formatSupport,
      int requiredAdaptiveSupport, String mimeType, int maxVideoWidth, int maxVideoHeight,
      int maxVideoBitrate, List<Integer> selectedTrackIndices) {
    int adaptiveTrackCount = 0;
    for (int i = 0; i < selectedTrackIndices.size(); i++) {
      int trackIndex = selectedTrackIndices.get(i);
      if (isSupportedAdaptiveVideoTrack(group.getFormat(trackIndex), mimeType,
          formatSupport[trackIndex], requiredAdaptiveSupport, maxVideoWidth, maxVideoHeight,
          maxVideoBitrate)) {
        adaptiveTrackCount++;
      }
    }
    return adaptiveTrackCount;
  }

  private static void filterAdaptiveVideoTrackCountForMimeType(TrackGroup group,
      int[] formatSupport, int requiredAdaptiveSupport, String mimeType, int maxVideoWidth,
      int maxVideoHeight, int maxVideoBitrate, List<Integer> selectedTrackIndices) {
    for (int i = selectedTrackIndices.size() - 1; i >= 0; i--) {
      int trackIndex = selectedTrackIndices.get(i);
      if (!isSupportedAdaptiveVideoTrack(group.getFormat(trackIndex), mimeType,
          formatSupport[trackIndex], requiredAdaptiveSupport, maxVideoWidth, maxVideoHeight,
          maxVideoBitrate)) {
        selectedTrackIndices.remove(i);
      }
    }
  }

  private static boolean isSupportedAdaptiveVideoTrack(Format format, String mimeType,
      int formatSupport, int requiredAdaptiveSupport, int maxVideoWidth, int maxVideoHeight,
      int maxVideoBitrate) {
    return isSupported(formatSupport, false) && ((formatSupport & requiredAdaptiveSupport) != 0)
        && (mimeType == null || Util.areEqual(format.sampleMimeType, mimeType))
        && (format.width == Format.NO_VALUE || format.width <= maxVideoWidth)
        && (format.height == Format.NO_VALUE || format.height <= maxVideoHeight)
        && (format.bitrate == Format.NO_VALUE || format.bitrate <= maxVideoBitrate);
  }

  private static TrackSelection selectFixedVideoTrack(TrackGroupArray groups,
      int[][] formatSupport, Parameters params) {
    TrackGroup selectedGroup = null;
    int selectedTrackIndex = 0;
    int selectedTrackScore = 0;
    int selectedBitrate = Format.NO_VALUE;
    int selectedPixelCount = Format.NO_VALUE;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      List<Integer> selectedTrackIndices = getViewportFilteredTrackIndices(trackGroup,
          params.viewportWidth, params.viewportHeight, params.viewportOrientationMayChange);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex],
            params.exceedRendererCapabilitiesIfNecessary)) {
          Format format = trackGroup.getFormat(trackIndex);
          boolean isWithinConstraints = selectedTrackIndices.contains(trackIndex)
              && (format.width == Format.NO_VALUE || format.width <= params.maxVideoWidth)
              && (format.height == Format.NO_VALUE || format.height <= params.maxVideoHeight)
              && (format.bitrate == Format.NO_VALUE || format.bitrate <= params.maxVideoBitrate);
          if (!isWithinConstraints && !params.exceedVideoConstraintsIfNecessary) {
            // Track should not be selected.
            continue;
          }
          int trackScore = isWithinConstraints ? 2 : 1;
          boolean isWithinCapabilities = isSupported(trackFormatSupport[trackIndex], false);
          if (isWithinCapabilities) {
            trackScore += WITHIN_RENDERER_CAPABILITIES_BONUS;
          }
          boolean selectTrack = trackScore > selectedTrackScore;
          if (trackScore == selectedTrackScore) {
            if (params.forceLowestBitrate) {
              // Use bitrate as a tie breaker, preferring the lower bitrate.
              selectTrack = compareFormatValues(format.bitrate, selectedBitrate) < 0;
            } else {
              // Use the pixel count as a tie breaker (or bitrate if pixel counts are tied). If
              // we're within constraints prefer a higher pixel count (or bitrate), else prefer a
              // lower count (or bitrate). If still tied then prefer the first track (i.e. the one
              // that's already selected).
              int formatPixelCount = format.getPixelCount();
              int comparisonResult = formatPixelCount != selectedPixelCount
                  ? compareFormatValues(formatPixelCount, selectedPixelCount)
                  : compareFormatValues(format.bitrate, selectedBitrate);
              selectTrack = isWithinCapabilities && isWithinConstraints
                  ? comparisonResult > 0 : comparisonResult < 0;
            }
          }
          if (selectTrack) {
            selectedGroup = trackGroup;
            selectedTrackIndex = trackIndex;
            selectedTrackScore = trackScore;
            selectedBitrate = format.bitrate;
            selectedPixelCount = format.getPixelCount();
          }
        }
      }
    }
    return selectedGroup == null ? null
        : new FixedTrackSelection(selectedGroup, selectedTrackIndex);
  }

  // Audio track selection implementation.

  /**
   * Called by {@link #selectTracks(RendererCapabilities[], MappedTrackInfo)} to create a {@link
   * TrackSelection} for an audio renderer.
   *
   * @param groups The {@link TrackGroupArray} mapped to the renderer.
   * @param formatSupport The result of {@link RendererCapabilities#supportsFormat} for each mapped
   *     track, indexed by track group index and track index (in that order).
   * @param params The selector's current constraint parameters.
   * @param adaptiveTrackSelectionFactory A factory for generating adaptive track selections, or
   *     null if a fixed track selection is required.
   * @return The {@link TrackSelection} for the renderer, or null if no selection was made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  protected TrackSelection selectAudioTrack(
      TrackGroupArray groups,
      int[][] formatSupport,
      Parameters params,
      TrackSelection.Factory adaptiveTrackSelectionFactory)
      throws ExoPlaybackException {
    int selectedTrackIndex = C.INDEX_UNSET;
    int selectedGroupIndex = C.INDEX_UNSET;
    AudioTrackScore selectedTrackScore = null;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex],
            params.exceedRendererCapabilitiesIfNecessary)) {
          Format format = trackGroup.getFormat(trackIndex);
          AudioTrackScore trackScore =
              new AudioTrackScore(format, params, trackFormatSupport[trackIndex]);
          if (selectedTrackScore == null || trackScore.compareTo(selectedTrackScore) > 0) {
            selectedGroupIndex = groupIndex;
            selectedTrackIndex = trackIndex;
            selectedTrackScore = trackScore;
          }
        }
      }
    }

    if (selectedGroupIndex == C.INDEX_UNSET) {
      return null;
    }

    TrackGroup selectedGroup = groups.get(selectedGroupIndex);
    if (!params.forceLowestBitrate && adaptiveTrackSelectionFactory != null) {
      // If the group of the track with the highest score allows it, try to enable adaptation.
      int[] adaptiveTracks = getAdaptiveAudioTracks(selectedGroup,
          formatSupport[selectedGroupIndex], params.allowMixedMimeAdaptiveness);
      if (adaptiveTracks.length > 0) {
        return adaptiveTrackSelectionFactory.createTrackSelection(selectedGroup,
            adaptiveTracks);
      }
    }
    return new FixedTrackSelection(selectedGroup, selectedTrackIndex);
  }

  private static int[] getAdaptiveAudioTracks(TrackGroup group, int[] formatSupport,
      boolean allowMixedMimeTypes) {
    int selectedConfigurationTrackCount = 0;
    AudioConfigurationTuple selectedConfiguration = null;
    HashSet<AudioConfigurationTuple> seenConfigurationTuples = new HashSet<>();
    for (int i = 0; i < group.length; i++) {
      Format format = group.getFormat(i);
      AudioConfigurationTuple configuration = new AudioConfigurationTuple(
          format.channelCount, format.sampleRate,
          allowMixedMimeTypes ? null : format.sampleMimeType);
      if (seenConfigurationTuples.add(configuration)) {
        int configurationCount = getAdaptiveAudioTrackCount(group, formatSupport, configuration);
        if (configurationCount > selectedConfigurationTrackCount) {
          selectedConfiguration = configuration;
          selectedConfigurationTrackCount = configurationCount;
        }
      }
    }

    if (selectedConfigurationTrackCount > 1) {
      int[] adaptiveIndices = new int[selectedConfigurationTrackCount];
      int index = 0;
      for (int i = 0; i < group.length; i++) {
        if (isSupportedAdaptiveAudioTrack(group.getFormat(i), formatSupport[i],
            selectedConfiguration)) {
          adaptiveIndices[index++] = i;
        }
      }
      return adaptiveIndices;
    }
    return NO_TRACKS;
  }

  private static int getAdaptiveAudioTrackCount(TrackGroup group, int[] formatSupport,
      AudioConfigurationTuple configuration) {
    int count = 0;
    for (int i = 0; i < group.length; i++) {
      if (isSupportedAdaptiveAudioTrack(group.getFormat(i), formatSupport[i], configuration)) {
        count++;
      }
    }
    return count;
  }

  private static boolean isSupportedAdaptiveAudioTrack(Format format, int formatSupport,
      AudioConfigurationTuple configuration) {
    return isSupported(formatSupport, false) && format.channelCount == configuration.channelCount
        && format.sampleRate == configuration.sampleRate
        && (configuration.mimeType == null
        || TextUtils.equals(configuration.mimeType, format.sampleMimeType));
  }

  // Text track selection implementation.

  /**
   * Called by {@link #selectTracks(RendererCapabilities[], MappedTrackInfo)} to create a {@link
   * TrackSelection} for a text renderer.
   *
   * @param groups The {@link TrackGroupArray} mapped to the renderer.
   * @param formatSupport The result of {@link RendererCapabilities#supportsFormat} for each mapped
   *     track, indexed by track group index and track index (in that order).
   * @param params The selector's current constraint parameters.
   * @return The {@link TrackSelection} for the renderer, or null if no selection was made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  protected TrackSelection selectTextTrack(
      TrackGroupArray groups, int[][] formatSupport, Parameters params)
      throws ExoPlaybackException {
    TrackGroup selectedGroup = null;
    int selectedTrackIndex = 0;
    int selectedTrackScore = 0;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex],
            params.exceedRendererCapabilitiesIfNecessary)) {
          Format format = trackGroup.getFormat(trackIndex);
          int maskedSelectionFlags =
              format.selectionFlags & ~params.disabledTextTrackSelectionFlags;
          boolean isDefault = (maskedSelectionFlags & C.SELECTION_FLAG_DEFAULT) != 0;
          boolean isForced = (maskedSelectionFlags & C.SELECTION_FLAG_FORCED) != 0;
          int trackScore;
          boolean preferredLanguageFound = formatHasLanguage(format, params.preferredTextLanguage);
          if (preferredLanguageFound
              || (params.selectUndeterminedTextLanguage && formatHasNoLanguage(format))) {
            if (isDefault) {
              trackScore = 8;
            } else if (!isForced) {
              // Prefer non-forced to forced if a preferred text language has been specified. Where
              // both are provided the non-forced track will usually contain the forced subtitles as
              // a subset.
              trackScore = 6;
            } else {
              trackScore = 4;
            }
            trackScore += preferredLanguageFound ? 1 : 0;
          } else if (isDefault) {
            trackScore = 3;
          } else if (isForced) {
            if (formatHasLanguage(format, params.preferredAudioLanguage)) {
              trackScore = 2;
            } else {
              trackScore = 1;
            }
          } else {
            // Track should not be selected.
            continue;
          }
          if (isSupported(trackFormatSupport[trackIndex], false)) {
            trackScore += WITHIN_RENDERER_CAPABILITIES_BONUS;
          }
          if (trackScore > selectedTrackScore) {
            selectedGroup = trackGroup;
            selectedTrackIndex = trackIndex;
            selectedTrackScore = trackScore;
          }
        }
      }
    }
    return selectedGroup == null ? null
        : new FixedTrackSelection(selectedGroup, selectedTrackIndex);
  }

  // General track selection methods.

  /**
   * Called by {@link #selectTracks(RendererCapabilities[], MappedTrackInfo)} to create a {@link
   * TrackSelection} for a renderer whose type is neither video, audio or text.
   *
   * @param trackType The type of the renderer.
   * @param groups The {@link TrackGroupArray} mapped to the renderer.
   * @param formatSupport The result of {@link RendererCapabilities#supportsFormat} for each mapped
   *     track, indexed by track group index and track index (in that order).
   * @param params The selector's current constraint parameters.
   * @return The {@link TrackSelection} for the renderer, or null if no selection was made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  protected TrackSelection selectOtherTrack(
      int trackType, TrackGroupArray groups, int[][] formatSupport, Parameters params)
      throws ExoPlaybackException {
    TrackGroup selectedGroup = null;
    int selectedTrackIndex = 0;
    int selectedTrackScore = 0;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex],
            params.exceedRendererCapabilitiesIfNecessary)) {
          Format format = trackGroup.getFormat(trackIndex);
          boolean isDefault = (format.selectionFlags & C.SELECTION_FLAG_DEFAULT) != 0;
          int trackScore = isDefault ? 2 : 1;
          if (isSupported(trackFormatSupport[trackIndex], false)) {
            trackScore += WITHIN_RENDERER_CAPABILITIES_BONUS;
          }
          if (trackScore > selectedTrackScore) {
            selectedGroup = trackGroup;
            selectedTrackIndex = trackIndex;
            selectedTrackScore = trackScore;
          }
        }
      }
    }
    return selectedGroup == null ? null
        : new FixedTrackSelection(selectedGroup, selectedTrackIndex);
  }

  // Utility methods.

  /**
   * Determines whether tunneling should be enabled, replacing {@link RendererConfiguration}s in
   * {@code rendererConfigurations} with configurations that enable tunneling on the appropriate
   * renderers if so.
   *
   * @param rendererCapabilities The {@link RendererCapabilities} of the renderers for which {@link
   *     TrackSelection}s are to be generated.
   * @param rendererConfigurations The renderer configurations. Configurations may be replaced with
   *     ones that enable tunneling as a result of this call.
   * @param trackSelections The renderer track selections.
   * @param tunnelingAudioSessionId The audio session id to use when tunneling, or {@link
   *     C#AUDIO_SESSION_ID_UNSET} if tunneling should not be enabled.
   */
  private static void maybeConfigureRenderersForTunneling(
      MappedTrackInfo mappedTrackInfo,
      RendererCapabilities[] rendererCapabilities,
      RendererConfiguration[] rendererConfigurations,
      TrackSelection[] trackSelections,
      int tunnelingAudioSessionId) {
    if (tunnelingAudioSessionId == C.AUDIO_SESSION_ID_UNSET) {
      return;
    }
    // Check whether we can enable tunneling. To enable tunneling we require exactly one audio and
    // one video renderer to support tunneling and have a selection.
    int tunnelingAudioRendererIndex = -1;
    int tunnelingVideoRendererIndex = -1;
    boolean enableTunneling = true;
    for (int i = 0; i < rendererCapabilities.length; i++) {
      int rendererType = rendererCapabilities[i].getTrackType();
      TrackSelection trackSelection = trackSelections[i];
      if ((rendererType == C.TRACK_TYPE_AUDIO || rendererType == C.TRACK_TYPE_VIDEO)
          && trackSelection != null) {
        if (rendererSupportsTunneling(
            mappedTrackInfo.getRendererTrackSupport(i),
            mappedTrackInfo.getTrackGroups(i),
            trackSelection)) {
          if (rendererType == C.TRACK_TYPE_AUDIO) {
            if (tunnelingAudioRendererIndex != -1) {
              enableTunneling = false;
              break;
            } else {
              tunnelingAudioRendererIndex = i;
            }
          } else {
            if (tunnelingVideoRendererIndex != -1) {
              enableTunneling = false;
              break;
            } else {
              tunnelingVideoRendererIndex = i;
            }
          }
        }
      }
    }
    enableTunneling &= tunnelingAudioRendererIndex != -1 && tunnelingVideoRendererIndex != -1;
    if (enableTunneling) {
      RendererConfiguration tunnelingRendererConfiguration =
          new RendererConfiguration(tunnelingAudioSessionId);
      rendererConfigurations[tunnelingAudioRendererIndex] = tunnelingRendererConfiguration;
      rendererConfigurations[tunnelingVideoRendererIndex] = tunnelingRendererConfiguration;
    }
  }

  /**
   * Returns whether a renderer supports tunneling for a {@link TrackSelection}.
   *
   * @param formatSupport The result of {@link RendererCapabilities#supportsFormat} for each track,
   *     indexed by group index and track index (in that order).
   * @param trackGroups The {@link TrackGroupArray}s for the renderer.
   * @param selection The track selection.
   * @return Whether the renderer supports tunneling for the {@link TrackSelection}.
   */
  private static boolean rendererSupportsTunneling(
      int[][] formatSupport, TrackGroupArray trackGroups, TrackSelection selection) {
    if (selection == null) {
      return false;
    }
    int trackGroupIndex = trackGroups.indexOf(selection.getTrackGroup());
    for (int i = 0; i < selection.length(); i++) {
      int trackFormatSupport = formatSupport[trackGroupIndex][selection.getIndexInTrackGroup(i)];
      if ((trackFormatSupport & RendererCapabilities.TUNNELING_SUPPORT_MASK)
          != RendererCapabilities.TUNNELING_SUPPORTED) {
        return false;
      }
    }
    return true;
  }

  /**
   * Compares two format values for order. A known value is considered greater than {@link
   * Format#NO_VALUE}.
   *
   * @param first The first value.
   * @param second The second value.
   * @return A negative integer if the first value is less than the second. Zero if they are equal.
   *     A positive integer if the first value is greater than the second.
   */
  private static int compareFormatValues(int first, int second) {
    return first == Format.NO_VALUE
        ? (second == Format.NO_VALUE ? 0 : -1)
        : (second == Format.NO_VALUE ? 1 : (first - second));
  }

  /**
   * Applies the {@link RendererCapabilities#FORMAT_SUPPORT_MASK} to a value obtained from
   * {@link RendererCapabilities#supportsFormat(Format)}, returning true if the result is
   * {@link RendererCapabilities#FORMAT_HANDLED} or if {@code allowExceedsCapabilities} is set
   * and the result is {@link RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES}.
   *
   * @param formatSupport A value obtained from {@link RendererCapabilities#supportsFormat(Format)}.
   * @param allowExceedsCapabilities Whether to return true if the format support component of the
   *     value is {@link RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES}.
   * @return True if the format support component is {@link RendererCapabilities#FORMAT_HANDLED}, or
   *     if {@code allowExceedsCapabilities} is set and the format support component is
   *     {@link RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES}.
   */
  protected static boolean isSupported(int formatSupport, boolean allowExceedsCapabilities) {
    int maskedSupport = formatSupport & RendererCapabilities.FORMAT_SUPPORT_MASK;
    return maskedSupport == RendererCapabilities.FORMAT_HANDLED || (allowExceedsCapabilities
        && maskedSupport == RendererCapabilities.FORMAT_EXCEEDS_CAPABILITIES);
  }

  /**
   * Returns whether a {@link Format} does not define a language.
   *
   * @param format The {@link Format}.
   * @return Whether the {@link Format} does not define a language.
   */
  protected static boolean formatHasNoLanguage(Format format) {
    return TextUtils.isEmpty(format.language) || formatHasLanguage(format, C.LANGUAGE_UNDETERMINED);
  }

  /**
   * Returns whether a {@link Format} specifies a particular language, or {@code false} if
   * {@code language} is null.
   *
   * @param format The {@link Format}.
   * @param language The language.
   * @return Whether the format specifies the language, or {@code false} if {@code language} is
   *     null.
   */
  protected static boolean formatHasLanguage(Format format, String language) {
    return language != null
        && TextUtils.equals(language, Util.normalizeLanguageCode(format.language));
  }

  private static List<Integer> getViewportFilteredTrackIndices(TrackGroup group, int viewportWidth,
      int viewportHeight, boolean orientationMayChange) {
    // Initially include all indices.
    ArrayList<Integer> selectedTrackIndices = new ArrayList<>(group.length);
    for (int i = 0; i < group.length; i++) {
      selectedTrackIndices.add(i);
    }

    if (viewportWidth == Integer.MAX_VALUE || viewportHeight == Integer.MAX_VALUE) {
      // Viewport dimensions not set. Return the full set of indices.
      return selectedTrackIndices;
    }

    int maxVideoPixelsToRetain = Integer.MAX_VALUE;
    for (int i = 0; i < group.length; i++) {
      Format format = group.getFormat(i);
      // Keep track of the number of pixels of the selected format whose resolution is the
      // smallest to exceed the maximum size at which it can be displayed within the viewport.
      // We'll discard formats of higher resolution.
      if (format.width > 0 && format.height > 0) {
        Point maxVideoSizeInViewport = getMaxVideoSizeInViewport(orientationMayChange,
            viewportWidth, viewportHeight, format.width, format.height);
        int videoPixels = format.width * format.height;
        if (format.width >= (int) (maxVideoSizeInViewport.x * FRACTION_TO_CONSIDER_FULLSCREEN)
            && format.height >= (int) (maxVideoSizeInViewport.y * FRACTION_TO_CONSIDER_FULLSCREEN)
            && videoPixels < maxVideoPixelsToRetain) {
          maxVideoPixelsToRetain = videoPixels;
        }
      }
    }

    // Filter out formats that exceed maxVideoPixelsToRetain. These formats have an unnecessarily
    // high resolution given the size at which the video will be displayed within the viewport. Also
    // filter out formats with unknown dimensions, since we have some whose dimensions are known.
    if (maxVideoPixelsToRetain != Integer.MAX_VALUE) {
      for (int i = selectedTrackIndices.size() - 1; i >= 0; i--) {
        Format format = group.getFormat(selectedTrackIndices.get(i));
        int pixelCount = format.getPixelCount();
        if (pixelCount == Format.NO_VALUE || pixelCount > maxVideoPixelsToRetain) {
          selectedTrackIndices.remove(i);
        }
      }
    }

    return selectedTrackIndices;
  }

  /**
   * Given viewport dimensions and video dimensions, computes the maximum size of the video as it
   * will be rendered to fit inside of the viewport.
   */
  private static Point getMaxVideoSizeInViewport(boolean orientationMayChange, int viewportWidth,
      int viewportHeight, int videoWidth, int videoHeight) {
    if (orientationMayChange && (videoWidth > videoHeight) != (viewportWidth > viewportHeight)) {
      // Rotation is allowed, and the video will be larger in the rotated viewport.
      int tempViewportWidth = viewportWidth;
      viewportWidth = viewportHeight;
      viewportHeight = tempViewportWidth;
    }

    if (videoWidth * viewportHeight >= videoHeight * viewportWidth) {
      // Horizontal letter-boxing along top and bottom.
      return new Point(viewportWidth, Util.ceilDivide(viewportWidth * videoHeight, videoWidth));
    } else {
      // Vertical letter-boxing along edges.
      return new Point(Util.ceilDivide(viewportHeight * videoWidth, videoHeight), viewportHeight);
    }
  }

  /**
   * A representation of how well a track fits with our track selection {@link Parameters}.
   *
   * <p>This is used to rank different audio tracks relatively with each other.
   */
  private static final class AudioTrackScore implements Comparable<AudioTrackScore> {
    private final Parameters parameters;
    private final int withinRendererCapabilitiesScore;
    private final int matchLanguageScore;
    private final int defaultSelectionFlagScore;
    private final int channelCount;
    private final int sampleRate;
    private final int bitrate;

    public AudioTrackScore(Format format, Parameters parameters, int formatSupport) {
      this.parameters = parameters;
      withinRendererCapabilitiesScore = isSupported(formatSupport, false) ? 1 : 0;
      matchLanguageScore = formatHasLanguage(format, parameters.preferredAudioLanguage) ? 1 : 0;
      defaultSelectionFlagScore = (format.selectionFlags & C.SELECTION_FLAG_DEFAULT) != 0 ? 1 : 0;
      channelCount = format.channelCount;
      sampleRate = format.sampleRate;
      bitrate = format.bitrate;
    }

    /**
     * Compares the score of the current track format with another {@link AudioTrackScore}.
     *
     * @param other The other score to compare to.
     * @return A positive integer if this score is better than the other. Zero if they are equal. A
     *     negative integer if this score is worse than the other.
     */
    @Override
    public int compareTo(@NonNull AudioTrackScore other) {
      if (this.withinRendererCapabilitiesScore != other.withinRendererCapabilitiesScore) {
        return compareInts(this.withinRendererCapabilitiesScore,
            other.withinRendererCapabilitiesScore);
      } else if (this.matchLanguageScore != other.matchLanguageScore) {
        return compareInts(this.matchLanguageScore, other.matchLanguageScore);
      } else if (this.defaultSelectionFlagScore != other.defaultSelectionFlagScore) {
        return compareInts(this.defaultSelectionFlagScore, other.defaultSelectionFlagScore);
      } else if (parameters.forceLowestBitrate) {
        return compareInts(other.bitrate, this.bitrate);
      } else {
        // If the format are within renderer capabilities, prefer higher values of channel count,
        // sample rate and bit rate in that order. Otherwise, prefer lower values.
        int resultSign = withinRendererCapabilitiesScore == 1 ? 1 : -1;
        if (this.channelCount != other.channelCount) {
          return resultSign * compareInts(this.channelCount, other.channelCount);
        } else if (this.sampleRate != other.sampleRate) {
          return resultSign * compareInts(this.sampleRate, other.sampleRate);
        }
        return resultSign * compareInts(this.bitrate, other.bitrate);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      AudioTrackScore that = (AudioTrackScore) o;

      return withinRendererCapabilitiesScore == that.withinRendererCapabilitiesScore
          && matchLanguageScore == that.matchLanguageScore
          && defaultSelectionFlagScore == that.defaultSelectionFlagScore
          && channelCount == that.channelCount && sampleRate == that.sampleRate
          && bitrate == that.bitrate;
    }

    @Override
    public int hashCode() {
      int result = withinRendererCapabilitiesScore;
      result = 31 * result + matchLanguageScore;
      result = 31 * result + defaultSelectionFlagScore;
      result = 31 * result + channelCount;
      result = 31 * result + sampleRate;
      result = 31 * result + bitrate;
      return result;
    }
  }

  /**
   * Compares two integers in a safe way and avoiding potential overflow.
   *
   * @param first The first value.
   * @param second The second value.
   * @return A negative integer if the first value is less than the second. Zero if they are equal.
   *     A positive integer if the first value is greater than the second.
   */
  private static int compareInts(int first, int second) {
    return first > second ? 1 : (second > first ? -1 : 0);
  }

  private static final class AudioConfigurationTuple {

    public final int channelCount;
    public final int sampleRate;
    public final String mimeType;

    public AudioConfigurationTuple(int channelCount, int sampleRate, String mimeType) {
      this.channelCount = channelCount;
      this.sampleRate = sampleRate;
      this.mimeType = mimeType;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      AudioConfigurationTuple other = (AudioConfigurationTuple) obj;
      return channelCount == other.channelCount && sampleRate == other.sampleRate
          && TextUtils.equals(mimeType, other.mimeType);
    }

    @Override
    public int hashCode() {
      int result = channelCount;
      result = 31 * result + sampleRate;
      result = 31 * result + (mimeType != null ? mimeType.hashCode() : 0);
      return result;
    }

  }

}
