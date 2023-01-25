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
package com.google.android.exoplayer2.ui;

import static com.google.android.exoplayer2.Player.COMMAND_GET_TRACKS;
import static com.google.android.exoplayer2.Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Builder for a dialog with a {@link TrackSelectionView}. */
public final class TrackSelectionDialogBuilder {

  /** Callback which is invoked when a track selection has been made. */
  public interface DialogCallback {

    /**
     * Called when tracks are selected.
     *
     * @param isDisabled Whether the disabled option is selected.
     * @param overrides The selected track overrides.
     */
    void onTracksSelected(boolean isDisabled, Map<TrackGroup, TrackSelectionOverride> overrides);
  }

  private final Context context;
  private final CharSequence title;
  private final List<Tracks.Group> trackGroups;
  private final DialogCallback callback;

  @StyleRes private int themeResId;
  private boolean allowAdaptiveSelections;
  private boolean allowMultipleOverrides;
  private boolean showDisableOption;
  @Nullable private TrackNameProvider trackNameProvider;
  private boolean isDisabled;
  private ImmutableMap<TrackGroup, TrackSelectionOverride> overrides;
  @Nullable private Comparator<Format> trackFormatComparator;

  /**
   * Creates a builder for a track selection dialog.
   *
   * @param context The context of the dialog.
   * @param title The title of the dialog.
   * @param trackGroups The {@link Tracks.Group track groups}.
   * @param callback The {@link DialogCallback} invoked when a track selection has been made.
   */
  public TrackSelectionDialogBuilder(
      Context context,
      CharSequence title,
      List<Tracks.Group> trackGroups,
      DialogCallback callback) {
    this.context = context;
    this.title = title;
    this.trackGroups = ImmutableList.copyOf(trackGroups);
    this.callback = callback;
    overrides = ImmutableMap.of();
  }

  /**
   * Creates a builder for a track selection dialog.
   *
   * @param context The context of the dialog.
   * @param title The title of the dialog.
   * @param player The {@link Player} whose tracks should be selected.
   * @param trackType The type of tracks to show for selection.
   */
  public TrackSelectionDialogBuilder(
      Context context, CharSequence title, Player player, @C.TrackType int trackType) {
    this.context = context;
    this.title = title;
    Tracks tracks =
        player.isCommandAvailable(COMMAND_GET_TRACKS) ? player.getCurrentTracks() : Tracks.EMPTY;
    List<Tracks.Group> allTrackGroups = tracks.getGroups();
    trackGroups = new ArrayList<>();
    for (int i = 0; i < allTrackGroups.size(); i++) {
      Tracks.Group trackGroup = allTrackGroups.get(i);
      if (trackGroup.getType() == trackType) {
        trackGroups.add(trackGroup);
      }
    }
    overrides = player.getTrackSelectionParameters().overrides;
    callback =
        (isDisabled, overrides) -> {
          if (!player.isCommandAvailable(COMMAND_SET_TRACK_SELECTION_PARAMETERS)) {
            return;
          }
          TrackSelectionParameters.Builder parametersBuilder =
              player.getTrackSelectionParameters().buildUpon();
          parametersBuilder.setTrackTypeDisabled(trackType, isDisabled);
          parametersBuilder.clearOverridesOfType(trackType);
          for (TrackSelectionOverride override : overrides.values()) {
            parametersBuilder.addOverride(override);
          }
          player.setTrackSelectionParameters(parametersBuilder.build());
        };
  }

  /**
   * Sets the resource ID of the theme used to inflate this dialog.
   *
   * @param themeResId The resource ID of the theme.
   * @return This builder, for convenience.
   */
  public TrackSelectionDialogBuilder setTheme(@StyleRes int themeResId) {
    this.themeResId = themeResId;
    return this;
  }

  /**
   * Sets whether the selection is initially shown as disabled.
   *
   * @param isDisabled Whether the selection is initially shown as disabled.
   * @return This builder, for convenience.
   */
  public TrackSelectionDialogBuilder setIsDisabled(boolean isDisabled) {
    this.isDisabled = isDisabled;
    return this;
  }

  /**
   * Sets the single initial override.
   *
   * @param override The initial override, or {@code null} for no override.
   * @return This builder, for convenience.
   */
  public TrackSelectionDialogBuilder setOverride(@Nullable TrackSelectionOverride override) {
    return setOverrides(
        override == null
            ? Collections.emptyMap()
            : ImmutableMap.of(override.mediaTrackGroup, override));
  }

  /**
   * Sets the initial track overrides. Any overrides that do not correspond to track groups that
   * were passed to the constructor will be ignored. If {@link #setAllowMultipleOverrides(boolean)}
   * hasn't been set to {@code true} then all but one override will be ignored. The retained
   * override will be the one whose track group was first in the list of track groups passed to the
   * constructor.
   *
   * @param overrides The initially selected track overrides.
   * @return This builder, for convenience.
   */
  public TrackSelectionDialogBuilder setOverrides(
      Map<TrackGroup, TrackSelectionOverride> overrides) {
    this.overrides = ImmutableMap.copyOf(overrides);
    return this;
  }

  /**
   * Sets whether adaptive selections (consisting of more than one track) can be made.
   *
   * <p>For the selection view to enable adaptive selection it is necessary both for this feature to
   * be enabled, and for the target renderer to support adaptation between the available tracks.
   *
   * @param allowAdaptiveSelections Whether adaptive selection is enabled.
   * @return This builder, for convenience.
   */
  public TrackSelectionDialogBuilder setAllowAdaptiveSelections(boolean allowAdaptiveSelections) {
    this.allowAdaptiveSelections = allowAdaptiveSelections;
    return this;
  }

  /**
   * Sets whether multiple overrides can be set and selected, i.e. tracks from multiple track groups
   * can be selected.
   *
   * @param allowMultipleOverrides Whether multiple track selection overrides are allowed.
   * @return This builder, for convenience.
   */
  public TrackSelectionDialogBuilder setAllowMultipleOverrides(boolean allowMultipleOverrides) {
    this.allowMultipleOverrides = allowMultipleOverrides;
    return this;
  }

  /**
   * Sets whether an option is available for disabling the renderer.
   *
   * @param showDisableOption Whether the disable option is shown.
   * @return This builder, for convenience.
   */
  public TrackSelectionDialogBuilder setShowDisableOption(boolean showDisableOption) {
    this.showDisableOption = showDisableOption;
    return this;
  }

  /**
   * Sets a {@link Comparator} used to determine the display order of the tracks within each track
   * group.
   *
   * @param trackFormatComparator The comparator, or {@code null} to use the original order.
   */
  public void setTrackFormatComparator(@Nullable Comparator<Format> trackFormatComparator) {
    this.trackFormatComparator = trackFormatComparator;
  }

  /**
   * Sets the {@link TrackNameProvider} used to generate the user visible name of each track and
   * updates the view with track names queried from the specified provider.
   *
   * @param trackNameProvider The {@link TrackNameProvider} to use, or null to use the default.
   */
  public TrackSelectionDialogBuilder setTrackNameProvider(
      @Nullable TrackNameProvider trackNameProvider) {
    this.trackNameProvider = trackNameProvider;
    return this;
  }

  /** Builds the dialog. */
  public Dialog build() {
    @Nullable Dialog dialog = buildForAndroidX();
    return dialog == null ? buildForPlatform() : dialog;
  }

  private Dialog buildForPlatform() {
    AlertDialog.Builder builder = new AlertDialog.Builder(context, themeResId);

    // Inflate with the builder's context to ensure the correct style is used.
    LayoutInflater dialogInflater = LayoutInflater.from(builder.getContext());
    View dialogView = dialogInflater.inflate(R.layout.exo_track_selection_dialog, /* root= */ null);
    Dialog.OnClickListener okClickListener = setUpDialogView(dialogView);

    return builder
        .setTitle(title)
        .setView(dialogView)
        .setPositiveButton(android.R.string.ok, okClickListener)
        .setNegativeButton(android.R.string.cancel, null)
        .create();
  }

  // Reflection calls can't verify null safety of return values or parameters.
  @SuppressWarnings("nullness:argument")
  @Nullable
  private Dialog buildForAndroidX() {
    try {
      // This method uses reflection to avoid a dependency on AndroidX appcompat that adds 800KB to
      // the APK size even with shrinking. See https://issuetracker.google.com/161514204.
      Class<?> builderClazz = Class.forName("androidx.appcompat.app.AlertDialog$Builder");
      Constructor<?> builderConstructor = builderClazz.getConstructor(Context.class, int.class);
      Object builder = builderConstructor.newInstance(context, themeResId);

      // Inflate with the builder's context to ensure the correct style is used.
      Context builderContext = (Context) builderClazz.getMethod("getContext").invoke(builder);
      LayoutInflater dialogInflater = LayoutInflater.from(builderContext);
      View dialogView =
          dialogInflater.inflate(R.layout.exo_track_selection_dialog, /* root= */ null);
      Dialog.OnClickListener okClickListener = setUpDialogView(dialogView);

      builderClazz.getMethod("setTitle", CharSequence.class).invoke(builder, title);
      builderClazz.getMethod("setView", View.class).invoke(builder, dialogView);
      builderClazz
          .getMethod("setPositiveButton", int.class, DialogInterface.OnClickListener.class)
          .invoke(builder, android.R.string.ok, okClickListener);
      builderClazz
          .getMethod("setNegativeButton", int.class, DialogInterface.OnClickListener.class)
          .invoke(builder, android.R.string.cancel, null);
      return (Dialog) builderClazz.getMethod("create").invoke(builder);
    } catch (ClassNotFoundException e) {
      // Expected if the AndroidX compat library is not available.
      return null;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Dialog.OnClickListener setUpDialogView(View dialogView) {
    TrackSelectionView selectionView = dialogView.findViewById(R.id.exo_track_selection_view);
    selectionView.setAllowMultipleOverrides(allowMultipleOverrides);
    selectionView.setAllowAdaptiveSelections(allowAdaptiveSelections);
    selectionView.setShowDisableOption(showDisableOption);
    if (trackNameProvider != null) {
      selectionView.setTrackNameProvider(trackNameProvider);
    }
    selectionView.init(
        trackGroups, isDisabled, overrides, trackFormatComparator, /* listener= */ null);
    return (dialog, which) ->
        callback.onTracksSelected(selectionView.getIsDisabled(), selectionView.getOverrides());
  }
}
