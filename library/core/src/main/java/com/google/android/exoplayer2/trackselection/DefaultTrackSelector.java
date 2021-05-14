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
import android.text.TextUtils;
import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.FormatSupport;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RendererCapabilities.AdaptiveSupport;
import com.google.android.exoplayer2.RendererCapabilities.Capabilities;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;

/**
 * A default {@link TrackSelector} suitable for most use cases. Track selections are made according
 * to configurable {@link Parameters}, which can be set by calling {@link
 * #setParameters(Parameters)}.
 *
 * <h3>Modifying parameters</h3>
 *
 * To modify only some aspects of the parameters currently used by a selector, it's possible to
 * obtain a {@link ParametersBuilder} initialized with the current {@link Parameters}. The desired
 * modifications can be made on the builder, and the resulting {@link Parameters} can then be built
 * and set on the selector. For example the following code modifies the parameters to restrict video
 * track selections to SD, and to select a German audio track if there is one:
 *
 * <pre>{@code
 * // Build on the current parameters.
 * Parameters currentParameters = trackSelector.getParameters();
 * // Build the resulting parameters.
 * Parameters newParameters = currentParameters
 *     .buildUpon()
 *     .setMaxVideoSizeSd()
 *     .setPreferredAudioLanguage("deu")
 *     .build();
 * // Set the new parameters.
 * trackSelector.setParameters(newParameters);
 * }</pre>
 *
 * Convenience methods and chaining allow this to be written more concisely as:
 *
 * <pre>{@code
 * trackSelector.setParameters(
 *     trackSelector
 *         .buildUponParameters()
 *         .setMaxVideoSizeSd()
 *         .setPreferredAudioLanguage("deu"));
 * }</pre>
 *
 * Selection {@link Parameters} support many different options, some of which are described below.
 *
 * <h3>Selecting specific tracks</h3>
 *
 * Track selection overrides can be used to select specific tracks. To specify an override for a
 * renderer, it's first necessary to obtain the tracks that have been mapped to it:
 *
 * <pre>{@code
 * MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
 * TrackGroupArray rendererTrackGroups = mappedTrackInfo == null ? null
 *     : mappedTrackInfo.getTrackGroups(rendererIndex);
 * }</pre>
 *
 * If {@code rendererTrackGroups} is null then there aren't any currently mapped tracks, and so
 * setting an override isn't possible. Note that a {@link Player.Listener} registered on the player
 * can be used to determine when the current tracks (and therefore the mapping) changes. If {@code
 * rendererTrackGroups} is non-null then an override can be set. The next step is to query the
 * properties of the available tracks to determine the {@code groupIndex} and the {@code
 * trackIndices} within the group it that should be selected. The override can then be specified
 * using {@link ParametersBuilder#setSelectionOverride}:
 *
 * <pre>{@code
 * SelectionOverride selectionOverride = new SelectionOverride(groupIndex, trackIndices);
 * trackSelector.setParameters(
 *     trackSelector
 *         .buildUponParameters()
 *         .setSelectionOverride(rendererIndex, rendererTrackGroups, selectionOverride));
 * }</pre>
 *
 * <h3>Constraint based track selection</h3>
 *
 * Whilst track selection overrides make it possible to select specific tracks, the recommended way
 * of controlling which tracks are selected is by specifying constraints. For example consider the
 * case of wanting to restrict video track selections to SD, and preferring German audio tracks.
 * Track selection overrides could be used to select specific tracks meeting these criteria, however
 * a simpler and more flexible approach is to specify these constraints directly:
 *
 * <pre>{@code
 * trackSelector.setParameters(
 *     trackSelector
 *         .buildUponParameters()
 *         .setMaxVideoSizeSd()
 *         .setPreferredAudioLanguage("deu"));
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
 * <h3>Disabling renderers</h3>
 *
 * Renderers can be disabled using {@link ParametersBuilder#setRendererDisabled}. Disabling a
 * renderer differs from setting a {@code null} override because the renderer is disabled
 * unconditionally, whereas a {@code null} override is applied only when the track groups available
 * to the renderer match the {@link TrackGroupArray} for which it was specified.
 *
 * <h3>Tunneling</h3>
 *
 * Tunneled playback can be enabled in cases where the combination of renderers and selected tracks
 * support it. Tunneled playback is enabled by passing an audio session ID to {@link
 * ParametersBuilder#setTunnelingEnabled(boolean)}.
 */
public class DefaultTrackSelector extends MappingTrackSelector {

  /**
   * A builder for {@link Parameters}. See the {@link Parameters} documentation for explanations of
   * the parameters that can be configured using this builder.
   */
  public static final class ParametersBuilder extends TrackSelectionParameters.Builder {

    // Video
    private int maxVideoWidth;
    private int maxVideoHeight;
    private int maxVideoFrameRate;
    private int maxVideoBitrate;
    private int minVideoWidth;
    private int minVideoHeight;
    private int minVideoFrameRate;
    private int minVideoBitrate;
    private boolean exceedVideoConstraintsIfNecessary;
    private boolean allowVideoMixedMimeTypeAdaptiveness;
    private boolean allowVideoNonSeamlessAdaptiveness;
    private int viewportWidth;
    private int viewportHeight;
    private boolean viewportOrientationMayChange;
    private ImmutableList<String> preferredVideoMimeTypes;
    // Audio
    private int maxAudioChannelCount;
    private int maxAudioBitrate;
    private boolean exceedAudioConstraintsIfNecessary;
    private boolean allowAudioMixedMimeTypeAdaptiveness;
    private boolean allowAudioMixedSampleRateAdaptiveness;
    private boolean allowAudioMixedChannelCountAdaptiveness;
    private ImmutableList<String> preferredAudioMimeTypes;
    // General
    private boolean forceLowestBitrate;
    private boolean forceHighestSupportedBitrate;
    private boolean exceedRendererCapabilitiesIfNecessary;
    private boolean tunnelingEnabled;
    private boolean allowMultipleAdaptiveSelections;

    private final SparseArray<Map<TrackGroupArray, @NullableType SelectionOverride>>
        selectionOverrides;
    private final SparseBooleanArray rendererDisabledFlags;

    /**
     * @deprecated {@link Context} constraints will not be set using this constructor. Use {@link
     *     #ParametersBuilder(Context)} instead.
     */
    @Deprecated
    @SuppressWarnings({"deprecation"})
    public ParametersBuilder() {
      super();
      setInitialValuesWithoutContext();
      selectionOverrides = new SparseArray<>();
      rendererDisabledFlags = new SparseBooleanArray();
    }

    /**
     * Creates a builder with default initial values.
     *
     * @param context Any context.
     */
    public ParametersBuilder(Context context) {
      super(context);
      setInitialValuesWithoutContext();
      selectionOverrides = new SparseArray<>();
      rendererDisabledFlags = new SparseBooleanArray();
      setViewportSizeToPhysicalDisplaySize(context, /* viewportOrientationMayChange= */ true);
    }

    /**
     * @param initialValues The {@link Parameters} from which the initial values of the builder are
     *     obtained.
     */
    private ParametersBuilder(Parameters initialValues) {
      super(initialValues);
      // Video
      maxVideoWidth = initialValues.maxVideoWidth;
      maxVideoHeight = initialValues.maxVideoHeight;
      maxVideoFrameRate = initialValues.maxVideoFrameRate;
      maxVideoBitrate = initialValues.maxVideoBitrate;
      minVideoWidth = initialValues.minVideoWidth;
      minVideoHeight = initialValues.minVideoHeight;
      minVideoFrameRate = initialValues.minVideoFrameRate;
      minVideoBitrate = initialValues.minVideoBitrate;
      exceedVideoConstraintsIfNecessary = initialValues.exceedVideoConstraintsIfNecessary;
      allowVideoMixedMimeTypeAdaptiveness = initialValues.allowVideoMixedMimeTypeAdaptiveness;
      allowVideoNonSeamlessAdaptiveness = initialValues.allowVideoNonSeamlessAdaptiveness;
      viewportWidth = initialValues.viewportWidth;
      viewportHeight = initialValues.viewportHeight;
      viewportOrientationMayChange = initialValues.viewportOrientationMayChange;
      preferredVideoMimeTypes = initialValues.preferredVideoMimeTypes;
      // Audio
      maxAudioChannelCount = initialValues.maxAudioChannelCount;
      maxAudioBitrate = initialValues.maxAudioBitrate;
      exceedAudioConstraintsIfNecessary = initialValues.exceedAudioConstraintsIfNecessary;
      allowAudioMixedMimeTypeAdaptiveness = initialValues.allowAudioMixedMimeTypeAdaptiveness;
      allowAudioMixedSampleRateAdaptiveness = initialValues.allowAudioMixedSampleRateAdaptiveness;
      allowAudioMixedChannelCountAdaptiveness =
          initialValues.allowAudioMixedChannelCountAdaptiveness;
      preferredAudioMimeTypes = initialValues.preferredAudioMimeTypes;
      // General
      forceLowestBitrate = initialValues.forceLowestBitrate;
      forceHighestSupportedBitrate = initialValues.forceHighestSupportedBitrate;
      exceedRendererCapabilitiesIfNecessary = initialValues.exceedRendererCapabilitiesIfNecessary;
      tunnelingEnabled = initialValues.tunnelingEnabled;
      allowMultipleAdaptiveSelections = initialValues.allowMultipleAdaptiveSelections;
      // Overrides
      selectionOverrides = cloneSelectionOverrides(initialValues.selectionOverrides);
      rendererDisabledFlags = initialValues.rendererDisabledFlags.clone();
    }

    // Video

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
     * Sets the maximum allowed video width and height.
     *
     * @param maxVideoWidth Maximum allowed video width in pixels.
     * @param maxVideoHeight Maximum allowed video height in pixels.
     * @return This builder.
     */
    public ParametersBuilder setMaxVideoSize(int maxVideoWidth, int maxVideoHeight) {
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
    public ParametersBuilder setMaxVideoFrameRate(int maxVideoFrameRate) {
      this.maxVideoFrameRate = maxVideoFrameRate;
      return this;
    }

    /**
     * Sets the maximum allowed video bitrate.
     *
     * @param maxVideoBitrate Maximum allowed video bitrate in bits per second.
     * @return This builder.
     */
    public ParametersBuilder setMaxVideoBitrate(int maxVideoBitrate) {
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
    public ParametersBuilder setMinVideoSize(int minVideoWidth, int minVideoHeight) {
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
    public ParametersBuilder setMinVideoFrameRate(int minVideoFrameRate) {
      this.minVideoFrameRate = minVideoFrameRate;
      return this;
    }

    /**
     * Sets the minimum allowed video bitrate.
     *
     * @param minVideoBitrate Minimum allowed video bitrate in bits per second.
     * @return This builder.
     */
    public ParametersBuilder setMinVideoBitrate(int minVideoBitrate) {
      this.minVideoBitrate = minVideoBitrate;
      return this;
    }

    /**
     * Sets whether to exceed the {@link #setMaxVideoBitrate}, {@link #setMaxVideoSize(int, int)}
     * and {@link #setMaxVideoFrameRate} constraints when no selection can be made otherwise.
     *
     * @param exceedVideoConstraintsIfNecessary Whether to exceed video constraints when no
     *     selection can be made otherwise.
     * @return This builder.
     */
    public ParametersBuilder setExceedVideoConstraintsIfNecessary(
        boolean exceedVideoConstraintsIfNecessary) {
      this.exceedVideoConstraintsIfNecessary = exceedVideoConstraintsIfNecessary;
      return this;
    }

    /**
     * Sets whether to allow adaptive video selections containing mixed MIME types.
     *
     * <p>Adaptations between different MIME types may not be completely seamless, in which case
     * {@link #setAllowVideoNonSeamlessAdaptiveness(boolean)} also needs to be {@code true} for
     * mixed MIME type selections to be made.
     *
     * @param allowVideoMixedMimeTypeAdaptiveness Whether to allow adaptive video selections
     *     containing mixed MIME types.
     * @return This builder.
     */
    public ParametersBuilder setAllowVideoMixedMimeTypeAdaptiveness(
        boolean allowVideoMixedMimeTypeAdaptiveness) {
      this.allowVideoMixedMimeTypeAdaptiveness = allowVideoMixedMimeTypeAdaptiveness;
      return this;
    }

    /**
     * Sets whether to allow adaptive video selections where adaptation may not be completely
     * seamless.
     *
     * @param allowVideoNonSeamlessAdaptiveness Whether to allow adaptive video selections where
     *     adaptation may not be completely seamless.
     * @return This builder.
     */
    public ParametersBuilder setAllowVideoNonSeamlessAdaptiveness(
        boolean allowVideoNonSeamlessAdaptiveness) {
      this.allowVideoNonSeamlessAdaptiveness = allowVideoNonSeamlessAdaptiveness;
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
    public ParametersBuilder setViewportSizeToPhysicalDisplaySize(
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
    public ParametersBuilder clearViewportSizeConstraints() {
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
    public ParametersBuilder setViewportSize(
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
    public ParametersBuilder setPreferredVideoMimeType(@Nullable String mimeType) {
      return mimeType == null ? setPreferredVideoMimeTypes() : setPreferredVideoMimeTypes(mimeType);
    }

    /**
     * Sets the preferred sample MIME types for video tracks.
     *
     * @param mimeTypes The preferred MIME types for video tracks in order of preference, or an
     *     empty list for no preference.
     * @return This builder.
     */
    public ParametersBuilder setPreferredVideoMimeTypes(String... mimeTypes) {
      preferredVideoMimeTypes = ImmutableList.copyOf(mimeTypes);
      return this;
    }

    // Audio

    @Override
    public ParametersBuilder setPreferredAudioLanguage(@Nullable String preferredAudioLanguage) {
      super.setPreferredAudioLanguage(preferredAudioLanguage);
      return this;
    }

    @Override
    public ParametersBuilder setPreferredAudioLanguages(String... preferredAudioLanguages) {
      super.setPreferredAudioLanguages(preferredAudioLanguages);
      return this;
    }

    @Override
    public ParametersBuilder setPreferredAudioRoleFlags(@C.RoleFlags int preferredAudioRoleFlags) {
      super.setPreferredAudioRoleFlags(preferredAudioRoleFlags);
      return this;
    }

    /**
     * Sets the maximum allowed audio channel count.
     *
     * @param maxAudioChannelCount Maximum allowed audio channel count.
     * @return This builder.
     */
    public ParametersBuilder setMaxAudioChannelCount(int maxAudioChannelCount) {
      this.maxAudioChannelCount = maxAudioChannelCount;
      return this;
    }

    /**
     * Sets the maximum allowed audio bitrate.
     *
     * @param maxAudioBitrate Maximum allowed audio bitrate in bits per second.
     * @return This builder.
     */
    public ParametersBuilder setMaxAudioBitrate(int maxAudioBitrate) {
      this.maxAudioBitrate = maxAudioBitrate;
      return this;
    }

    /**
     * Sets whether to exceed the {@link #setMaxAudioChannelCount(int)} and {@link
     * #setMaxAudioBitrate(int)} constraints when no selection can be made otherwise.
     *
     * @param exceedAudioConstraintsIfNecessary Whether to exceed audio constraints when no
     *     selection can be made otherwise.
     * @return This builder.
     */
    public ParametersBuilder setExceedAudioConstraintsIfNecessary(
        boolean exceedAudioConstraintsIfNecessary) {
      this.exceedAudioConstraintsIfNecessary = exceedAudioConstraintsIfNecessary;
      return this;
    }

    /**
     * Sets whether to allow adaptive audio selections containing mixed MIME types.
     *
     * <p>Adaptations between different MIME types may not be completely seamless.
     *
     * @param allowAudioMixedMimeTypeAdaptiveness Whether to allow adaptive audio selections
     *     containing mixed MIME types.
     * @return This builder.
     */
    public ParametersBuilder setAllowAudioMixedMimeTypeAdaptiveness(
        boolean allowAudioMixedMimeTypeAdaptiveness) {
      this.allowAudioMixedMimeTypeAdaptiveness = allowAudioMixedMimeTypeAdaptiveness;
      return this;
    }

    /**
     * Sets whether to allow adaptive audio selections containing mixed sample rates.
     *
     * <p>Adaptations between different sample rates may not be completely seamless.
     *
     * @param allowAudioMixedSampleRateAdaptiveness Whether to allow adaptive audio selections
     *     containing mixed sample rates.
     * @return This builder.
     */
    public ParametersBuilder setAllowAudioMixedSampleRateAdaptiveness(
        boolean allowAudioMixedSampleRateAdaptiveness) {
      this.allowAudioMixedSampleRateAdaptiveness = allowAudioMixedSampleRateAdaptiveness;
      return this;
    }

    /**
     * Sets whether to allow adaptive audio selections containing mixed channel counts.
     *
     * <p>Adaptations between different channel counts may not be completely seamless.
     *
     * @param allowAudioMixedChannelCountAdaptiveness Whether to allow adaptive audio selections
     *     containing mixed channel counts.
     * @return This builder.
     */
    public ParametersBuilder setAllowAudioMixedChannelCountAdaptiveness(
        boolean allowAudioMixedChannelCountAdaptiveness) {
      this.allowAudioMixedChannelCountAdaptiveness = allowAudioMixedChannelCountAdaptiveness;
      return this;
    }

    /**
     * Sets the preferred sample MIME type for audio tracks.
     *
     * @param mimeType The preferred MIME type for audio tracks, or {@code null} to clear a
     *     previously set preference.
     * @return This builder.
     */
    public ParametersBuilder setPreferredAudioMimeType(@Nullable String mimeType) {
      return mimeType == null ? setPreferredAudioMimeTypes() : setPreferredAudioMimeTypes(mimeType);
    }

    /**
     * Sets the preferred sample MIME types for audio tracks.
     *
     * @param mimeTypes The preferred MIME types for audio tracks in order of preference, or an
     *     empty list for no preference.
     * @return This builder.
     */
    public ParametersBuilder setPreferredAudioMimeTypes(String... mimeTypes) {
      preferredAudioMimeTypes = ImmutableList.copyOf(mimeTypes);
      return this;
    }

    // Text

    @Override
    public ParametersBuilder setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(
        Context context) {
      super.setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(context);
      return this;
    }

    @Override
    public ParametersBuilder setPreferredTextLanguage(@Nullable String preferredTextLanguage) {
      super.setPreferredTextLanguage(preferredTextLanguage);
      return this;
    }

    @Override
    public ParametersBuilder setPreferredTextLanguages(String... preferredTextLanguages) {
      super.setPreferredTextLanguages(preferredTextLanguages);
      return this;
    }

    @Override
    public ParametersBuilder setPreferredTextRoleFlags(@C.RoleFlags int preferredTextRoleFlags) {
      super.setPreferredTextRoleFlags(preferredTextRoleFlags);
      return this;
    }

    @Override
    public ParametersBuilder setSelectUndeterminedTextLanguage(
        boolean selectUndeterminedTextLanguage) {
      super.setSelectUndeterminedTextLanguage(selectUndeterminedTextLanguage);
      return this;
    }

    @Override
    public ParametersBuilder setDisabledTextTrackSelectionFlags(
        @C.SelectionFlags int disabledTextTrackSelectionFlags) {
      super.setDisabledTextTrackSelectionFlags(disabledTextTrackSelectionFlags);
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
    public ParametersBuilder setForceLowestBitrate(boolean forceLowestBitrate) {
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
    public ParametersBuilder setForceHighestSupportedBitrate(boolean forceHighestSupportedBitrate) {
      this.forceHighestSupportedBitrate = forceHighestSupportedBitrate;
      return this;
    }

    /**
     * Sets whether to exceed renderer capabilities when no selection can be made otherwise.
     *
     * <p>This parameter applies when all of the tracks available for a renderer exceed the
     * renderer's reported capabilities. If the parameter is {@code true} then the lowest quality
     * track will still be selected. Playback may succeed if the renderer has under-reported its
     * true capabilities. If {@code false} then no track will be selected.
     *
     * @param exceedRendererCapabilitiesIfNecessary Whether to exceed renderer capabilities when no
     *     selection can be made otherwise.
     * @return This builder.
     */
    public ParametersBuilder setExceedRendererCapabilitiesIfNecessary(
        boolean exceedRendererCapabilitiesIfNecessary) {
      this.exceedRendererCapabilitiesIfNecessary = exceedRendererCapabilitiesIfNecessary;
      return this;
    }

    /**
     * Sets whether to enable tunneling if possible. Tunneling will only be enabled if it's
     * supported by the audio and video renderers for the selected tracks.
     *
     * @param tunnelingEnabled Whether to enable tunneling if possible.
     * @return This builder.
     */
    public ParametersBuilder setTunnelingEnabled(boolean tunnelingEnabled) {
      this.tunnelingEnabled = tunnelingEnabled;
      return this;
    }

    /**
     * Sets whether multiple adaptive selections with more than one track are allowed.
     *
     * @param allowMultipleAdaptiveSelections Whether multiple adaptive selections are allowed.
     * @return This builder.
     */
    public ParametersBuilder setAllowMultipleAdaptiveSelections(
        boolean allowMultipleAdaptiveSelections) {
      this.allowMultipleAdaptiveSelections = allowMultipleAdaptiveSelections;
      return this;
    }

    // Overrides

    /**
     * Sets whether the renderer at the specified index is disabled. Disabling a renderer prevents
     * the selector from selecting any tracks for it.
     *
     * @param rendererIndex The renderer index.
     * @param disabled Whether the renderer is disabled.
     * @return This builder.
     */
    public final ParametersBuilder setRendererDisabled(int rendererIndex, boolean disabled) {
      if (rendererDisabledFlags.get(rendererIndex) == disabled) {
        // The disabled flag is unchanged.
        return this;
      }
      // Only true values are placed in the array to make it easier to check for equality.
      if (disabled) {
        rendererDisabledFlags.put(rendererIndex, true);
      } else {
        rendererDisabledFlags.delete(rendererIndex);
      }
      return this;
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
     * disabling the renderer using {@link #setRendererDisabled(int, boolean)} because the renderer
     * is disabled conditionally on the {@link TrackGroupArray} mapped to it, where-as {@link
     * #setRendererDisabled(int, boolean)} disables the renderer unconditionally.
     *
     * <p>To remove overrides use {@link #clearSelectionOverride(int, TrackGroupArray)}, {@link
     * #clearSelectionOverrides(int)} or {@link #clearSelectionOverrides()}.
     *
     * @param rendererIndex The renderer index.
     * @param groups The {@link TrackGroupArray} for which the override should be applied.
     * @param override The override.
     * @return This builder.
     */
    public final ParametersBuilder setSelectionOverride(
        int rendererIndex, TrackGroupArray groups, @Nullable SelectionOverride override) {
      Map<TrackGroupArray, @NullableType SelectionOverride> overrides =
          selectionOverrides.get(rendererIndex);
      if (overrides == null) {
        overrides = new HashMap<>();
        selectionOverrides.put(rendererIndex, overrides);
      }
      if (overrides.containsKey(groups) && Util.areEqual(overrides.get(groups), override)) {
        // The override is unchanged.
        return this;
      }
      overrides.put(groups, override);
      return this;
    }

    /**
     * Clears a track selection override for the specified renderer and {@link TrackGroupArray}.
     *
     * @param rendererIndex The renderer index.
     * @param groups The {@link TrackGroupArray} for which the override should be cleared.
     * @return This builder.
     */
    public final ParametersBuilder clearSelectionOverride(
        int rendererIndex, TrackGroupArray groups) {
      Map<TrackGroupArray, @NullableType SelectionOverride> overrides =
          selectionOverrides.get(rendererIndex);
      if (overrides == null || !overrides.containsKey(groups)) {
        // Nothing to clear.
        return this;
      }
      overrides.remove(groups);
      if (overrides.isEmpty()) {
        selectionOverrides.remove(rendererIndex);
      }
      return this;
    }

    /**
     * Clears all track selection overrides for the specified renderer.
     *
     * @param rendererIndex The renderer index.
     * @return This builder.
     */
    public final ParametersBuilder clearSelectionOverrides(int rendererIndex) {
      Map<TrackGroupArray, @NullableType SelectionOverride> overrides =
          selectionOverrides.get(rendererIndex);
      if (overrides == null || overrides.isEmpty()) {
        // Nothing to clear.
        return this;
      }
      selectionOverrides.remove(rendererIndex);
      return this;
    }

    /**
     * Clears all track selection overrides for all renderers.
     *
     * @return This builder.
     */
    public final ParametersBuilder clearSelectionOverrides() {
      if (selectionOverrides.size() == 0) {
        // Nothing to clear.
        return this;
      }
      selectionOverrides.clear();
      return this;
    }

    /** Builds a {@link Parameters} instance with the selected values. */
    public Parameters build() {
      return new Parameters(
          // Video
          maxVideoWidth,
          maxVideoHeight,
          maxVideoFrameRate,
          maxVideoBitrate,
          minVideoWidth,
          minVideoHeight,
          minVideoFrameRate,
          minVideoBitrate,
          exceedVideoConstraintsIfNecessary,
          allowVideoMixedMimeTypeAdaptiveness,
          allowVideoNonSeamlessAdaptiveness,
          viewportWidth,
          viewportHeight,
          viewportOrientationMayChange,
          preferredVideoMimeTypes,
          // Audio
          preferredAudioLanguages,
          preferredAudioRoleFlags,
          maxAudioChannelCount,
          maxAudioBitrate,
          exceedAudioConstraintsIfNecessary,
          allowAudioMixedMimeTypeAdaptiveness,
          allowAudioMixedSampleRateAdaptiveness,
          allowAudioMixedChannelCountAdaptiveness,
          preferredAudioMimeTypes,
          // Text
          preferredTextLanguages,
          preferredTextRoleFlags,
          selectUndeterminedTextLanguage,
          disabledTextTrackSelectionFlags,
          // General
          forceLowestBitrate,
          forceHighestSupportedBitrate,
          exceedRendererCapabilitiesIfNecessary,
          tunnelingEnabled,
          allowMultipleAdaptiveSelections,
          selectionOverrides,
          rendererDisabledFlags);
    }

    @EnsuresNonNull({"preferredVideoMimeTypes", "preferredAudioMimeTypes"})
    private void setInitialValuesWithoutContext(@UnderInitialization ParametersBuilder this) {
      // Video
      maxVideoWidth = Integer.MAX_VALUE;
      maxVideoHeight = Integer.MAX_VALUE;
      maxVideoFrameRate = Integer.MAX_VALUE;
      maxVideoBitrate = Integer.MAX_VALUE;
      exceedVideoConstraintsIfNecessary = true;
      allowVideoMixedMimeTypeAdaptiveness = false;
      allowVideoNonSeamlessAdaptiveness = true;
      viewportWidth = Integer.MAX_VALUE;
      viewportHeight = Integer.MAX_VALUE;
      viewportOrientationMayChange = true;
      preferredVideoMimeTypes = ImmutableList.of();
      // Audio
      maxAudioChannelCount = Integer.MAX_VALUE;
      maxAudioBitrate = Integer.MAX_VALUE;
      exceedAudioConstraintsIfNecessary = true;
      allowAudioMixedMimeTypeAdaptiveness = false;
      allowAudioMixedSampleRateAdaptiveness = false;
      allowAudioMixedChannelCountAdaptiveness = false;
      preferredAudioMimeTypes = ImmutableList.of();
      // General
      forceLowestBitrate = false;
      forceHighestSupportedBitrate = false;
      exceedRendererCapabilitiesIfNecessary = true;
      tunnelingEnabled = false;
      allowMultipleAdaptiveSelections = true;
    }

    private static SparseArray<Map<TrackGroupArray, @NullableType SelectionOverride>>
        cloneSelectionOverrides(
            SparseArray<Map<TrackGroupArray, @NullableType SelectionOverride>> selectionOverrides) {
      SparseArray<Map<TrackGroupArray, @NullableType SelectionOverride>> clone =
          new SparseArray<>();
      for (int i = 0; i < selectionOverrides.size(); i++) {
        clone.put(selectionOverrides.keyAt(i), new HashMap<>(selectionOverrides.valueAt(i)));
      }
      return clone;
    }
  }

  /**
   * Extends {@link TrackSelectionParameters} by adding fields that are specific to {@link
   * DefaultTrackSelector}.
   */
  public static final class Parameters extends TrackSelectionParameters {

    /**
     * An instance with default values, except those obtained from the {@link Context}.
     *
     * <p>If possible, use {@link #getDefaults(Context)} instead.
     *
     * <p>This instance will not have the following settings:
     *
     * <ul>
     *   <li>{@link ParametersBuilder#setViewportSizeToPhysicalDisplaySize(Context, boolean)
     *       Viewport constraints} configured for the primary display.
     *   <li>{@link
     *       ParametersBuilder#setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(Context)
     *       Preferred text language and role flags} configured to the accessibility settings of
     *       {@link android.view.accessibility.CaptioningManager}.
     * </ul>
     */
    @SuppressWarnings("deprecation")
    public static final Parameters DEFAULT_WITHOUT_CONTEXT = new ParametersBuilder().build();

    /** Returns an instance configured with default values. */
    public static Parameters getDefaults(Context context) {
      return new ParametersBuilder(context).build();
    }

    // Video
    /**
     * Maximum allowed video width in pixels. The default value is {@link Integer#MAX_VALUE} (i.e.
     * no constraint).
     *
     * <p>To constrain adaptive video track selections to be suitable for a given viewport (the
     * region of the display within which video will be played), use ({@link #viewportWidth}, {@link
     * #viewportHeight} and {@link #viewportOrientationMayChange}) instead.
     */
    public final int maxVideoWidth;
    /**
     * Maximum allowed video height in pixels. The default value is {@link Integer#MAX_VALUE} (i.e.
     * no constraint).
     *
     * <p>To constrain adaptive video track selections to be suitable for a given viewport (the
     * region of the display within which video will be played), use ({@link #viewportWidth}, {@link
     * #viewportHeight} and {@link #viewportOrientationMayChange}) instead.
     */
    public final int maxVideoHeight;
    /**
     * Maximum allowed video frame rate in hertz. The default value is {@link Integer#MAX_VALUE}
     * (i.e. no constraint).
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
     * Minimum allowed video bitrate in bits per second. The default value is 0 (i.e. no
     * constraint).
     */
    public final int minVideoBitrate;
    /**
     * Whether to exceed the {@link #maxVideoWidth}, {@link #maxVideoHeight} and {@link
     * #maxVideoBitrate} constraints when no selection can be made otherwise. The default value is
     * {@code true}.
     */
    public final boolean exceedVideoConstraintsIfNecessary;
    /**
     * Whether to allow adaptive video selections containing mixed MIME types. Adaptations between
     * different MIME types may not be completely seamless, in which case {@link
     * #allowVideoNonSeamlessAdaptiveness} also needs to be {@code true} for mixed MIME type
     * selections to be made. The default value is {@code false}.
     */
    public final boolean allowVideoMixedMimeTypeAdaptiveness;
    /**
     * Whether to allow adaptive video selections where adaptation may not be completely seamless.
     * The default value is {@code true}.
     */
    public final boolean allowVideoNonSeamlessAdaptiveness;
    /**
     * Viewport width in pixels. Constrains video track selections for adaptive content so that only
     * tracks suitable for the viewport are selected. The default value is the physical width of the
     * primary display, in pixels.
     */
    public final int viewportWidth;
    /**
     * Viewport height in pixels. Constrains video track selections for adaptive content so that
     * only tracks suitable for the viewport are selected. The default value is the physical height
     * of the primary display, in pixels.
     */
    public final int viewportHeight;
    /**
     * Whether the viewport orientation may change during playback. Constrains video track
     * selections for adaptive content so that only tracks suitable for the viewport are selected.
     * The default value is {@code true}.
     */
    public final boolean viewportOrientationMayChange;
    /**
     * The preferred sample MIME types for video tracks in order of preference, or an empty list for
     * no preference. The default is an empty list.
     */
    public final ImmutableList<String> preferredVideoMimeTypes;
    // Audio
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
     * Whether to exceed the {@link #maxAudioChannelCount} and {@link #maxAudioBitrate} constraints
     * when no selection can be made otherwise. The default value is {@code true}.
     */
    public final boolean exceedAudioConstraintsIfNecessary;
    /**
     * Whether to allow adaptive audio selections containing mixed MIME types. Adaptations between
     * different MIME types may not be completely seamless. The default value is {@code false}.
     */
    public final boolean allowAudioMixedMimeTypeAdaptiveness;
    /**
     * Whether to allow adaptive audio selections containing mixed sample rates. Adaptations between
     * different sample rates may not be completely seamless. The default value is {@code false}.
     */
    public final boolean allowAudioMixedSampleRateAdaptiveness;
    /**
     * Whether to allow adaptive audio selections containing mixed channel counts. Adaptations
     * between different channel counts may not be completely seamless. The default value is {@code
     * false}.
     */
    public final boolean allowAudioMixedChannelCountAdaptiveness;
    /**
     * The preferred sample MIME types for audio tracks in order of preference, or an empty list for
     * no preference. The default is an empty list.
     */
    public final ImmutableList<String> preferredAudioMimeTypes;
    // General
    /**
     * Whether to force selection of the single lowest bitrate audio and video tracks that comply
     * with all other constraints. The default value is {@code false}.
     */
    public final boolean forceLowestBitrate;
    /**
     * Whether to force selection of the highest bitrate audio and video tracks that comply with all
     * other constraints. The default value is {@code false}.
     */
    public final boolean forceHighestSupportedBitrate;
    /**
     * Whether to exceed renderer capabilities when no selection can be made otherwise.
     *
     * <p>This parameter applies when all of the tracks available for a renderer exceed the
     * renderer's reported capabilities. If the parameter is {@code true} then the lowest quality
     * track will still be selected. Playback may succeed if the renderer has under-reported its
     * true capabilities. If {@code false} then no track will be selected. The default value is
     * {@code true}.
     */
    public final boolean exceedRendererCapabilitiesIfNecessary;
    /** Whether to enable tunneling if possible. */
    public final boolean tunnelingEnabled;
    /**
     * Whether multiple adaptive selections with more than one track are allowed. The default value
     * is {@code true}.
     *
     * <p>Note that tracks are only eligible for adaptation if they define a bitrate, the renderers
     * support the tracks and allow adaptation between them, and they are not excluded based on
     * other track selection parameters.
     */
    public final boolean allowMultipleAdaptiveSelections;

    // Overrides
    private final SparseArray<Map<TrackGroupArray, @NullableType SelectionOverride>>
        selectionOverrides;
    private final SparseBooleanArray rendererDisabledFlags;

    /* package */ Parameters(
        // Video
        int maxVideoWidth,
        int maxVideoHeight,
        int maxVideoFrameRate,
        int maxVideoBitrate,
        int minVideoWidth,
        int minVideoHeight,
        int minVideoFrameRate,
        int minVideoBitrate,
        boolean exceedVideoConstraintsIfNecessary,
        boolean allowVideoMixedMimeTypeAdaptiveness,
        boolean allowVideoNonSeamlessAdaptiveness,
        int viewportWidth,
        int viewportHeight,
        boolean viewportOrientationMayChange,
        ImmutableList<String> preferredVideoMimeTypes,
        // Audio
        ImmutableList<String> preferredAudioLanguages,
        @C.RoleFlags int preferredAudioRoleFlags,
        int maxAudioChannelCount,
        int maxAudioBitrate,
        boolean exceedAudioConstraintsIfNecessary,
        boolean allowAudioMixedMimeTypeAdaptiveness,
        boolean allowAudioMixedSampleRateAdaptiveness,
        boolean allowAudioMixedChannelCountAdaptiveness,
        ImmutableList<String> preferredAudioMimeTypes,
        // Text
        ImmutableList<String> preferredTextLanguages,
        @C.RoleFlags int preferredTextRoleFlags,
        boolean selectUndeterminedTextLanguage,
        @C.SelectionFlags int disabledTextTrackSelectionFlags,
        // General
        boolean forceLowestBitrate,
        boolean forceHighestSupportedBitrate,
        boolean exceedRendererCapabilitiesIfNecessary,
        boolean tunnelingEnabled,
        boolean allowMultipleAdaptiveSelections,
        // Overrides
        SparseArray<Map<TrackGroupArray, @NullableType SelectionOverride>> selectionOverrides,
        SparseBooleanArray rendererDisabledFlags) {
      super(
          preferredAudioLanguages,
          preferredAudioRoleFlags,
          preferredTextLanguages,
          preferredTextRoleFlags,
          selectUndeterminedTextLanguage,
          disabledTextTrackSelectionFlags);
      // Video
      this.maxVideoWidth = maxVideoWidth;
      this.maxVideoHeight = maxVideoHeight;
      this.maxVideoFrameRate = maxVideoFrameRate;
      this.maxVideoBitrate = maxVideoBitrate;
      this.minVideoWidth = minVideoWidth;
      this.minVideoHeight = minVideoHeight;
      this.minVideoFrameRate = minVideoFrameRate;
      this.minVideoBitrate = minVideoBitrate;
      this.exceedVideoConstraintsIfNecessary = exceedVideoConstraintsIfNecessary;
      this.allowVideoMixedMimeTypeAdaptiveness = allowVideoMixedMimeTypeAdaptiveness;
      this.allowVideoNonSeamlessAdaptiveness = allowVideoNonSeamlessAdaptiveness;
      this.viewportWidth = viewportWidth;
      this.viewportHeight = viewportHeight;
      this.viewportOrientationMayChange = viewportOrientationMayChange;
      this.preferredVideoMimeTypes = preferredVideoMimeTypes;
      // Audio
      this.maxAudioChannelCount = maxAudioChannelCount;
      this.maxAudioBitrate = maxAudioBitrate;
      this.exceedAudioConstraintsIfNecessary = exceedAudioConstraintsIfNecessary;
      this.allowAudioMixedMimeTypeAdaptiveness = allowAudioMixedMimeTypeAdaptiveness;
      this.allowAudioMixedSampleRateAdaptiveness = allowAudioMixedSampleRateAdaptiveness;
      this.allowAudioMixedChannelCountAdaptiveness = allowAudioMixedChannelCountAdaptiveness;
      this.preferredAudioMimeTypes = preferredAudioMimeTypes;
      // General
      this.forceLowestBitrate = forceLowestBitrate;
      this.forceHighestSupportedBitrate = forceHighestSupportedBitrate;
      this.exceedRendererCapabilitiesIfNecessary = exceedRendererCapabilitiesIfNecessary;
      this.tunnelingEnabled = tunnelingEnabled;
      this.allowMultipleAdaptiveSelections = allowMultipleAdaptiveSelections;
      // Overrides
      this.selectionOverrides = selectionOverrides;
      this.rendererDisabledFlags = rendererDisabledFlags;
    }

    /* package */ Parameters(Parcel in) {
      super(in);
      // Video
      this.maxVideoWidth = in.readInt();
      this.maxVideoHeight = in.readInt();
      this.maxVideoFrameRate = in.readInt();
      this.maxVideoBitrate = in.readInt();
      this.minVideoWidth = in.readInt();
      this.minVideoHeight = in.readInt();
      this.minVideoFrameRate = in.readInt();
      this.minVideoBitrate = in.readInt();
      this.exceedVideoConstraintsIfNecessary = Util.readBoolean(in);
      this.allowVideoMixedMimeTypeAdaptiveness = Util.readBoolean(in);
      this.allowVideoNonSeamlessAdaptiveness = Util.readBoolean(in);
      this.viewportWidth = in.readInt();
      this.viewportHeight = in.readInt();
      this.viewportOrientationMayChange = Util.readBoolean(in);
      ArrayList<String> preferredVideoMimeTypes = new ArrayList<>();
      in.readList(preferredVideoMimeTypes, /* loader= */ null);
      this.preferredVideoMimeTypes = ImmutableList.copyOf(preferredVideoMimeTypes);
      // Audio
      this.maxAudioChannelCount = in.readInt();
      this.maxAudioBitrate = in.readInt();
      this.exceedAudioConstraintsIfNecessary = Util.readBoolean(in);
      this.allowAudioMixedMimeTypeAdaptiveness = Util.readBoolean(in);
      this.allowAudioMixedSampleRateAdaptiveness = Util.readBoolean(in);
      this.allowAudioMixedChannelCountAdaptiveness = Util.readBoolean(in);
      ArrayList<String> preferredAudioMimeTypes = new ArrayList<>();
      in.readList(preferredAudioMimeTypes, /* loader= */ null);
      this.preferredAudioMimeTypes = ImmutableList.copyOf(preferredAudioMimeTypes);
      // General
      this.forceLowestBitrate = Util.readBoolean(in);
      this.forceHighestSupportedBitrate = Util.readBoolean(in);
      this.exceedRendererCapabilitiesIfNecessary = Util.readBoolean(in);
      this.tunnelingEnabled = Util.readBoolean(in);
      this.allowMultipleAdaptiveSelections = Util.readBoolean(in);
      // Overrides
      this.selectionOverrides = readSelectionOverrides(in);
      this.rendererDisabledFlags = Util.castNonNull(in.readSparseBooleanArray());
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
     * Returns whether there is an override for the specified renderer and {@link TrackGroupArray}.
     *
     * @param rendererIndex The renderer index.
     * @param groups The {@link TrackGroupArray}.
     * @return Whether there is an override.
     */
    public final boolean hasSelectionOverride(int rendererIndex, TrackGroupArray groups) {
      Map<TrackGroupArray, @NullableType SelectionOverride> overrides =
          selectionOverrides.get(rendererIndex);
      return overrides != null && overrides.containsKey(groups);
    }

    /**
     * Returns the override for the specified renderer and {@link TrackGroupArray}.
     *
     * @param rendererIndex The renderer index.
     * @param groups The {@link TrackGroupArray}.
     * @return The override, or null if no override exists.
     */
    @Nullable
    public final SelectionOverride getSelectionOverride(int rendererIndex, TrackGroupArray groups) {
      Map<TrackGroupArray, @NullableType SelectionOverride> overrides =
          selectionOverrides.get(rendererIndex);
      return overrides != null ? overrides.get(groups) : null;
    }

    /** Creates a new {@link ParametersBuilder}, copying the initial values from this instance. */
    @Override
    public ParametersBuilder buildUpon() {
      return new ParametersBuilder(this);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      Parameters other = (Parameters) obj;
      return super.equals(obj)
          // Video
          && maxVideoWidth == other.maxVideoWidth
          && maxVideoHeight == other.maxVideoHeight
          && maxVideoFrameRate == other.maxVideoFrameRate
          && maxVideoBitrate == other.maxVideoBitrate
          && minVideoWidth == other.minVideoWidth
          && minVideoHeight == other.minVideoHeight
          && minVideoFrameRate == other.minVideoFrameRate
          && minVideoBitrate == other.minVideoBitrate
          && exceedVideoConstraintsIfNecessary == other.exceedVideoConstraintsIfNecessary
          && allowVideoMixedMimeTypeAdaptiveness == other.allowVideoMixedMimeTypeAdaptiveness
          && allowVideoNonSeamlessAdaptiveness == other.allowVideoNonSeamlessAdaptiveness
          && viewportOrientationMayChange == other.viewportOrientationMayChange
          && viewportWidth == other.viewportWidth
          && viewportHeight == other.viewportHeight
          && preferredVideoMimeTypes.equals(other.preferredVideoMimeTypes)
          // Audio
          && maxAudioChannelCount == other.maxAudioChannelCount
          && maxAudioBitrate == other.maxAudioBitrate
          && exceedAudioConstraintsIfNecessary == other.exceedAudioConstraintsIfNecessary
          && allowAudioMixedMimeTypeAdaptiveness == other.allowAudioMixedMimeTypeAdaptiveness
          && allowAudioMixedSampleRateAdaptiveness == other.allowAudioMixedSampleRateAdaptiveness
          && allowAudioMixedChannelCountAdaptiveness
              == other.allowAudioMixedChannelCountAdaptiveness
          && preferredAudioMimeTypes.equals(other.preferredAudioMimeTypes)
          // General
          && forceLowestBitrate == other.forceLowestBitrate
          && forceHighestSupportedBitrate == other.forceHighestSupportedBitrate
          && exceedRendererCapabilitiesIfNecessary == other.exceedRendererCapabilitiesIfNecessary
          && tunnelingEnabled == other.tunnelingEnabled
          && allowMultipleAdaptiveSelections == other.allowMultipleAdaptiveSelections
          // Overrides
          && areRendererDisabledFlagsEqual(rendererDisabledFlags, other.rendererDisabledFlags)
          && areSelectionOverridesEqual(selectionOverrides, other.selectionOverrides);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      // Video
      result = 31 * result + maxVideoWidth;
      result = 31 * result + maxVideoHeight;
      result = 31 * result + maxVideoFrameRate;
      result = 31 * result + maxVideoBitrate;
      result = 31 * result + minVideoWidth;
      result = 31 * result + minVideoHeight;
      result = 31 * result + minVideoFrameRate;
      result = 31 * result + minVideoBitrate;
      result = 31 * result + (exceedVideoConstraintsIfNecessary ? 1 : 0);
      result = 31 * result + (allowVideoMixedMimeTypeAdaptiveness ? 1 : 0);
      result = 31 * result + (allowVideoNonSeamlessAdaptiveness ? 1 : 0);
      result = 31 * result + (viewportOrientationMayChange ? 1 : 0);
      result = 31 * result + viewportWidth;
      result = 31 * result + viewportHeight;
      result = 31 * result + preferredVideoMimeTypes.hashCode();
      // Audio
      result = 31 * result + maxAudioChannelCount;
      result = 31 * result + maxAudioBitrate;
      result = 31 * result + (exceedAudioConstraintsIfNecessary ? 1 : 0);
      result = 31 * result + (allowAudioMixedMimeTypeAdaptiveness ? 1 : 0);
      result = 31 * result + (allowAudioMixedSampleRateAdaptiveness ? 1 : 0);
      result = 31 * result + (allowAudioMixedChannelCountAdaptiveness ? 1 : 0);
      result = 31 * result + preferredAudioMimeTypes.hashCode();
      // General
      result = 31 * result + (forceLowestBitrate ? 1 : 0);
      result = 31 * result + (forceHighestSupportedBitrate ? 1 : 0);
      result = 31 * result + (exceedRendererCapabilitiesIfNecessary ? 1 : 0);
      result = 31 * result + (tunnelingEnabled ? 1 : 0);
      result = 31 * result + (allowMultipleAdaptiveSelections ? 1 : 0);
      // Overrides (omitted from hashCode).
      return result;
    }

    // Parcelable implementation.

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      super.writeToParcel(dest, flags);
      // Video
      dest.writeInt(maxVideoWidth);
      dest.writeInt(maxVideoHeight);
      dest.writeInt(maxVideoFrameRate);
      dest.writeInt(maxVideoBitrate);
      dest.writeInt(minVideoWidth);
      dest.writeInt(minVideoHeight);
      dest.writeInt(minVideoFrameRate);
      dest.writeInt(minVideoBitrate);
      Util.writeBoolean(dest, exceedVideoConstraintsIfNecessary);
      Util.writeBoolean(dest, allowVideoMixedMimeTypeAdaptiveness);
      Util.writeBoolean(dest, allowVideoNonSeamlessAdaptiveness);
      dest.writeInt(viewportWidth);
      dest.writeInt(viewportHeight);
      Util.writeBoolean(dest, viewportOrientationMayChange);
      dest.writeList(preferredVideoMimeTypes);
      // Audio
      dest.writeInt(maxAudioChannelCount);
      dest.writeInt(maxAudioBitrate);
      Util.writeBoolean(dest, exceedAudioConstraintsIfNecessary);
      Util.writeBoolean(dest, allowAudioMixedMimeTypeAdaptiveness);
      Util.writeBoolean(dest, allowAudioMixedSampleRateAdaptiveness);
      Util.writeBoolean(dest, allowAudioMixedChannelCountAdaptiveness);
      dest.writeList(preferredAudioMimeTypes);
      // General
      Util.writeBoolean(dest, forceLowestBitrate);
      Util.writeBoolean(dest, forceHighestSupportedBitrate);
      Util.writeBoolean(dest, exceedRendererCapabilitiesIfNecessary);
      Util.writeBoolean(dest, tunnelingEnabled);
      Util.writeBoolean(dest, allowMultipleAdaptiveSelections);
      // Overrides
      writeSelectionOverridesToParcel(dest, selectionOverrides);
      dest.writeSparseBooleanArray(rendererDisabledFlags);
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

    // Static utility methods.

    private static SparseArray<Map<TrackGroupArray, @NullableType SelectionOverride>>
        readSelectionOverrides(Parcel in) {
      int renderersWithOverridesCount = in.readInt();
      SparseArray<Map<TrackGroupArray, @NullableType SelectionOverride>> selectionOverrides =
          new SparseArray<>(renderersWithOverridesCount);
      for (int i = 0; i < renderersWithOverridesCount; i++) {
        int rendererIndex = in.readInt();
        int overrideCount = in.readInt();
        Map<TrackGroupArray, @NullableType SelectionOverride> overrides =
            new HashMap<>(overrideCount);
        for (int j = 0; j < overrideCount; j++) {
          TrackGroupArray trackGroups =
              Assertions.checkNotNull(in.readParcelable(TrackGroupArray.class.getClassLoader()));
          @Nullable
          SelectionOverride override = in.readParcelable(SelectionOverride.class.getClassLoader());
          overrides.put(trackGroups, override);
        }
        selectionOverrides.put(rendererIndex, overrides);
      }
      return selectionOverrides;
    }

    private static void writeSelectionOverridesToParcel(
        Parcel dest,
        SparseArray<Map<TrackGroupArray, @NullableType SelectionOverride>> selectionOverrides) {
      int renderersWithOverridesCount = selectionOverrides.size();
      dest.writeInt(renderersWithOverridesCount);
      for (int i = 0; i < renderersWithOverridesCount; i++) {
        int rendererIndex = selectionOverrides.keyAt(i);
        Map<TrackGroupArray, @NullableType SelectionOverride> overrides =
            selectionOverrides.valueAt(i);
        int overrideCount = overrides.size();
        dest.writeInt(rendererIndex);
        dest.writeInt(overrideCount);
        for (Map.Entry<TrackGroupArray, @NullableType SelectionOverride> override :
            overrides.entrySet()) {
          dest.writeParcelable(override.getKey(), /* parcelableFlags= */ 0);
          dest.writeParcelable(override.getValue(), /* parcelableFlags= */ 0);
        }
      }
    }

    private static boolean areRendererDisabledFlagsEqual(
        SparseBooleanArray first, SparseBooleanArray second) {
      int firstSize = first.size();
      if (second.size() != firstSize) {
        return false;
      }
      // Only true values are put into rendererDisabledFlags, so we don't need to compare values.
      for (int indexInFirst = 0; indexInFirst < firstSize; indexInFirst++) {
        if (second.indexOfKey(first.keyAt(indexInFirst)) < 0) {
          return false;
        }
      }
      return true;
    }

    private static boolean areSelectionOverridesEqual(
        SparseArray<Map<TrackGroupArray, @NullableType SelectionOverride>> first,
        SparseArray<Map<TrackGroupArray, @NullableType SelectionOverride>> second) {
      int firstSize = first.size();
      if (second.size() != firstSize) {
        return false;
      }
      for (int indexInFirst = 0; indexInFirst < firstSize; indexInFirst++) {
        int indexInSecond = second.indexOfKey(first.keyAt(indexInFirst));
        if (indexInSecond < 0
            || !areSelectionOverridesEqual(
                first.valueAt(indexInFirst), second.valueAt(indexInSecond))) {
          return false;
        }
      }
      return true;
    }

    private static boolean areSelectionOverridesEqual(
        Map<TrackGroupArray, @NullableType SelectionOverride> first,
        Map<TrackGroupArray, @NullableType SelectionOverride> second) {
      int firstSize = first.size();
      if (second.size() != firstSize) {
        return false;
      }
      for (Map.Entry<TrackGroupArray, @NullableType SelectionOverride> firstEntry :
          first.entrySet()) {
        TrackGroupArray key = firstEntry.getKey();
        if (!second.containsKey(key) || !Util.areEqual(firstEntry.getValue(), second.get(key))) {
          return false;
        }
      }
      return true;
    }
  }

  /** A track selection override. */
  public static final class SelectionOverride implements Parcelable {

    public final int groupIndex;
    public final int[] tracks;
    public final int length;
    public final int type;

    /**
     * @param groupIndex The overriding track group index.
     * @param tracks The overriding track indices within the track group.
     */
    public SelectionOverride(int groupIndex, int... tracks) {
      this(groupIndex, tracks, TrackSelection.TYPE_UNSET);
    }

    /**
     * @param groupIndex The overriding track group index.
     * @param tracks The overriding track indices within the track group.
     * @param type The type that will be returned from {@link TrackSelection#getType()}.
     */
    public SelectionOverride(int groupIndex, int[] tracks, int type) {
      this.groupIndex = groupIndex;
      this.tracks = Arrays.copyOf(tracks, tracks.length);
      this.length = tracks.length;
      this.type = type;
      Arrays.sort(this.tracks);
    }

    /* package */ SelectionOverride(Parcel in) {
      groupIndex = in.readInt();
      length = in.readByte();
      tracks = new int[length];
      in.readIntArray(tracks);
      type = in.readInt();
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

    @Override
    public int hashCode() {
      int hash = 31 * groupIndex + Arrays.hashCode(tracks);
      return 31 * hash + type;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      SelectionOverride other = (SelectionOverride) obj;
      return groupIndex == other.groupIndex
          && Arrays.equals(tracks, other.tracks)
          && type == other.type;
    }

    // Parcelable implementation.

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeInt(groupIndex);
      dest.writeInt(tracks.length);
      dest.writeIntArray(tracks);
      dest.writeInt(type);
    }

    public static final Parcelable.Creator<SelectionOverride> CREATOR =
        new Parcelable.Creator<SelectionOverride>() {

          @Override
          public SelectionOverride createFromParcel(Parcel in) {
            return new SelectionOverride(in);
          }

          @Override
          public SelectionOverride[] newArray(int size) {
            return new SelectionOverride[size];
          }
        };
  }

  /**
   * If a dimension (i.e. width or height) of a video is greater or equal to this fraction of the
   * corresponding viewport dimension, then the video is considered as filling the viewport (in that
   * dimension).
   */
  private static final float FRACTION_TO_CONSIDER_FULLSCREEN = 0.98f;

  private static final int[] NO_TRACKS = new int[0];
  /** Ordering of two format values. A known value is considered greater than Format#NO_VALUE. */
  private static final Ordering<Integer> FORMAT_VALUE_ORDERING =
      Ordering.from(
          (first, second) ->
              first == Format.NO_VALUE
                  ? (second == Format.NO_VALUE ? 0 : -1)
                  : (second == Format.NO_VALUE ? 1 : (first - second)));
  /** Ordering where all elements are equal. */
  private static final Ordering<Integer> NO_ORDER = Ordering.from((first, second) -> 0);

  private final ExoTrackSelection.Factory trackSelectionFactory;
  private final AtomicReference<Parameters> parametersReference;

  /** @deprecated Use {@link #DefaultTrackSelector(Context)} instead. */
  @Deprecated
  public DefaultTrackSelector() {
    this(Parameters.DEFAULT_WITHOUT_CONTEXT, new AdaptiveTrackSelection.Factory());
  }

  /** @deprecated Use {@link #DefaultTrackSelector(Context, ExoTrackSelection.Factory)}. */
  @Deprecated
  public DefaultTrackSelector(ExoTrackSelection.Factory trackSelectionFactory) {
    this(Parameters.DEFAULT_WITHOUT_CONTEXT, trackSelectionFactory);
  }

  /** @param context Any {@link Context}. */
  public DefaultTrackSelector(Context context) {
    this(context, new AdaptiveTrackSelection.Factory());
  }

  /**
   * @param context Any {@link Context}.
   * @param trackSelectionFactory A factory for {@link ExoTrackSelection}s.
   */
  public DefaultTrackSelector(Context context, ExoTrackSelection.Factory trackSelectionFactory) {
    this(Parameters.getDefaults(context), trackSelectionFactory);
  }

  /**
   * @param parameters Initial {@link Parameters}.
   * @param trackSelectionFactory A factory for {@link ExoTrackSelection}s.
   */
  public DefaultTrackSelector(
      Parameters parameters, ExoTrackSelection.Factory trackSelectionFactory) {
    this.trackSelectionFactory = trackSelectionFactory;
    parametersReference = new AtomicReference<>(parameters);
  }

  /**
   * Atomically sets the provided parameters for track selection.
   *
   * @param parameters The parameters for track selection.
   */
  public void setParameters(Parameters parameters) {
    Assertions.checkNotNull(parameters);
    if (!parametersReference.getAndSet(parameters).equals(parameters)) {
      invalidate();
    }
  }

  /**
   * Atomically sets the provided parameters for track selection.
   *
   * @param parametersBuilder A builder from which to obtain the parameters for track selection.
   */
  public void setParameters(ParametersBuilder parametersBuilder) {
    setParameters(parametersBuilder.build());
  }

  /**
   * Gets the current selection parameters.
   *
   * @return The current selection parameters.
   */
  public Parameters getParameters() {
    return parametersReference.get();
  }

  /** Returns a new {@link ParametersBuilder} initialized with the current selection parameters. */
  public ParametersBuilder buildUponParameters() {
    return getParameters().buildUpon();
  }

  // MappingTrackSelector implementation.

  @Override
  protected final Pair<@NullableType RendererConfiguration[], @NullableType ExoTrackSelection[]>
      selectTracks(
          MappedTrackInfo mappedTrackInfo,
          @Capabilities int[][][] rendererFormatSupports,
          @AdaptiveSupport int[] rendererMixedMimeTypeAdaptationSupports,
          MediaPeriodId mediaPeriodId,
          Timeline timeline)
          throws ExoPlaybackException {
    Parameters params = parametersReference.get();
    int rendererCount = mappedTrackInfo.getRendererCount();
    ExoTrackSelection.@NullableType Definition[] definitions =
        selectAllTracks(
            mappedTrackInfo,
            rendererFormatSupports,
            rendererMixedMimeTypeAdaptationSupports,
            params);

    // Apply track disabling and overriding.
    for (int i = 0; i < rendererCount; i++) {
      if (params.getRendererDisabled(i)) {
        definitions[i] = null;
        continue;
      }
      TrackGroupArray rendererTrackGroups = mappedTrackInfo.getTrackGroups(i);
      if (params.hasSelectionOverride(i, rendererTrackGroups)) {
        SelectionOverride override = params.getSelectionOverride(i, rendererTrackGroups);
        definitions[i] =
            override == null
                ? null
                : new ExoTrackSelection.Definition(
                    rendererTrackGroups.get(override.groupIndex), override.tracks, override.type);
      }
    }

    @NullableType
    ExoTrackSelection[] rendererTrackSelections =
        trackSelectionFactory.createTrackSelections(
            definitions, getBandwidthMeter(), mediaPeriodId, timeline);

    // Initialize the renderer configurations to the default configuration for all renderers with
    // selections, and null otherwise.
    @NullableType
    RendererConfiguration[] rendererConfigurations = new RendererConfiguration[rendererCount];
    for (int i = 0; i < rendererCount; i++) {
      boolean forceRendererDisabled = params.getRendererDisabled(i);
      boolean rendererEnabled =
          !forceRendererDisabled
              && (mappedTrackInfo.getRendererType(i) == C.TRACK_TYPE_NONE
                  || rendererTrackSelections[i] != null);
      rendererConfigurations[i] = rendererEnabled ? RendererConfiguration.DEFAULT : null;
    }

    // Configure audio and video renderers to use tunneling if appropriate.
    if (params.tunnelingEnabled) {
      maybeConfigureRenderersForTunneling(
          mappedTrackInfo, rendererFormatSupports, rendererConfigurations, rendererTrackSelections);
    }

    return Pair.create(rendererConfigurations, rendererTrackSelections);
  }

  // Track selection prior to overrides and disabled flags being applied.

  /**
   * Called from {@link #selectTracks(MappedTrackInfo, int[][][], int[], MediaPeriodId, Timeline)}
   * to make a track selection for each renderer, prior to overrides and disabled flags being
   * applied.
   *
   * <p>The implementation should not account for overrides and disabled flags. Track selections
   * generated by this method will be overridden to account for these properties.
   *
   * @param mappedTrackInfo Mapped track information.
   * @param rendererFormatSupports The {@link Capabilities} for each mapped track, indexed by
   *     renderer, track group and track (in that order).
   * @param rendererMixedMimeTypeAdaptationSupports The {@link AdaptiveSupport} for mixed MIME type
   *     adaptation for the renderer.
   * @return The {@link ExoTrackSelection.Definition}s for the renderers. A null entry indicates no
   *     selection was made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  protected ExoTrackSelection.@NullableType Definition[] selectAllTracks(
      MappedTrackInfo mappedTrackInfo,
      @Capabilities int[][][] rendererFormatSupports,
      @AdaptiveSupport int[] rendererMixedMimeTypeAdaptationSupports,
      Parameters params)
      throws ExoPlaybackException {
    int rendererCount = mappedTrackInfo.getRendererCount();
    ExoTrackSelection.@NullableType Definition[] definitions =
        new ExoTrackSelection.Definition[rendererCount];

    boolean seenVideoRendererWithMappedTracks = false;
    boolean selectedVideoTracks = false;
    for (int i = 0; i < rendererCount; i++) {
      if (C.TRACK_TYPE_VIDEO == mappedTrackInfo.getRendererType(i)) {
        if (!selectedVideoTracks) {
          definitions[i] =
              selectVideoTrack(
                  mappedTrackInfo.getTrackGroups(i),
                  rendererFormatSupports[i],
                  rendererMixedMimeTypeAdaptationSupports[i],
                  params,
                  /* enableAdaptiveTrackSelection= */ true);
          selectedVideoTracks = definitions[i] != null;
        }
        seenVideoRendererWithMappedTracks |= mappedTrackInfo.getTrackGroups(i).length > 0;
      }
    }

    @Nullable AudioTrackScore selectedAudioTrackScore = null;
    @Nullable String selectedAudioLanguage = null;
    int selectedAudioRendererIndex = C.INDEX_UNSET;
    for (int i = 0; i < rendererCount; i++) {
      if (C.TRACK_TYPE_AUDIO == mappedTrackInfo.getRendererType(i)) {
        boolean enableAdaptiveTrackSelection =
            params.allowMultipleAdaptiveSelections || !seenVideoRendererWithMappedTracks;
        @Nullable
        Pair<ExoTrackSelection.Definition, AudioTrackScore> audioSelection =
            selectAudioTrack(
                mappedTrackInfo.getTrackGroups(i),
                rendererFormatSupports[i],
                rendererMixedMimeTypeAdaptationSupports[i],
                params,
                enableAdaptiveTrackSelection);
        if (audioSelection != null
            && (selectedAudioTrackScore == null
                || audioSelection.second.compareTo(selectedAudioTrackScore) > 0)) {
          if (selectedAudioRendererIndex != C.INDEX_UNSET) {
            // We've already made a selection for another audio renderer, but it had a lower
            // score. Clear the selection for that renderer.
            definitions[selectedAudioRendererIndex] = null;
          }
          ExoTrackSelection.Definition definition = audioSelection.first;
          definitions[i] = definition;
          // We assume that audio tracks in the same group have matching language.
          selectedAudioLanguage = definition.group.getFormat(definition.tracks[0]).language;
          selectedAudioTrackScore = audioSelection.second;
          selectedAudioRendererIndex = i;
        }
      }
    }

    @Nullable TextTrackScore selectedTextTrackScore = null;
    int selectedTextRendererIndex = C.INDEX_UNSET;
    for (int i = 0; i < rendererCount; i++) {
      int trackType = mappedTrackInfo.getRendererType(i);
      switch (trackType) {
        case C.TRACK_TYPE_VIDEO:
        case C.TRACK_TYPE_AUDIO:
          // Already done. Do nothing.
          break;
        case C.TRACK_TYPE_TEXT:
          @Nullable
          Pair<ExoTrackSelection.Definition, TextTrackScore> textSelection =
              selectTextTrack(
                  mappedTrackInfo.getTrackGroups(i),
                  rendererFormatSupports[i],
                  params,
                  selectedAudioLanguage);
          if (textSelection != null
              && (selectedTextTrackScore == null
                  || textSelection.second.compareTo(selectedTextTrackScore) > 0)) {
            if (selectedTextRendererIndex != C.INDEX_UNSET) {
              // We've already made a selection for another text renderer, but it had a lower score.
              // Clear the selection for that renderer.
              definitions[selectedTextRendererIndex] = null;
            }
            definitions[i] = textSelection.first;
            selectedTextTrackScore = textSelection.second;
            selectedTextRendererIndex = i;
          }
          break;
        default:
          definitions[i] =
              selectOtherTrack(
                  trackType, mappedTrackInfo.getTrackGroups(i), rendererFormatSupports[i], params);
          break;
      }
    }

    return definitions;
  }

  // Video track selection implementation.

  /**
   * Called by {@link #selectAllTracks(MappedTrackInfo, int[][][], int[], Parameters)} to create a
   * {@link ExoTrackSelection} for a video renderer.
   *
   * @param groups The {@link TrackGroupArray} mapped to the renderer.
   * @param formatSupport The {@link Capabilities} for each mapped track, indexed by track group and
   *     track (in that order).
   * @param mixedMimeTypeAdaptationSupports The {@link AdaptiveSupport} for mixed MIME type
   *     adaptation for the renderer.
   * @param params The selector's current constraint parameters.
   * @param enableAdaptiveTrackSelection Whether adaptive track selection is allowed.
   * @return The {@link ExoTrackSelection.Definition} for the renderer, or null if no selection was
   *     made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  @Nullable
  protected ExoTrackSelection.Definition selectVideoTrack(
      TrackGroupArray groups,
      @Capabilities int[][] formatSupport,
      @AdaptiveSupport int mixedMimeTypeAdaptationSupports,
      Parameters params,
      boolean enableAdaptiveTrackSelection)
      throws ExoPlaybackException {
    ExoTrackSelection.Definition definition = null;
    if (!params.forceHighestSupportedBitrate
        && !params.forceLowestBitrate
        && enableAdaptiveTrackSelection) {
      definition =
          selectAdaptiveVideoTrack(groups, formatSupport, mixedMimeTypeAdaptationSupports, params);
    }
    if (definition == null) {
      definition = selectFixedVideoTrack(groups, formatSupport, params);
    }
    return definition;
  }

  @Nullable
  private static ExoTrackSelection.Definition selectAdaptiveVideoTrack(
      TrackGroupArray groups,
      @Capabilities int[][] formatSupport,
      @AdaptiveSupport int mixedMimeTypeAdaptationSupports,
      Parameters params) {
    int requiredAdaptiveSupport =
        params.allowVideoNonSeamlessAdaptiveness
            ? (RendererCapabilities.ADAPTIVE_NOT_SEAMLESS | RendererCapabilities.ADAPTIVE_SEAMLESS)
            : RendererCapabilities.ADAPTIVE_SEAMLESS;
    boolean allowMixedMimeTypes =
        params.allowVideoMixedMimeTypeAdaptiveness
            && (mixedMimeTypeAdaptationSupports & requiredAdaptiveSupport) != 0;
    for (int i = 0; i < groups.length; i++) {
      TrackGroup group = groups.get(i);
      int[] adaptiveTracks =
          getAdaptiveVideoTracksForGroup(
              group,
              formatSupport[i],
              allowMixedMimeTypes,
              requiredAdaptiveSupport,
              params.maxVideoWidth,
              params.maxVideoHeight,
              params.maxVideoFrameRate,
              params.maxVideoBitrate,
              params.minVideoWidth,
              params.minVideoHeight,
              params.minVideoFrameRate,
              params.minVideoBitrate,
              params.viewportWidth,
              params.viewportHeight,
              params.viewportOrientationMayChange);
      if (adaptiveTracks.length > 0) {
        return new ExoTrackSelection.Definition(group, adaptiveTracks);
      }
    }
    return null;
  }

  private static int[] getAdaptiveVideoTracksForGroup(
      TrackGroup group,
      @Capabilities int[] formatSupport,
      boolean allowMixedMimeTypes,
      int requiredAdaptiveSupport,
      int maxVideoWidth,
      int maxVideoHeight,
      int maxVideoFrameRate,
      int maxVideoBitrate,
      int minVideoWidth,
      int minVideoHeight,
      int minVideoFrameRate,
      int minVideoBitrate,
      int viewportWidth,
      int viewportHeight,
      boolean viewportOrientationMayChange) {
    if (group.length < 2) {
      return NO_TRACKS;
    }

    List<Integer> selectedTrackIndices =
        getViewportFilteredTrackIndices(
            group, viewportWidth, viewportHeight, viewportOrientationMayChange);
    if (selectedTrackIndices.size() < 2) {
      return NO_TRACKS;
    }

    String selectedMimeType = null;
    if (!allowMixedMimeTypes) {
      // Select the mime type for which we have the most adaptive tracks.
      HashSet<@NullableType String> seenMimeTypes = new HashSet<>();
      int selectedMimeTypeTrackCount = 0;
      for (int i = 0; i < selectedTrackIndices.size(); i++) {
        int trackIndex = selectedTrackIndices.get(i);
        String sampleMimeType = group.getFormat(trackIndex).sampleMimeType;
        if (seenMimeTypes.add(sampleMimeType)) {
          int countForMimeType =
              getAdaptiveVideoTrackCountForMimeType(
                  group,
                  formatSupport,
                  requiredAdaptiveSupport,
                  sampleMimeType,
                  maxVideoWidth,
                  maxVideoHeight,
                  maxVideoFrameRate,
                  maxVideoBitrate,
                  minVideoWidth,
                  minVideoHeight,
                  minVideoFrameRate,
                  minVideoBitrate,
                  selectedTrackIndices);
          if (countForMimeType > selectedMimeTypeTrackCount) {
            selectedMimeType = sampleMimeType;
            selectedMimeTypeTrackCount = countForMimeType;
          }
        }
      }
    }

    // Filter by the selected mime type.
    filterAdaptiveVideoTrackCountForMimeType(
        group,
        formatSupport,
        requiredAdaptiveSupport,
        selectedMimeType,
        maxVideoWidth,
        maxVideoHeight,
        maxVideoFrameRate,
        maxVideoBitrate,
        minVideoWidth,
        minVideoHeight,
        minVideoFrameRate,
        minVideoBitrate,
        selectedTrackIndices);

    return selectedTrackIndices.size() < 2 ? NO_TRACKS : Ints.toArray(selectedTrackIndices);
  }

  private static int getAdaptiveVideoTrackCountForMimeType(
      TrackGroup group,
      @Capabilities int[] formatSupport,
      int requiredAdaptiveSupport,
      @Nullable String mimeType,
      int maxVideoWidth,
      int maxVideoHeight,
      int maxVideoFrameRate,
      int maxVideoBitrate,
      int minVideoWidth,
      int minVideoHeight,
      int minVideoFrameRate,
      int minVideoBitrate,
      List<Integer> selectedTrackIndices) {
    int adaptiveTrackCount = 0;
    for (int i = 0; i < selectedTrackIndices.size(); i++) {
      int trackIndex = selectedTrackIndices.get(i);
      if (isSupportedAdaptiveVideoTrack(
          group.getFormat(trackIndex),
          mimeType,
          formatSupport[trackIndex],
          requiredAdaptiveSupport,
          maxVideoWidth,
          maxVideoHeight,
          maxVideoFrameRate,
          maxVideoBitrate,
          minVideoWidth,
          minVideoHeight,
          minVideoFrameRate,
          minVideoBitrate)) {
        adaptiveTrackCount++;
      }
    }
    return adaptiveTrackCount;
  }

  private static void filterAdaptiveVideoTrackCountForMimeType(
      TrackGroup group,
      @Capabilities int[] formatSupport,
      int requiredAdaptiveSupport,
      @Nullable String mimeType,
      int maxVideoWidth,
      int maxVideoHeight,
      int maxVideoFrameRate,
      int maxVideoBitrate,
      int minVideoWidth,
      int minVideoHeight,
      int minVideoFrameRate,
      int minVideoBitrate,
      List<Integer> selectedTrackIndices) {
    for (int i = selectedTrackIndices.size() - 1; i >= 0; i--) {
      int trackIndex = selectedTrackIndices.get(i);
      if (!isSupportedAdaptiveVideoTrack(
          group.getFormat(trackIndex),
          mimeType,
          formatSupport[trackIndex],
          requiredAdaptiveSupport,
          maxVideoWidth,
          maxVideoHeight,
          maxVideoFrameRate,
          maxVideoBitrate,
          minVideoWidth,
          minVideoHeight,
          minVideoFrameRate,
          minVideoBitrate)) {
        selectedTrackIndices.remove(i);
      }
    }
  }

  private static boolean isSupportedAdaptiveVideoTrack(
      Format format,
      @Nullable String mimeType,
      @Capabilities int formatSupport,
      int requiredAdaptiveSupport,
      int maxVideoWidth,
      int maxVideoHeight,
      int maxVideoFrameRate,
      int maxVideoBitrate,
      int minVideoWidth,
      int minVideoHeight,
      int minVideoFrameRate,
      int minVideoBitrate) {
    if ((format.roleFlags & C.ROLE_FLAG_TRICK_PLAY) != 0) {
      // Ignore trick-play tracks for now.
      return false;
    }
    return isSupported(formatSupport, /* allowExceedsCapabilities= */ false)
        && ((formatSupport & requiredAdaptiveSupport) != 0)
        && (mimeType == null || Util.areEqual(format.sampleMimeType, mimeType))
        && (format.width == Format.NO_VALUE
            || (minVideoWidth <= format.width && format.width <= maxVideoWidth))
        && (format.height == Format.NO_VALUE
            || (minVideoHeight <= format.height && format.height <= maxVideoHeight))
        && (format.frameRate == Format.NO_VALUE
            || (minVideoFrameRate <= format.frameRate && format.frameRate <= maxVideoFrameRate))
        && format.bitrate != Format.NO_VALUE
        && minVideoBitrate <= format.bitrate
        && format.bitrate <= maxVideoBitrate;
  }

  @Nullable
  private static ExoTrackSelection.Definition selectFixedVideoTrack(
      TrackGroupArray groups, @Capabilities int[][] formatSupport, Parameters params) {
    int selectedTrackIndex = C.INDEX_UNSET;
    @Nullable TrackGroup selectedGroup = null;
    @Nullable VideoTrackScore selectedTrackScore = null;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      List<Integer> viewportFilteredTrackIndices =
          getViewportFilteredTrackIndices(
              trackGroup,
              params.viewportWidth,
              params.viewportHeight,
              params.viewportOrientationMayChange);
      @Capabilities int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        Format format = trackGroup.getFormat(trackIndex);
        if ((format.roleFlags & C.ROLE_FLAG_TRICK_PLAY) != 0) {
          // Ignore trick-play tracks for now.
          continue;
        }
        if (isSupported(
            trackFormatSupport[trackIndex], params.exceedRendererCapabilitiesIfNecessary)) {
          VideoTrackScore trackScore =
              new VideoTrackScore(
                  format,
                  params,
                  trackFormatSupport[trackIndex],
                  viewportFilteredTrackIndices.contains(trackIndex));
          if (!trackScore.isWithinMaxConstraints && !params.exceedVideoConstraintsIfNecessary) {
            // Track should not be selected.
            continue;
          }
          if (selectedTrackScore == null || trackScore.compareTo(selectedTrackScore) > 0) {
            selectedGroup = trackGroup;
            selectedTrackIndex = trackIndex;
            selectedTrackScore = trackScore;
          }
        }
      }
    }

    return selectedGroup == null
        ? null
        : new ExoTrackSelection.Definition(selectedGroup, selectedTrackIndex);
  }

  // Audio track selection implementation.

  /**
   * Called by {@link #selectAllTracks(MappedTrackInfo, int[][][], int[], Parameters)} to create a
   * {@link ExoTrackSelection} for an audio renderer.
   *
   * @param groups The {@link TrackGroupArray} mapped to the renderer.
   * @param formatSupport The {@link Capabilities} for each mapped track, indexed by track group and
   *     track (in that order).
   * @param mixedMimeTypeAdaptationSupports The {@link AdaptiveSupport} for mixed MIME type
   *     adaptation for the renderer.
   * @param params The selector's current constraint parameters.
   * @param enableAdaptiveTrackSelection Whether adaptive track selection is allowed.
   * @return The {@link ExoTrackSelection.Definition} and corresponding {@link AudioTrackScore}, or
   *     null if no selection was made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  @SuppressWarnings("unused")
  @Nullable
  protected Pair<ExoTrackSelection.Definition, AudioTrackScore> selectAudioTrack(
      TrackGroupArray groups,
      @Capabilities int[][] formatSupport,
      @AdaptiveSupport int mixedMimeTypeAdaptationSupports,
      Parameters params,
      boolean enableAdaptiveTrackSelection)
      throws ExoPlaybackException {
    int selectedTrackIndex = C.INDEX_UNSET;
    int selectedGroupIndex = C.INDEX_UNSET;
    @Nullable AudioTrackScore selectedTrackScore = null;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      @Capabilities int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(
            trackFormatSupport[trackIndex], params.exceedRendererCapabilitiesIfNecessary)) {
          Format format = trackGroup.getFormat(trackIndex);
          AudioTrackScore trackScore =
              new AudioTrackScore(format, params, trackFormatSupport[trackIndex]);
          if (!trackScore.isWithinConstraints && !params.exceedAudioConstraintsIfNecessary) {
            // Track should not be selected.
            continue;
          }
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

    ExoTrackSelection.Definition definition = null;
    if (!params.forceHighestSupportedBitrate
        && !params.forceLowestBitrate
        && enableAdaptiveTrackSelection) {
      // If the group of the track with the highest score allows it, try to enable adaptation.
      int[] adaptiveTracks =
          getAdaptiveAudioTracks(
              selectedGroup,
              formatSupport[selectedGroupIndex],
              selectedTrackIndex,
              params.maxAudioBitrate,
              params.allowAudioMixedMimeTypeAdaptiveness,
              params.allowAudioMixedSampleRateAdaptiveness,
              params.allowAudioMixedChannelCountAdaptiveness);
      if (adaptiveTracks.length > 1) {
        definition = new ExoTrackSelection.Definition(selectedGroup, adaptiveTracks);
      }
    }
    if (definition == null) {
      // We didn't make an adaptive selection, so make a fixed one instead.
      definition = new ExoTrackSelection.Definition(selectedGroup, selectedTrackIndex);
    }

    return Pair.create(definition, Assertions.checkNotNull(selectedTrackScore));
  }

  private static int[] getAdaptiveAudioTracks(
      TrackGroup group,
      @Capabilities int[] formatSupport,
      int primaryTrackIndex,
      int maxAudioBitrate,
      boolean allowMixedMimeTypeAdaptiveness,
      boolean allowMixedSampleRateAdaptiveness,
      boolean allowAudioMixedChannelCountAdaptiveness) {
    Format primaryFormat = group.getFormat(primaryTrackIndex);
    int[] adaptiveIndices = new int[group.length];
    int count = 0;
    for (int i = 0; i < group.length; i++) {
      if (i == primaryTrackIndex
          || isSupportedAdaptiveAudioTrack(
              group.getFormat(i),
              formatSupport[i],
              primaryFormat,
              maxAudioBitrate,
              allowMixedMimeTypeAdaptiveness,
              allowMixedSampleRateAdaptiveness,
              allowAudioMixedChannelCountAdaptiveness)) {
        adaptiveIndices[count++] = i;
      }
    }
    return Arrays.copyOf(adaptiveIndices, count);
  }

  private static boolean isSupportedAdaptiveAudioTrack(
      Format format,
      @Capabilities int formatSupport,
      Format primaryFormat,
      int maxAudioBitrate,
      boolean allowMixedMimeTypeAdaptiveness,
      boolean allowMixedSampleRateAdaptiveness,
      boolean allowAudioMixedChannelCountAdaptiveness) {
    return isSupported(formatSupport, /* allowExceedsCapabilities= */ false)
        && format.bitrate != Format.NO_VALUE
        && format.bitrate <= maxAudioBitrate
        && (allowAudioMixedChannelCountAdaptiveness
            || (format.channelCount != Format.NO_VALUE
                && format.channelCount == primaryFormat.channelCount))
        && (allowMixedMimeTypeAdaptiveness
            || (format.sampleMimeType != null
                && TextUtils.equals(format.sampleMimeType, primaryFormat.sampleMimeType)))
        && (allowMixedSampleRateAdaptiveness
            || (format.sampleRate != Format.NO_VALUE
                && format.sampleRate == primaryFormat.sampleRate));
  }

  // Text track selection implementation.

  /**
   * Called by {@link #selectAllTracks(MappedTrackInfo, int[][][], int[], Parameters)} to create a
   * {@link ExoTrackSelection} for a text renderer.
   *
   * @param groups The {@link TrackGroupArray} mapped to the renderer.
   * @param formatSupport The {@link Capabilities} for each mapped track, indexed by track group and
   *     track (in that order).
   * @param params The selector's current constraint parameters.
   * @param selectedAudioLanguage The language of the selected audio track. May be null if the
   *     selected text track declares no language or no text track was selected.
   * @return The {@link ExoTrackSelection.Definition} and corresponding {@link TextTrackScore}, or
   *     null if no selection was made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  @Nullable
  protected Pair<ExoTrackSelection.Definition, TextTrackScore> selectTextTrack(
      TrackGroupArray groups,
      @Capabilities int[][] formatSupport,
      Parameters params,
      @Nullable String selectedAudioLanguage)
      throws ExoPlaybackException {
    @Nullable TrackGroup selectedGroup = null;
    int selectedTrackIndex = C.INDEX_UNSET;
    @Nullable TextTrackScore selectedTrackScore = null;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      @Capabilities int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(
            trackFormatSupport[trackIndex], params.exceedRendererCapabilitiesIfNecessary)) {
          Format format = trackGroup.getFormat(trackIndex);
          TextTrackScore trackScore =
              new TextTrackScore(
                  format, params, trackFormatSupport[trackIndex], selectedAudioLanguage);
          if (trackScore.isWithinConstraints
              && (selectedTrackScore == null || trackScore.compareTo(selectedTrackScore) > 0)) {
            selectedGroup = trackGroup;
            selectedTrackIndex = trackIndex;
            selectedTrackScore = trackScore;
          }
        }
      }
    }
    return selectedGroup == null
        ? null
        : Pair.create(
            new ExoTrackSelection.Definition(selectedGroup, selectedTrackIndex),
            Assertions.checkNotNull(selectedTrackScore));
  }

  // General track selection methods.

  /**
   * Called by {@link #selectAllTracks(MappedTrackInfo, int[][][], int[], Parameters)} to create a
   * {@link ExoTrackSelection} for a renderer whose type is neither video, audio or text.
   *
   * @param trackType The type of the renderer.
   * @param groups The {@link TrackGroupArray} mapped to the renderer.
   * @param formatSupport The {@link Capabilities} for each mapped track, indexed by track group and
   *     track (in that order).
   * @param params The selector's current constraint parameters.
   * @return The {@link ExoTrackSelection} for the renderer, or null if no selection was made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  @Nullable
  protected ExoTrackSelection.Definition selectOtherTrack(
      int trackType, TrackGroupArray groups, @Capabilities int[][] formatSupport, Parameters params)
      throws ExoPlaybackException {
    @Nullable TrackGroup selectedGroup = null;
    int selectedTrackIndex = 0;
    @Nullable OtherTrackScore selectedTrackScore = null;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      @Capabilities int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(
            trackFormatSupport[trackIndex], params.exceedRendererCapabilitiesIfNecessary)) {
          Format format = trackGroup.getFormat(trackIndex);
          OtherTrackScore trackScore = new OtherTrackScore(format, trackFormatSupport[trackIndex]);
          if (selectedTrackScore == null || trackScore.compareTo(selectedTrackScore) > 0) {
            selectedGroup = trackGroup;
            selectedTrackIndex = trackIndex;
            selectedTrackScore = trackScore;
          }
        }
      }
    }
    return selectedGroup == null
        ? null
        : new ExoTrackSelection.Definition(selectedGroup, selectedTrackIndex);
  }

  // Utility methods.

  /**
   * Determines whether tunneling can be enabled, replacing {@link RendererConfiguration}s in {@code
   * rendererConfigurations} with configurations that enable tunneling on the appropriate renderers
   * if so.
   *
   * @param mappedTrackInfo Mapped track information.
   * @param renderererFormatSupports The {@link Capabilities} for each mapped track, indexed by
   *     renderer, track group and track (in that order).
   * @param rendererConfigurations The renderer configurations. Configurations may be replaced with
   *     ones that enable tunneling as a result of this call.
   * @param trackSelections The renderer track selections.
   */
  private static void maybeConfigureRenderersForTunneling(
      MappedTrackInfo mappedTrackInfo,
      @Capabilities int[][][] renderererFormatSupports,
      @NullableType RendererConfiguration[] rendererConfigurations,
      @NullableType ExoTrackSelection[] trackSelections) {
    // Check whether we can enable tunneling. To enable tunneling we require exactly one audio and
    // one video renderer to support tunneling and have a selection.
    int tunnelingAudioRendererIndex = -1;
    int tunnelingVideoRendererIndex = -1;
    boolean enableTunneling = true;
    for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
      int rendererType = mappedTrackInfo.getRendererType(i);
      ExoTrackSelection trackSelection = trackSelections[i];
      if ((rendererType == C.TRACK_TYPE_AUDIO || rendererType == C.TRACK_TYPE_VIDEO)
          && trackSelection != null) {
        if (rendererSupportsTunneling(
            renderererFormatSupports[i], mappedTrackInfo.getTrackGroups(i), trackSelection)) {
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
          new RendererConfiguration(/* tunneling= */ true);
      rendererConfigurations[tunnelingAudioRendererIndex] = tunnelingRendererConfiguration;
      rendererConfigurations[tunnelingVideoRendererIndex] = tunnelingRendererConfiguration;
    }
  }

  /**
   * Returns whether a renderer supports tunneling for a {@link ExoTrackSelection}.
   *
   * @param formatSupport The {@link Capabilities} for each track, indexed by group index and track
   *     index (in that order).
   * @param trackGroups The {@link TrackGroupArray}s for the renderer.
   * @param selection The track selection.
   * @return Whether the renderer supports tunneling for the {@link ExoTrackSelection}.
   */
  private static boolean rendererSupportsTunneling(
      @Capabilities int[][] formatSupport,
      TrackGroupArray trackGroups,
      ExoTrackSelection selection) {
    if (selection == null) {
      return false;
    }
    int trackGroupIndex = trackGroups.indexOf(selection.getTrackGroup());
    for (int i = 0; i < selection.length(); i++) {
      @Capabilities
      int trackFormatSupport = formatSupport[trackGroupIndex][selection.getIndexInTrackGroup(i)];
      if (RendererCapabilities.getTunnelingSupport(trackFormatSupport)
          != RendererCapabilities.TUNNELING_SUPPORTED) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true if the {@link FormatSupport} in the given {@link Capabilities} is {@link
   * C#FORMAT_HANDLED} or if {@code allowExceedsCapabilities} is set and the format support is
   * {@link C#FORMAT_EXCEEDS_CAPABILITIES}.
   *
   * @param formatSupport {@link Capabilities}.
   * @param allowExceedsCapabilities Whether to return true if {@link FormatSupport} is {@link
   *     C#FORMAT_EXCEEDS_CAPABILITIES}.
   * @return True if {@link FormatSupport} is {@link C#FORMAT_HANDLED}, or if {@code
   *     allowExceedsCapabilities} is set and the format support is {@link
   *     C#FORMAT_EXCEEDS_CAPABILITIES}.
   */
  protected static boolean isSupported(
      @Capabilities int formatSupport, boolean allowExceedsCapabilities) {
    @FormatSupport int maskedSupport = RendererCapabilities.getFormatSupport(formatSupport);
    return maskedSupport == C.FORMAT_HANDLED
        || (allowExceedsCapabilities && maskedSupport == C.FORMAT_EXCEEDS_CAPABILITIES);
  }

  /**
   * Normalizes the input string to null if it does not define a language, or returns it otherwise.
   *
   * @param language The string.
   * @return The string, optionally normalized to null if it does not define a language.
   */
  @Nullable
  protected static String normalizeUndeterminedLanguageToNull(@Nullable String language) {
    return TextUtils.isEmpty(language) || TextUtils.equals(language, C.LANGUAGE_UNDETERMINED)
        ? null
        : language;
  }

  /**
   * Returns a score for how well a language specified in a {@link Format} matches a given language.
   *
   * @param format The {@link Format}.
   * @param language The language, or null.
   * @param allowUndeterminedFormatLanguage Whether matches with an empty or undetermined format
   *     language tag are allowed.
   * @return A score of 4 if the languages match fully, a score of 3 if the languages match partly,
   *     a score of 2 if the languages don't match but belong to the same main language, a score of
   *     1 if the format language is undetermined and such a match is allowed, and a score of 0 if
   *     the languages don't match at all.
   */
  protected static int getFormatLanguageScore(
      Format format, @Nullable String language, boolean allowUndeterminedFormatLanguage) {
    if (!TextUtils.isEmpty(language) && language.equals(format.language)) {
      // Full literal match of non-empty languages, including matches of an explicit "und" query.
      return 4;
    }
    language = normalizeUndeterminedLanguageToNull(language);
    String formatLanguage = normalizeUndeterminedLanguageToNull(format.language);
    if (formatLanguage == null || language == null) {
      // At least one of the languages is undetermined.
      return allowUndeterminedFormatLanguage && formatLanguage == null ? 1 : 0;
    }
    if (formatLanguage.startsWith(language) || language.startsWith(formatLanguage)) {
      // Partial match where one language is a subset of the other (e.g. "zh-hans" and "zh-hans-hk")
      return 3;
    }
    String formatMainLanguage = Util.splitAtFirst(formatLanguage, "-")[0];
    String queryMainLanguage = Util.splitAtFirst(language, "-")[0];
    if (formatMainLanguage.equals(queryMainLanguage)) {
      // Partial match where only the main language tag is the same (e.g. "fr-fr" and "fr-ca")
      return 2;
    }
    return 0;
  }

  private static List<Integer> getViewportFilteredTrackIndices(
      TrackGroup group, int viewportWidth, int viewportHeight, boolean orientationMayChange) {
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
        Point maxVideoSizeInViewport =
            getMaxVideoSizeInViewport(
                orientationMayChange, viewportWidth, viewportHeight, format.width, format.height);
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
  private static Point getMaxVideoSizeInViewport(
      boolean orientationMayChange,
      int viewportWidth,
      int viewportHeight,
      int videoWidth,
      int videoHeight) {
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

  /** Represents how well a video track matches the selection {@link Parameters}. */
  protected static final class VideoTrackScore implements Comparable<VideoTrackScore> {

    /**
     * Whether the provided format is within the parameter maximum constraints. If {@code false},
     * the format should not be selected.
     */
    public final boolean isWithinMaxConstraints;

    private final Parameters parameters;
    private final boolean isWithinMinConstraints;
    private final boolean isWithinRendererCapabilities;
    private final int bitrate;
    private final int pixelCount;
    private final int preferredMimeTypeMatchIndex;

    public VideoTrackScore(
        Format format,
        Parameters parameters,
        @Capabilities int formatSupport,
        boolean isSuitableForViewport) {
      this.parameters = parameters;
      isWithinMaxConstraints =
          isSuitableForViewport
              && (format.width == Format.NO_VALUE || format.width <= parameters.maxVideoWidth)
              && (format.height == Format.NO_VALUE || format.height <= parameters.maxVideoHeight)
              && (format.frameRate == Format.NO_VALUE
                  || format.frameRate <= parameters.maxVideoFrameRate)
              && (format.bitrate == Format.NO_VALUE
                  || format.bitrate <= parameters.maxVideoBitrate);
      isWithinMinConstraints =
          isSuitableForViewport
              && (format.width == Format.NO_VALUE || format.width >= parameters.minVideoWidth)
              && (format.height == Format.NO_VALUE || format.height >= parameters.minVideoHeight)
              && (format.frameRate == Format.NO_VALUE
                  || format.frameRate >= parameters.minVideoFrameRate)
              && (format.bitrate == Format.NO_VALUE
                  || format.bitrate >= parameters.minVideoBitrate);
      isWithinRendererCapabilities =
          isSupported(formatSupport, /* allowExceedsCapabilities= */ false);
      bitrate = format.bitrate;
      pixelCount = format.getPixelCount();
      int bestMimeTypeMatchIndex = Integer.MAX_VALUE;
      for (int i = 0; i < parameters.preferredVideoMimeTypes.size(); i++) {
        if (format.sampleMimeType != null
            && format.sampleMimeType.equals(parameters.preferredVideoMimeTypes.get(i))) {
          bestMimeTypeMatchIndex = i;
          break;
        }
      }
      preferredMimeTypeMatchIndex = bestMimeTypeMatchIndex;
    }

    @Override
    public int compareTo(VideoTrackScore other) {
      // The preferred ordering by video quality depends on the constraints:
      // - Not within renderer capabilities: Prefer lower quality because it's more likely to play.
      // - Within min and max constraints: Prefer higher quality.
      // - Within max constraints only: Prefer higher quality because it gets us closest to
      //   satisfying the violated min constraints.
      // - Within min constraints only: Prefer lower quality because it gets us closest to
      //   satisfying the violated max constraints.
      // - Outside min and max constraints: Arbitrarily prefer lower quality.
      Ordering<Integer> qualityOrdering =
          isWithinMaxConstraints && isWithinRendererCapabilities
              ? FORMAT_VALUE_ORDERING
              : FORMAT_VALUE_ORDERING.reverse();
      return ComparisonChain.start()
          .compareFalseFirst(this.isWithinRendererCapabilities, other.isWithinRendererCapabilities)
          .compareFalseFirst(this.isWithinMaxConstraints, other.isWithinMaxConstraints)
          .compareFalseFirst(this.isWithinMinConstraints, other.isWithinMinConstraints)
          .compare(
              this.preferredMimeTypeMatchIndex,
              other.preferredMimeTypeMatchIndex,
              Ordering.natural().reverse())
          .compare(
              this.bitrate,
              other.bitrate,
              parameters.forceLowestBitrate ? FORMAT_VALUE_ORDERING.reverse() : NO_ORDER)
          .compare(this.pixelCount, other.pixelCount, qualityOrdering)
          .compare(this.bitrate, other.bitrate, qualityOrdering)
          .result();
    }
  }

  /** Represents how well an audio track matches the selection {@link Parameters}. */
  protected static final class AudioTrackScore implements Comparable<AudioTrackScore> {

    /**
     * Whether the provided format is within the parameter constraints. If {@code false}, the format
     * should not be selected.
     */
    public final boolean isWithinConstraints;

    @Nullable private final String language;
    private final Parameters parameters;
    private final boolean isWithinRendererCapabilities;
    private final int preferredLanguageScore;
    private final int preferredLanguageIndex;
    private final int preferredRoleFlagsScore;
    private final int localeLanguageMatchIndex;
    private final int localeLanguageScore;
    private final boolean isDefaultSelectionFlag;
    private final int channelCount;
    private final int sampleRate;
    private final int bitrate;
    private final int preferredMimeTypeMatchIndex;

    public AudioTrackScore(Format format, Parameters parameters, @Capabilities int formatSupport) {
      this.parameters = parameters;
      this.language = normalizeUndeterminedLanguageToNull(format.language);
      isWithinRendererCapabilities =
          isSupported(formatSupport, /* allowExceedsCapabilities= */ false);
      int bestLanguageScore = 0;
      int bestLanguageIndex = Integer.MAX_VALUE;
      for (int i = 0; i < parameters.preferredAudioLanguages.size(); i++) {
        int score =
            getFormatLanguageScore(
                format,
                parameters.preferredAudioLanguages.get(i),
                /* allowUndeterminedFormatLanguage= */ false);
        if (score > 0) {
          bestLanguageIndex = i;
          bestLanguageScore = score;
          break;
        }
      }
      preferredLanguageIndex = bestLanguageIndex;
      preferredLanguageScore = bestLanguageScore;
      preferredRoleFlagsScore =
          Integer.bitCount(format.roleFlags & parameters.preferredAudioRoleFlags);
      isDefaultSelectionFlag = (format.selectionFlags & C.SELECTION_FLAG_DEFAULT) != 0;
      channelCount = format.channelCount;
      sampleRate = format.sampleRate;
      bitrate = format.bitrate;
      isWithinConstraints =
          (format.bitrate == Format.NO_VALUE || format.bitrate <= parameters.maxAudioBitrate)
              && (format.channelCount == Format.NO_VALUE
                  || format.channelCount <= parameters.maxAudioChannelCount);
      String[] localeLanguages = Util.getSystemLanguageCodes();
      int bestLocaleMatchIndex = Integer.MAX_VALUE;
      int bestLocaleMatchScore = 0;
      for (int i = 0; i < localeLanguages.length; i++) {
        int score =
            getFormatLanguageScore(
                format, localeLanguages[i], /* allowUndeterminedFormatLanguage= */ false);
        if (score > 0) {
          bestLocaleMatchIndex = i;
          bestLocaleMatchScore = score;
          break;
        }
      }
      localeLanguageMatchIndex = bestLocaleMatchIndex;
      localeLanguageScore = bestLocaleMatchScore;
      int bestMimeTypeMatchIndex = Integer.MAX_VALUE;
      for (int i = 0; i < parameters.preferredAudioMimeTypes.size(); i++) {
        if (format.sampleMimeType != null
            && format.sampleMimeType.equals(parameters.preferredAudioMimeTypes.get(i))) {
          bestMimeTypeMatchIndex = i;
          break;
        }
      }
      preferredMimeTypeMatchIndex = bestMimeTypeMatchIndex;
    }

    /**
     * Compares this score with another.
     *
     * @param other The other score to compare to.
     * @return A positive integer if this score is better than the other. Zero if they are equal. A
     *     negative integer if this score is worse than the other.
     */
    @Override
    public int compareTo(AudioTrackScore other) {
      // If the formats are within constraints and renderer capabilities then prefer higher values
      // of channel count, sample rate and bit rate in that order. Otherwise, prefer lower values.
      Ordering<Integer> qualityOrdering =
          isWithinConstraints && isWithinRendererCapabilities
              ? FORMAT_VALUE_ORDERING
              : FORMAT_VALUE_ORDERING.reverse();
      return ComparisonChain.start()
          .compareFalseFirst(this.isWithinRendererCapabilities, other.isWithinRendererCapabilities)
          .compare(
              this.preferredLanguageIndex,
              other.preferredLanguageIndex,
              Ordering.natural().reverse())
          .compare(this.preferredLanguageScore, other.preferredLanguageScore)
          .compare(this.preferredRoleFlagsScore, other.preferredRoleFlagsScore)
          .compareFalseFirst(this.isWithinConstraints, other.isWithinConstraints)
          .compare(
              this.preferredMimeTypeMatchIndex,
              other.preferredMimeTypeMatchIndex,
              Ordering.natural().reverse())
          .compare(
              this.bitrate,
              other.bitrate,
              parameters.forceLowestBitrate ? FORMAT_VALUE_ORDERING.reverse() : NO_ORDER)
          .compareFalseFirst(this.isDefaultSelectionFlag, other.isDefaultSelectionFlag)
          .compare(
              this.localeLanguageMatchIndex,
              other.localeLanguageMatchIndex,
              Ordering.natural().reverse())
          .compare(this.localeLanguageScore, other.localeLanguageScore)
          .compare(this.channelCount, other.channelCount, qualityOrdering)
          .compare(this.sampleRate, other.sampleRate, qualityOrdering)
          .compare(
              this.bitrate,
              other.bitrate,
              // Only compare bit rates of tracks with matching language information.
              Util.areEqual(this.language, other.language) ? qualityOrdering : NO_ORDER)
          .result();
    }
  }

  /** Represents how well a text track matches the selection {@link Parameters}. */
  protected static final class TextTrackScore implements Comparable<TextTrackScore> {

    /**
     * Whether the provided format is within the parameter constraints. If {@code false}, the format
     * should not be selected.
     */
    public final boolean isWithinConstraints;

    private final boolean isWithinRendererCapabilities;
    private final boolean isDefault;
    private final boolean isForced;
    private final int preferredLanguageIndex;
    private final int preferredLanguageScore;
    private final int preferredRoleFlagsScore;
    private final int selectedAudioLanguageScore;
    private final boolean hasCaptionRoleFlags;

    public TextTrackScore(
        Format format,
        Parameters parameters,
        @Capabilities int trackFormatSupport,
        @Nullable String selectedAudioLanguage) {
      isWithinRendererCapabilities =
          isSupported(trackFormatSupport, /* allowExceedsCapabilities= */ false);
      int maskedSelectionFlags =
          format.selectionFlags & ~parameters.disabledTextTrackSelectionFlags;
      isDefault = (maskedSelectionFlags & C.SELECTION_FLAG_DEFAULT) != 0;
      isForced = (maskedSelectionFlags & C.SELECTION_FLAG_FORCED) != 0;
      int bestLanguageIndex = Integer.MAX_VALUE;
      int bestLanguageScore = 0;
      // Compare against empty (unset) language if no preference is given to allow the selection of
      // a text track with undetermined language.
      ImmutableList<String> preferredLanguages =
          parameters.preferredTextLanguages.isEmpty()
              ? ImmutableList.of("")
              : parameters.preferredTextLanguages;
      for (int i = 0; i < preferredLanguages.size(); i++) {
        int score =
            getFormatLanguageScore(
                format, preferredLanguages.get(i), parameters.selectUndeterminedTextLanguage);
        if (score > 0) {
          bestLanguageIndex = i;
          bestLanguageScore = score;
          break;
        }
      }
      preferredLanguageIndex = bestLanguageIndex;
      preferredLanguageScore = bestLanguageScore;
      preferredRoleFlagsScore =
          Integer.bitCount(format.roleFlags & parameters.preferredTextRoleFlags);
      hasCaptionRoleFlags =
          (format.roleFlags & (C.ROLE_FLAG_CAPTION | C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND)) != 0;
      boolean selectedAudioLanguageUndetermined =
          normalizeUndeterminedLanguageToNull(selectedAudioLanguage) == null;
      selectedAudioLanguageScore =
          getFormatLanguageScore(format, selectedAudioLanguage, selectedAudioLanguageUndetermined);
      isWithinConstraints =
          preferredLanguageScore > 0
              || (parameters.preferredTextLanguages.isEmpty() && preferredRoleFlagsScore > 0)
              || isDefault
              || (isForced && selectedAudioLanguageScore > 0);
    }

    /**
     * Compares this score with another.
     *
     * @param other The other score to compare to.
     * @return A positive integer if this score is better than the other. Zero if they are equal. A
     *     negative integer if this score is worse than the other.
     */
    @Override
    public int compareTo(TextTrackScore other) {
      ComparisonChain chain =
          ComparisonChain.start()
              .compareFalseFirst(
                  this.isWithinRendererCapabilities, other.isWithinRendererCapabilities)
              .compare(
                  this.preferredLanguageIndex,
                  other.preferredLanguageIndex,
                  Ordering.natural().reverse())
              .compare(this.preferredLanguageScore, other.preferredLanguageScore)
              .compare(this.preferredRoleFlagsScore, other.preferredRoleFlagsScore)
              .compareFalseFirst(this.isDefault, other.isDefault)
              .compare(
                  this.isForced,
                  other.isForced,
                  // Prefer non-forced to forced if a preferred text language has been matched.
                  // Where both are provided the non-forced track will usually contain the forced
                  // subtitles as a subset. Otherwise, prefer a forced track.
                  preferredLanguageScore == 0 ? Ordering.natural() : Ordering.natural().reverse())
              .compare(this.selectedAudioLanguageScore, other.selectedAudioLanguageScore);
      if (preferredRoleFlagsScore == 0) {
        chain = chain.compareTrueFirst(this.hasCaptionRoleFlags, other.hasCaptionRoleFlags);
      }
      return chain.result();
    }
  }

  /**
   * Represents how well any other track (non video, audio or text) matches the selection {@link
   * Parameters}.
   */
  protected static final class OtherTrackScore implements Comparable<OtherTrackScore> {

    private final boolean isDefault;
    private final boolean isWithinRendererCapabilities;

    public OtherTrackScore(Format format, @Capabilities int trackFormatSupport) {
      isDefault = (format.selectionFlags & C.SELECTION_FLAG_DEFAULT) != 0;
      isWithinRendererCapabilities =
          isSupported(trackFormatSupport, /* allowExceedsCapabilities= */ false);
    }

    @Override
    public int compareTo(OtherTrackScore other) {
      return ComparisonChain.start()
          .compareFalseFirst(this.isWithinRendererCapabilities, other.isWithinRendererCapabilities)
          .compareFalseFirst(this.isDefault, other.isDefault)
          .result();
    }
  }
}
