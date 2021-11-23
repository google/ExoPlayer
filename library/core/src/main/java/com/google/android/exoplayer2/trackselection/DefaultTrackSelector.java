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
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Bundleable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.FormatSupport;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RendererCapabilities.AdaptiveSupport;
import com.google.android.exoplayer2.RendererCapabilities.Capabilities;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionOverrides.TrackSelectionOverride;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.BundleableUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * A default {@link TrackSelector} suitable for most use cases.
 *
 * <h2>Modifying parameters</h2>
 *
 * Track selection parameters should be modified by obtaining a {@link
 * TrackSelectionParameters.Builder} initialized with the current {@link TrackSelectionParameters}
 * from the player. The desired modifications can be made on the builder, and the resulting {@link
 * TrackSelectionParameters} can then be built and set on the player:
 *
 * <pre>{@code
 * player.setTrackSelectionParameters(
 *     player.getTrackSelectionParameters()
 *         .buildUpon()
 *         .setMaxVideoSizeSd()
 *         .setPreferredAudioLanguage("de")
 *         .build());
 *
 * }</pre>
 *
 * Some specialized parameters are only available in the extended {@link Parameters} class, which
 * can be retrieved and modified in a similar way in this track selector:
 *
 * <pre>{@code
 * defaultTrackSelector.setParameters(
 *     defaultTrackSelector.getParameters()
 *         .buildUpon()
 *         .setTunnelingEnabled(true)
 *         .build());
 *
 * }</pre>
 */
public class DefaultTrackSelector extends MappingTrackSelector {

  /**
   * A builder for {@link Parameters}. See the {@link Parameters} documentation for explanations of
   * the parameters that can be configured using this builder.
   */
  public static final class ParametersBuilder extends TrackSelectionParameters.Builder {

    // Video
    private boolean exceedVideoConstraintsIfNecessary;
    private boolean allowVideoMixedMimeTypeAdaptiveness;
    private boolean allowVideoNonSeamlessAdaptiveness;
    // Audio
    private boolean exceedAudioConstraintsIfNecessary;
    private boolean allowAudioMixedMimeTypeAdaptiveness;
    private boolean allowAudioMixedSampleRateAdaptiveness;
    private boolean allowAudioMixedChannelCountAdaptiveness;
    // Text
    @C.SelectionFlags private int disabledTextTrackSelectionFlags;
    // General
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
      selectionOverrides = new SparseArray<>();
      rendererDisabledFlags = new SparseBooleanArray();
      init();
    }

    /**
     * Creates a builder with default initial values.
     *
     * @param context Any context.
     */
    public ParametersBuilder(Context context) {
      super(context);
      selectionOverrides = new SparseArray<>();
      rendererDisabledFlags = new SparseBooleanArray();
      init();
    }

    /**
     * @param initialValues The {@link Parameters} from which the initial values of the builder are
     *     obtained.
     */
    private ParametersBuilder(Parameters initialValues) {
      super(initialValues);
      // Text
      disabledTextTrackSelectionFlags = initialValues.disabledTextTrackSelectionFlags;
      // Video
      exceedVideoConstraintsIfNecessary = initialValues.exceedVideoConstraintsIfNecessary;
      allowVideoMixedMimeTypeAdaptiveness = initialValues.allowVideoMixedMimeTypeAdaptiveness;
      allowVideoNonSeamlessAdaptiveness = initialValues.allowVideoNonSeamlessAdaptiveness;
      // Audio
      exceedAudioConstraintsIfNecessary = initialValues.exceedAudioConstraintsIfNecessary;
      allowAudioMixedMimeTypeAdaptiveness = initialValues.allowAudioMixedMimeTypeAdaptiveness;
      allowAudioMixedSampleRateAdaptiveness = initialValues.allowAudioMixedSampleRateAdaptiveness;
      allowAudioMixedChannelCountAdaptiveness =
          initialValues.allowAudioMixedChannelCountAdaptiveness;
      // General
      exceedRendererCapabilitiesIfNecessary = initialValues.exceedRendererCapabilitiesIfNecessary;
      tunnelingEnabled = initialValues.tunnelingEnabled;
      allowMultipleAdaptiveSelections = initialValues.allowMultipleAdaptiveSelections;
      // Overrides
      selectionOverrides = cloneSelectionOverrides(initialValues.selectionOverrides);
      rendererDisabledFlags = initialValues.rendererDisabledFlags.clone();
    }

    @SuppressWarnings("method.invocation") // Only setter are invoked.
    private ParametersBuilder(Bundle bundle) {
      super(bundle);
      Parameters defaultValue = Parameters.DEFAULT_WITHOUT_CONTEXT;
      // Video
      setExceedVideoConstraintsIfNecessary(
          bundle.getBoolean(
              Parameters.keyForField(Parameters.FIELD_EXCEED_VIDEO_CONSTRAINTS_IF_NECESSARY),
              defaultValue.exceedVideoConstraintsIfNecessary));
      setAllowVideoMixedMimeTypeAdaptiveness(
          bundle.getBoolean(
              Parameters.keyForField(Parameters.FIELD_ALLOW_VIDEO_MIXED_MIME_TYPE_ADAPTIVENESS),
              defaultValue.allowVideoMixedMimeTypeAdaptiveness));
      setAllowVideoNonSeamlessAdaptiveness(
          bundle.getBoolean(
              Parameters.keyForField(Parameters.FIELD_ALLOW_VIDEO_NON_SEAMLESS_ADAPTIVENESS),
              defaultValue.allowVideoNonSeamlessAdaptiveness));
      // Audio
      setExceedAudioConstraintsIfNecessary(
          bundle.getBoolean(
              Parameters.keyForField(Parameters.FIELD_EXCEED_AUDIO_CONSTRAINTS_IF_NCESSARY),
              defaultValue.exceedAudioConstraintsIfNecessary));
      setAllowAudioMixedMimeTypeAdaptiveness(
          bundle.getBoolean(
              Parameters.keyForField(Parameters.FIELD_ALLOW_AUDIO_MIXED_MIME_TYPE_ADAPTIVENESS),
              defaultValue.allowAudioMixedMimeTypeAdaptiveness));
      setAllowAudioMixedSampleRateAdaptiveness(
          bundle.getBoolean(
              Parameters.keyForField(Parameters.FIELD_ALLOW_AUDIO_MIXED_SAMPLE_RATE_ADAPTIVENESS),
              defaultValue.allowAudioMixedSampleRateAdaptiveness));
      setAllowAudioMixedChannelCountAdaptiveness(
          bundle.getBoolean(
              Parameters.keyForField(Parameters.FIELD_ALLOW_AUDIO_MIXED_CHANNEL_COUNT_ADAPTIVENESS),
              defaultValue.allowAudioMixedChannelCountAdaptiveness));
      // Text
      setDisabledTextTrackSelectionFlags(
          bundle.getInt(
              Parameters.keyForField(Parameters.FIELD_DISABLED_TEXT_TRACK_SELECTION_FLAGS),
              defaultValue.disabledTextTrackSelectionFlags));
      // General
      setExceedRendererCapabilitiesIfNecessary(
          bundle.getBoolean(
              Parameters.keyForField(Parameters.FIELD_EXCEED_RENDERER_CAPABILITIES_IF_NECESSARY),
              defaultValue.exceedRendererCapabilitiesIfNecessary));
      setTunnelingEnabled(
          bundle.getBoolean(
              Parameters.keyForField(Parameters.FIELD_TUNNELING_ENABLED),
              defaultValue.tunnelingEnabled));
      setAllowMultipleAdaptiveSelections(
          bundle.getBoolean(
              Parameters.keyForField(Parameters.FIELD_ALLOW_MULTIPLE_ADAPTIVE_SELECTIONS),
              defaultValue.allowMultipleAdaptiveSelections));

      selectionOverrides = new SparseArray<>();
      setSelectionOverridesFromBundle(bundle);

      rendererDisabledFlags =
          makeSparseBooleanArrayFromTrueKeys(
              bundle.getIntArray(
                  Parameters.keyForField(Parameters.FIELD_RENDERER_DISABLED_INDICES)));
    }

    @Override
    protected ParametersBuilder set(TrackSelectionParameters parameters) {
      super.set(parameters);
      return this;
    }

    // Video

    @Override
    public DefaultTrackSelector.ParametersBuilder setMaxVideoSizeSd() {
      super.setMaxVideoSizeSd();
      return this;
    }

    @Override
    public DefaultTrackSelector.ParametersBuilder clearVideoSizeConstraints() {
      super.clearVideoSizeConstraints();
      return this;
    }

    @Override
    public DefaultTrackSelector.ParametersBuilder setMaxVideoSize(
        int maxVideoWidth, int maxVideoHeight) {
      super.setMaxVideoSize(maxVideoWidth, maxVideoHeight);
      return this;
    }

    @Override
    public DefaultTrackSelector.ParametersBuilder setMaxVideoFrameRate(int maxVideoFrameRate) {
      super.setMaxVideoFrameRate(maxVideoFrameRate);
      return this;
    }

    @Override
    public DefaultTrackSelector.ParametersBuilder setMaxVideoBitrate(int maxVideoBitrate) {
      super.setMaxVideoBitrate(maxVideoBitrate);
      return this;
    }

    @Override
    public DefaultTrackSelector.ParametersBuilder setMinVideoSize(
        int minVideoWidth, int minVideoHeight) {
      super.setMinVideoSize(minVideoWidth, minVideoHeight);
      return this;
    }

    @Override
    public DefaultTrackSelector.ParametersBuilder setMinVideoFrameRate(int minVideoFrameRate) {
      super.setMinVideoFrameRate(minVideoFrameRate);
      return this;
    }

    @Override
    public DefaultTrackSelector.ParametersBuilder setMinVideoBitrate(int minVideoBitrate) {
      super.setMinVideoBitrate(minVideoBitrate);
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

    @Override
    public ParametersBuilder setViewportSizeToPhysicalDisplaySize(
        Context context, boolean viewportOrientationMayChange) {
      super.setViewportSizeToPhysicalDisplaySize(context, viewportOrientationMayChange);
      return this;
    }

    @Override
    public ParametersBuilder clearViewportSizeConstraints() {
      super.clearViewportSizeConstraints();
      return this;
    }

    @Override
    public ParametersBuilder setViewportSize(
        int viewportWidth, int viewportHeight, boolean viewportOrientationMayChange) {
      super.setViewportSize(viewportWidth, viewportHeight, viewportOrientationMayChange);
      return this;
    }

    @Override
    public ParametersBuilder setPreferredVideoMimeType(@Nullable String mimeType) {
      super.setPreferredVideoMimeType(mimeType);
      return this;
    }

    @Override
    public ParametersBuilder setPreferredVideoMimeTypes(String... mimeTypes) {
      super.setPreferredVideoMimeTypes(mimeTypes);
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

    @Override
    public ParametersBuilder setMaxAudioChannelCount(int maxAudioChannelCount) {
      super.setMaxAudioChannelCount(maxAudioChannelCount);
      return this;
    }

    @Override
    public ParametersBuilder setMaxAudioBitrate(int maxAudioBitrate) {
      super.setMaxAudioBitrate(maxAudioBitrate);
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

    @Override
    public ParametersBuilder setPreferredAudioMimeType(@Nullable String mimeType) {
      super.setPreferredAudioMimeType(mimeType);
      return this;
    }

    @Override
    public ParametersBuilder setPreferredAudioMimeTypes(String... mimeTypes) {
      super.setPreferredAudioMimeTypes(mimeTypes);
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

    /**
     * Sets a bitmask of selection flags that are disabled for text track selections.
     *
     * @param disabledTextTrackSelectionFlags A bitmask of {@link C.SelectionFlags} that are
     *     disabled for text track selections.
     * @return This builder.
     */
    public ParametersBuilder setDisabledTextTrackSelectionFlags(
        @C.SelectionFlags int disabledTextTrackSelectionFlags) {
      this.disabledTextTrackSelectionFlags = disabledTextTrackSelectionFlags;
      return this;
    }

    // General

    @Override
    public ParametersBuilder setForceLowestBitrate(boolean forceLowestBitrate) {
      super.setForceLowestBitrate(forceLowestBitrate);
      return this;
    }

    @Override
    public ParametersBuilder setForceHighestSupportedBitrate(boolean forceHighestSupportedBitrate) {
      super.setForceHighestSupportedBitrate(forceHighestSupportedBitrate);
      return this;
    }

    @Override
    public ParametersBuilder setTrackSelectionOverrides(
        TrackSelectionOverrides trackSelectionOverrides) {
      super.setTrackSelectionOverrides(trackSelectionOverrides);
      return this;
    }

    @Override
    public ParametersBuilder setDisabledTrackTypes(Set<@C.TrackType Integer> disabledTrackTypes) {
      super.setDisabledTrackTypes(disabledTrackTypes);
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
     * @deprecated Use {@link TrackSelectionParameters.Builder#setTrackSelectionOverrides}.
     */
    @Deprecated
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
     * @deprecated Use {@link TrackSelectionParameters.Builder#setTrackSelectionOverrides}.
     */
    @Deprecated
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
     * @deprecated Use {@link TrackSelectionParameters.Builder#setTrackSelectionOverrides}.
     */
    @Deprecated
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
     * @deprecated Use {@link TrackSelectionParameters.Builder#setTrackSelectionOverrides}.
     */
    @Deprecated
    public final ParametersBuilder clearSelectionOverrides() {
      if (selectionOverrides.size() == 0) {
        // Nothing to clear.
        return this;
      }
      selectionOverrides.clear();
      return this;
    }

    /** Builds a {@link Parameters} instance with the selected values. */
    @Override
    public Parameters build() {
      return new Parameters(this);
    }

    private void init(ParametersBuilder this) {
      // Video
      exceedVideoConstraintsIfNecessary = true;
      allowVideoMixedMimeTypeAdaptiveness = false;
      allowVideoNonSeamlessAdaptiveness = true;
      // Audio
      exceedAudioConstraintsIfNecessary = true;
      allowAudioMixedMimeTypeAdaptiveness = false;
      allowAudioMixedSampleRateAdaptiveness = false;
      allowAudioMixedChannelCountAdaptiveness = false;
      // Text
      disabledTextTrackSelectionFlags = 0;
      // General
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

    private void setSelectionOverridesFromBundle(Bundle bundle) {
      @Nullable
      int[] rendererIndices =
          bundle.getIntArray(
              Parameters.keyForField(Parameters.FIELD_SELECTION_OVERRIDES_RENDERER_INDICES));
      List<TrackGroupArray> trackGroupArrays =
          BundleableUtil.fromBundleNullableList(
              TrackGroupArray.CREATOR,
              bundle.getParcelableArrayList(
                  Parameters.keyForField(Parameters.FIELD_SELECTION_OVERRIDES_TRACK_GROUP_ARRAYS)),
              /* defaultValue= */ ImmutableList.of());
      SparseArray<SelectionOverride> selectionOverrides =
          BundleableUtil.fromBundleNullableSparseArray(
              SelectionOverride.CREATOR,
              bundle.getSparseParcelableArray(
                  Parameters.keyForField(Parameters.FIELD_SELECTION_OVERRIDES)),
              /* defaultValue= */ new SparseArray<>());

      if (rendererIndices == null || rendererIndices.length != trackGroupArrays.size()) {
        return; // Incorrect format, ignore all overrides.
      }
      for (int i = 0; i < rendererIndices.length; i++) {
        int rendererIndex = rendererIndices[i];
        TrackGroupArray groups = trackGroupArrays.get(i);
        @Nullable SelectionOverride selectionOverride = selectionOverrides.get(i);
        setSelectionOverride(rendererIndex, groups, selectionOverride);
      }
    }

    private SparseBooleanArray makeSparseBooleanArrayFromTrueKeys(@Nullable int[] trueKeys) {
      if (trueKeys == null) {
        return new SparseBooleanArray();
      }
      SparseBooleanArray sparseBooleanArray = new SparseBooleanArray(trueKeys.length);
      for (int trueKey : trueKeys) {
        sparseBooleanArray.append(trueKey, true);
      }
      return sparseBooleanArray;
    }
  }

  /**
   * Extends {@link Parameters} by adding fields that are specific to {@link DefaultTrackSelector}.
   */
  public static final class Parameters extends TrackSelectionParameters implements Bundleable {

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
    /**
     * @deprecated This instance is not configured using {@link Context} constraints. Use {@link
     *     #getDefaults(Context)} instead.
     */
    @Deprecated public static final Parameters DEFAULT = DEFAULT_WITHOUT_CONTEXT;

    /**
     * Bitmask of selection flags that are disabled for text track selections. See {@link
     * C.SelectionFlags}. The default value is {@code 0} (i.e. no flags).
     */
    @C.SelectionFlags public final int disabledTextTrackSelectionFlags;

    /** Returns an instance configured with default values. */
    public static Parameters getDefaults(Context context) {
      return new ParametersBuilder(context).build();
    }

    // Video
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

    private Parameters(ParametersBuilder builder) {
      super(builder);
      // Video
      exceedVideoConstraintsIfNecessary = builder.exceedVideoConstraintsIfNecessary;
      allowVideoMixedMimeTypeAdaptiveness = builder.allowVideoMixedMimeTypeAdaptiveness;
      allowVideoNonSeamlessAdaptiveness = builder.allowVideoNonSeamlessAdaptiveness;
      // Audio
      exceedAudioConstraintsIfNecessary = builder.exceedAudioConstraintsIfNecessary;
      allowAudioMixedMimeTypeAdaptiveness = builder.allowAudioMixedMimeTypeAdaptiveness;
      allowAudioMixedSampleRateAdaptiveness = builder.allowAudioMixedSampleRateAdaptiveness;
      allowAudioMixedChannelCountAdaptiveness = builder.allowAudioMixedChannelCountAdaptiveness;
      // Text
      disabledTextTrackSelectionFlags = builder.disabledTextTrackSelectionFlags;
      // General
      exceedRendererCapabilitiesIfNecessary = builder.exceedRendererCapabilitiesIfNecessary;
      tunnelingEnabled = builder.tunnelingEnabled;
      allowMultipleAdaptiveSelections = builder.allowMultipleAdaptiveSelections;
      // Overrides
      selectionOverrides = builder.selectionOverrides;
      rendererDisabledFlags = builder.rendererDisabledFlags;
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
     * @deprecated Only works to retrieve the overrides set with the deprecated {@link
     *     ParametersBuilder#setSelectionOverride(int, TrackGroupArray, SelectionOverride)}. Use
     *     {@link TrackSelectionParameters#trackSelectionOverrides} instead.
     */
    @Deprecated
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
     * @deprecated Only works to retrieve the overrides set with the deprecated {@link
     *     ParametersBuilder#setSelectionOverride(int, TrackGroupArray, SelectionOverride)}. Use
     *     {@link TrackSelectionParameters#trackSelectionOverrides} instead.
     */
    @Deprecated
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

    @SuppressWarnings("EqualsGetClass") // Class is not final for backward-compatibility reason.
    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      Parameters other = (Parameters) obj;
      return super.equals(other)
          // Video
          && exceedVideoConstraintsIfNecessary == other.exceedVideoConstraintsIfNecessary
          && allowVideoMixedMimeTypeAdaptiveness == other.allowVideoMixedMimeTypeAdaptiveness
          && allowVideoNonSeamlessAdaptiveness == other.allowVideoNonSeamlessAdaptiveness
          // Audio
          && exceedAudioConstraintsIfNecessary == other.exceedAudioConstraintsIfNecessary
          && allowAudioMixedMimeTypeAdaptiveness == other.allowAudioMixedMimeTypeAdaptiveness
          && allowAudioMixedSampleRateAdaptiveness == other.allowAudioMixedSampleRateAdaptiveness
          && allowAudioMixedChannelCountAdaptiveness
              == other.allowAudioMixedChannelCountAdaptiveness
          // Text
          && disabledTextTrackSelectionFlags == other.disabledTextTrackSelectionFlags
          // General
          && exceedRendererCapabilitiesIfNecessary == other.exceedRendererCapabilitiesIfNecessary
          && tunnelingEnabled == other.tunnelingEnabled
          && allowMultipleAdaptiveSelections == other.allowMultipleAdaptiveSelections
          // Overrides
          && areRendererDisabledFlagsEqual(rendererDisabledFlags, other.rendererDisabledFlags)
          && areSelectionOverridesEqual(selectionOverrides, other.selectionOverrides);
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + super.hashCode();
      // Video
      result = 31 * result + (exceedVideoConstraintsIfNecessary ? 1 : 0);
      result = 31 * result + (allowVideoMixedMimeTypeAdaptiveness ? 1 : 0);
      result = 31 * result + (allowVideoNonSeamlessAdaptiveness ? 1 : 0);
      // Audio
      result = 31 * result + (exceedAudioConstraintsIfNecessary ? 1 : 0);
      result = 31 * result + (allowAudioMixedMimeTypeAdaptiveness ? 1 : 0);
      result = 31 * result + (allowAudioMixedSampleRateAdaptiveness ? 1 : 0);
      result = 31 * result + (allowAudioMixedChannelCountAdaptiveness ? 1 : 0);
      // Text
      result = 31 * result + disabledTextTrackSelectionFlags;
      // General
      result = 31 * result + (exceedRendererCapabilitiesIfNecessary ? 1 : 0);
      result = 31 * result + (tunnelingEnabled ? 1 : 0);
      result = 31 * result + (allowMultipleAdaptiveSelections ? 1 : 0);
      // Overrides (omitted from hashCode).
      return result;
    }

    // Bundleable implementation.

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
      FIELD_EXCEED_VIDEO_CONSTRAINTS_IF_NECESSARY,
      FIELD_ALLOW_VIDEO_MIXED_MIME_TYPE_ADAPTIVENESS,
      FIELD_ALLOW_VIDEO_NON_SEAMLESS_ADAPTIVENESS,
      FIELD_EXCEED_AUDIO_CONSTRAINTS_IF_NCESSARY,
      FIELD_ALLOW_AUDIO_MIXED_MIME_TYPE_ADAPTIVENESS,
      FIELD_ALLOW_AUDIO_MIXED_SAMPLE_RATE_ADAPTIVENESS,
      FIELD_ALLOW_AUDIO_MIXED_CHANNEL_COUNT_ADAPTIVENESS,
      FIELD_DISABLED_TEXT_TRACK_SELECTION_FLAGS,
      FIELD_EXCEED_RENDERER_CAPABILITIES_IF_NECESSARY,
      FIELD_TUNNELING_ENABLED,
      FIELD_ALLOW_MULTIPLE_ADAPTIVE_SELECTIONS,
      FIELD_SELECTION_OVERRIDES_RENDERER_INDICES,
      FIELD_SELECTION_OVERRIDES_TRACK_GROUP_ARRAYS,
      FIELD_SELECTION_OVERRIDES,
      FIELD_RENDERER_DISABLED_INDICES,
    })
    private @interface FieldNumber {}

    // Start at 1000 to avoid conflict with the base class fields.
    private static final int FIELD_EXCEED_VIDEO_CONSTRAINTS_IF_NECESSARY = 1000;
    private static final int FIELD_ALLOW_VIDEO_MIXED_MIME_TYPE_ADAPTIVENESS = 1001;
    private static final int FIELD_ALLOW_VIDEO_NON_SEAMLESS_ADAPTIVENESS = 1002;
    private static final int FIELD_EXCEED_AUDIO_CONSTRAINTS_IF_NCESSARY = 1003;
    private static final int FIELD_ALLOW_AUDIO_MIXED_MIME_TYPE_ADAPTIVENESS = 1004;
    private static final int FIELD_ALLOW_AUDIO_MIXED_SAMPLE_RATE_ADAPTIVENESS = 1005;
    private static final int FIELD_ALLOW_AUDIO_MIXED_CHANNEL_COUNT_ADAPTIVENESS = 1006;
    private static final int FIELD_DISABLED_TEXT_TRACK_SELECTION_FLAGS = 1007;
    private static final int FIELD_EXCEED_RENDERER_CAPABILITIES_IF_NECESSARY = 1008;
    private static final int FIELD_TUNNELING_ENABLED = 1009;
    private static final int FIELD_ALLOW_MULTIPLE_ADAPTIVE_SELECTIONS = 1010;
    private static final int FIELD_SELECTION_OVERRIDES_RENDERER_INDICES = 1011;
    private static final int FIELD_SELECTION_OVERRIDES_TRACK_GROUP_ARRAYS = 1012;
    private static final int FIELD_SELECTION_OVERRIDES = 1013;
    private static final int FIELD_RENDERER_DISABLED_INDICES = 1014;

    @Override
    public Bundle toBundle() {
      Bundle bundle = super.toBundle();

      // Video
      bundle.putBoolean(
          keyForField(FIELD_EXCEED_VIDEO_CONSTRAINTS_IF_NECESSARY),
          exceedVideoConstraintsIfNecessary);
      bundle.putBoolean(
          keyForField(FIELD_ALLOW_VIDEO_MIXED_MIME_TYPE_ADAPTIVENESS),
          allowVideoMixedMimeTypeAdaptiveness);
      bundle.putBoolean(
          keyForField(FIELD_ALLOW_VIDEO_NON_SEAMLESS_ADAPTIVENESS),
          allowVideoNonSeamlessAdaptiveness);
      // Audio
      bundle.putBoolean(
          keyForField(FIELD_EXCEED_AUDIO_CONSTRAINTS_IF_NCESSARY),
          exceedAudioConstraintsIfNecessary);
      bundle.putBoolean(
          keyForField(FIELD_ALLOW_AUDIO_MIXED_MIME_TYPE_ADAPTIVENESS),
          allowAudioMixedMimeTypeAdaptiveness);
      bundle.putBoolean(
          keyForField(FIELD_ALLOW_AUDIO_MIXED_SAMPLE_RATE_ADAPTIVENESS),
          allowAudioMixedSampleRateAdaptiveness);
      bundle.putBoolean(
          keyForField(FIELD_ALLOW_AUDIO_MIXED_CHANNEL_COUNT_ADAPTIVENESS),
          allowAudioMixedChannelCountAdaptiveness);
      // Text
      bundle.putInt(
          keyForField(FIELD_DISABLED_TEXT_TRACK_SELECTION_FLAGS), disabledTextTrackSelectionFlags);
      // General
      bundle.putBoolean(
          keyForField(FIELD_EXCEED_RENDERER_CAPABILITIES_IF_NECESSARY),
          exceedRendererCapabilitiesIfNecessary);
      bundle.putBoolean(keyForField(FIELD_TUNNELING_ENABLED), tunnelingEnabled);
      bundle.putBoolean(
          keyForField(FIELD_ALLOW_MULTIPLE_ADAPTIVE_SELECTIONS), allowMultipleAdaptiveSelections);

      putSelectionOverridesToBundle(bundle, selectionOverrides);
      // Only true values are put into rendererDisabledFlags.
      bundle.putIntArray(
          keyForField(FIELD_RENDERER_DISABLED_INDICES),
          getKeysFromSparseBooleanArray(rendererDisabledFlags));

      return bundle;
    }

    /** Object that can restore {@code Parameters} from a {@link Bundle}. */
    public static final Creator<Parameters> CREATOR =
        bundle -> new ParametersBuilder(bundle).build();

    private static String keyForField(@FieldNumber int field) {
      return Integer.toString(field, Character.MAX_RADIX);
    }

    /**
     * Bundles selection overrides in 3 arrays of equal length. Each triplet of matching indices is:
     * the selection override (stored in a sparse array as they can be null), the trackGroupArray of
     * that override, the rendererIndex of that override.
     */
    private static void putSelectionOverridesToBundle(
        Bundle bundle,
        SparseArray<Map<TrackGroupArray, @NullableType SelectionOverride>> selectionOverrides) {
      ArrayList<Integer> rendererIndices = new ArrayList<>();
      ArrayList<TrackGroupArray> trackGroupArrays = new ArrayList<>();
      SparseArray<SelectionOverride> selections = new SparseArray<>();

      for (int i = 0; i < selectionOverrides.size(); i++) {
        int rendererIndex = selectionOverrides.keyAt(i);
        for (Map.Entry<TrackGroupArray, @NullableType SelectionOverride> override :
            selectionOverrides.valueAt(i).entrySet()) {
          @Nullable SelectionOverride selection = override.getValue();
          if (selection != null) {
            selections.put(trackGroupArrays.size(), selection);
          }
          trackGroupArrays.add(override.getKey());
          rendererIndices.add(rendererIndex);
        }
        bundle.putIntArray(
            keyForField(FIELD_SELECTION_OVERRIDES_RENDERER_INDICES), Ints.toArray(rendererIndices));
        bundle.putParcelableArrayList(
            keyForField(FIELD_SELECTION_OVERRIDES_TRACK_GROUP_ARRAYS),
            BundleableUtil.toBundleArrayList(trackGroupArrays));
        bundle.putSparseParcelableArray(
            keyForField(FIELD_SELECTION_OVERRIDES), BundleableUtil.toBundleSparseArray(selections));
      }
    }

    private static int[] getKeysFromSparseBooleanArray(SparseBooleanArray sparseBooleanArray) {
      int[] keys = new int[sparseBooleanArray.size()];
      for (int i = 0; i < sparseBooleanArray.size(); i++) {
        keys[i] = sparseBooleanArray.keyAt(i);
      }
      return keys;
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
  public static final class SelectionOverride implements Bundleable {

    public final int groupIndex;
    public final int[] tracks;
    public final int length;
    public final @TrackSelection.Type int type;

    /**
     * Constructs a {@code SelectionOverride} to override tracks of a group.
     *
     * @param groupIndex The overriding track group index.
     * @param tracks The overriding track indices within the track group.
     */
    public SelectionOverride(int groupIndex, int... tracks) {
      this(groupIndex, tracks, TrackSelection.TYPE_UNSET);
    }

    /**
     * Constructs a {@code SelectionOverride} of the given type to override tracks of a group.
     *
     * @param groupIndex The overriding track group index.
     * @param tracks The overriding track indices within the track group.
     * @param type The type that will be returned from {@link TrackSelection#getType()}.
     */
    public SelectionOverride(int groupIndex, int[] tracks, @TrackSelection.Type int type) {
      this.groupIndex = groupIndex;
      this.tracks = Arrays.copyOf(tracks, tracks.length);
      this.length = tracks.length;
      this.type = type;
      Arrays.sort(this.tracks);
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

    // Bundleable implementation.

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
      FIELD_GROUP_INDEX,
      FIELD_TRACKS,
      FIELD_TRACK_TYPE,
    })
    private @interface FieldNumber {}

    private static final int FIELD_GROUP_INDEX = 0;
    private static final int FIELD_TRACKS = 1;
    private static final int FIELD_TRACK_TYPE = 2;

    @Override
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      bundle.putInt(keyForField(FIELD_GROUP_INDEX), groupIndex);
      bundle.putIntArray(keyForField(FIELD_TRACKS), tracks);
      bundle.putInt(keyForField(FIELD_TRACK_TYPE), type);
      return bundle;
    }

    /** Object that can restore {@code SelectionOverride} from a {@link Bundle}. */
    public static final Creator<SelectionOverride> CREATOR =
        bundle -> {
          int groupIndex = bundle.getInt(keyForField(FIELD_GROUP_INDEX), -1);
          @Nullable int[] tracks = bundle.getIntArray(keyForField(FIELD_TRACKS));
          int trackType = bundle.getInt(keyForField(FIELD_TRACK_TYPE), -1);
          Assertions.checkArgument(groupIndex >= 0 && trackType >= 0);
          Assertions.checkNotNull(tracks);
          return new SelectionOverride(groupIndex, tracks, trackType);
        };

    private static String keyForField(@FieldNumber int field) {
      return Integer.toString(field, Character.MAX_RADIX);
    }
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

  @Override
  public Parameters getParameters() {
    return parametersReference.get();
  }

  @Override
  public boolean isSetParametersSupported() {
    return true;
  }

  @Override
  public void setParameters(TrackSelectionParameters parameters) {
    if (parameters instanceof Parameters) {
      setParametersInternal((Parameters) parameters);
    }
    // Only add the fields of `TrackSelectionParameters` to `parameters`.
    Parameters mergedParameters =
        new ParametersBuilder(parametersReference.get()).set(parameters).build();
    setParametersInternal(mergedParameters);
  }

  /**
   * Atomically sets the provided parameters for track selection.
   *
   * @param parametersBuilder A builder from which to obtain the parameters for track selection.
   */
  public void setParameters(ParametersBuilder parametersBuilder) {
    setParametersInternal(parametersBuilder.build());
  }

  /** Returns a new {@link ParametersBuilder} initialized with the current selection parameters. */
  public ParametersBuilder buildUponParameters() {
    return getParameters().buildUpon();
  }

  /**
   * Atomically sets the provided {@link Parameters} for track selection.
   *
   * @param parameters The parameters for track selection.
   */
  private void setParametersInternal(Parameters parameters) {
    Assertions.checkNotNull(parameters);
    if (!parametersReference.getAndSet(parameters).equals(parameters)) {
      invalidate();
    }
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

    // Apply per track type overrides.
    SparseArray<Pair<TrackSelectionOverride, Integer>> applicableOverridesByTrackType =
        getApplicableOverrides(mappedTrackInfo, params);
    for (int i = 0; i < applicableOverridesByTrackType.size(); i++) {
      Pair<TrackSelectionOverride, Integer> overrideAndRendererIndex =
          applicableOverridesByTrackType.valueAt(i);
      applyTrackTypeOverride(
          mappedTrackInfo,
          definitions,
          /* trackType= */ applicableOverridesByTrackType.keyAt(i),
          /* override= */ overrideAndRendererIndex.first,
          /* overrideRendererIndex= */ overrideAndRendererIndex.second);
    }

    // Apply legacy per renderer overrides.
    for (int i = 0; i < rendererCount; i++) {
      if (hasLegacyRendererOverride(mappedTrackInfo, params, /* rendererIndex= */ i)) {
        definitions[i] = getLegacyRendererOverride(mappedTrackInfo, params, /* rendererIndex= */ i);
      }
    }

    // Disable renderers if needed.
    for (int i = 0; i < rendererCount; i++) {
      if (isRendererDisabled(mappedTrackInfo, params, /* rendererIndex= */ i)) {
        definitions[i] = null;
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
      @C.TrackType int rendererType = mappedTrackInfo.getRendererType(i);
      boolean forceRendererDisabled =
          params.getRendererDisabled(i) || params.disabledTrackTypes.contains(rendererType);
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

  private boolean isRendererDisabled(
      MappedTrackInfo mappedTrackInfo, Parameters params, int rendererIndex) {
    @C.TrackType int rendererType = mappedTrackInfo.getRendererType(rendererIndex);
    return params.getRendererDisabled(rendererIndex)
        || params.disabledTrackTypes.contains(rendererType);
  }

  @SuppressWarnings("deprecation") // Calling deprecated hasSelectionOverride.
  private boolean hasLegacyRendererOverride(
      MappedTrackInfo mappedTrackInfo, Parameters params, int rendererIndex) {
    TrackGroupArray rendererTrackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
    return params.hasSelectionOverride(rendererIndex, rendererTrackGroups);
  }

  @SuppressWarnings("deprecation") // Calling deprecated getSelectionOverride.
  private ExoTrackSelection.@NullableType Definition getLegacyRendererOverride(
      MappedTrackInfo mappedTrackInfo, Parameters params, int rendererIndex) {
    TrackGroupArray rendererTrackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
    @Nullable
    SelectionOverride override = params.getSelectionOverride(rendererIndex, rendererTrackGroups);
    if (override == null) {
      return null;
    }
    return new ExoTrackSelection.Definition(
        rendererTrackGroups.get(override.groupIndex), override.tracks, override.type);
  }

  /**
   * Returns applicable overrides. Mapping from track type to a pair of override and renderer index
   * for this override.
   */
  private SparseArray<Pair<TrackSelectionOverride, Integer>> getApplicableOverrides(
      MappedTrackInfo mappedTrackInfo, Parameters params) {
    SparseArray<Pair<TrackSelectionOverride, Integer>> applicableOverrides = new SparseArray<>();
    // Iterate through all existing track groups to ensure only overrides for those groups are used.
    int rendererCount = mappedTrackInfo.getRendererCount();
    for (int rendererIndex = 0; rendererIndex < rendererCount; rendererIndex++) {
      TrackGroupArray rendererTrackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
      for (int j = 0; j < rendererTrackGroups.length; j++) {
        maybeUpdateApplicableOverrides(
            applicableOverrides,
            params.trackSelectionOverrides.getOverride(rendererTrackGroups.get(j)),
            rendererIndex);
      }
    }
    // Also iterate unmapped groups to see if they have overrides.
    TrackGroupArray unmappedGroups = mappedTrackInfo.getUnmappedTrackGroups();
    for (int i = 0; i < unmappedGroups.length; i++) {
      maybeUpdateApplicableOverrides(
          applicableOverrides,
          params.trackSelectionOverrides.getOverride(unmappedGroups.get(i)),
          /* rendererIndex= */ C.INDEX_UNSET);
    }
    return applicableOverrides;
  }

  private void maybeUpdateApplicableOverrides(
      SparseArray<Pair<TrackSelectionOverride, Integer>> applicableOverrides,
      @Nullable TrackSelectionOverride override,
      int rendererIndex) {
    if (override == null) {
      return;
    }
    @C.TrackType int trackType = override.getTrackType();
    @Nullable
    Pair<TrackSelectionOverride, Integer> existingOverride = applicableOverrides.get(trackType);
    if (existingOverride == null || existingOverride.first.trackIndices.isEmpty()) {
      // We only need to choose one non-empty override per type.
      applicableOverrides.put(trackType, Pair.create(override, rendererIndex));
    }
  }

  private void applyTrackTypeOverride(
      MappedTrackInfo mappedTrackInfo,
      ExoTrackSelection.@NullableType Definition[] definitions,
      @C.TrackType int trackType,
      TrackSelectionOverride override,
      int overrideRendererIndex) {
    for (int i = 0; i < definitions.length; i++) {
      if (overrideRendererIndex == i) {
        definitions[i] =
            new ExoTrackSelection.Definition(
                override.trackGroup, Ints.toArray(override.trackIndices));
      } else if (mappedTrackInfo.getRendererType(i) == trackType) {
        // Disable other renderers of the same type.
        definitions[i] = null;
      }
    }
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
