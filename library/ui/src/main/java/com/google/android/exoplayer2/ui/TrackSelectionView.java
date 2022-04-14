/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;
import androidx.annotation.AttrRes;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride;
import com.google.android.exoplayer2.util.Assertions;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A view for making track selections. */
public class TrackSelectionView extends LinearLayout {

  /** Listener for changes to the selected tracks. */
  public interface TrackSelectionListener {

    /**
     * Called when the selected tracks changed.
     *
     * @param isDisabled Whether the disabled option is selected.
     * @param overrides The selected track overrides.
     */
    void onTrackSelectionChanged(
        boolean isDisabled, Map<TrackGroup, TrackSelectionOverride> overrides);
  }

  /**
   * Returns the subset of {@code overrides} that apply to the specified {@code trackGroups}. If
   * {@code allowMultipleOverrides} is {@code} then at most one override is retained, which will be
   * the one whose track group is first in {@code trackGroups}.
   *
   * @param overrides The overrides to filter.
   * @param trackGroups The track groups whose overrides should be retained.
   * @param allowMultipleOverrides Whether more than one override can be retained.
   * @return The filtered overrides.
   */
  public static Map<TrackGroup, TrackSelectionOverride> filterOverrides(
      Map<TrackGroup, TrackSelectionOverride> overrides,
      List<Tracks.Group> trackGroups,
      boolean allowMultipleOverrides) {
    HashMap<TrackGroup, TrackSelectionOverride> filteredOverrides = new HashMap<>();
    for (int i = 0; i < trackGroups.size(); i++) {
      Tracks.Group trackGroup = trackGroups.get(i);
      @Nullable TrackSelectionOverride override = overrides.get(trackGroup.getMediaTrackGroup());
      if (override != null && (allowMultipleOverrides || filteredOverrides.isEmpty())) {
        filteredOverrides.put(override.mediaTrackGroup, override);
      }
    }
    return filteredOverrides;
  }

  private final int selectableItemBackgroundResourceId;
  private final LayoutInflater inflater;
  private final CheckedTextView disableView;
  private final CheckedTextView defaultView;
  private final ComponentListener componentListener;
  private final List<Tracks.Group> trackGroups;
  private final Map<TrackGroup, TrackSelectionOverride> overrides;

  private boolean allowAdaptiveSelections;
  private boolean allowMultipleOverrides;

  private TrackNameProvider trackNameProvider;
  private CheckedTextView[][] trackViews;

  private boolean isDisabled;
  @Nullable private Comparator<TrackInfo> trackInfoComparator;
  @Nullable private TrackSelectionListener listener;

  /** Creates a track selection view. */
  public TrackSelectionView(Context context) {
    this(context, null);
  }

  /** Creates a track selection view. */
  public TrackSelectionView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  /** Creates a track selection view. */
  @SuppressWarnings("nullness")
  public TrackSelectionView(
      Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setOrientation(LinearLayout.VERTICAL);
    // Don't save view hierarchy as it needs to be reinitialized with a call to init.
    setSaveFromParentEnabled(false);

    TypedArray attributeArray =
        context
            .getTheme()
            .obtainStyledAttributes(new int[] {android.R.attr.selectableItemBackground});
    selectableItemBackgroundResourceId = attributeArray.getResourceId(0, 0);
    attributeArray.recycle();

    inflater = LayoutInflater.from(context);
    componentListener = new ComponentListener();
    trackNameProvider = new DefaultTrackNameProvider(getResources());
    trackGroups = new ArrayList<>();
    overrides = new HashMap<>();

    // View for disabling the renderer.
    disableView =
        (CheckedTextView)
            inflater.inflate(android.R.layout.simple_list_item_single_choice, this, false);
    disableView.setBackgroundResource(selectableItemBackgroundResourceId);
    disableView.setText(R.string.exo_track_selection_none);
    disableView.setEnabled(false);
    disableView.setFocusable(true);
    disableView.setOnClickListener(componentListener);
    disableView.setVisibility(View.GONE);
    addView(disableView);
    // Divider view.
    addView(inflater.inflate(R.layout.exo_list_divider, this, false));
    // View for clearing the override to allow the selector to use its default selection logic.
    defaultView =
        (CheckedTextView)
            inflater.inflate(android.R.layout.simple_list_item_single_choice, this, false);
    defaultView.setBackgroundResource(selectableItemBackgroundResourceId);
    defaultView.setText(R.string.exo_track_selection_auto);
    defaultView.setEnabled(false);
    defaultView.setFocusable(true);
    defaultView.setOnClickListener(componentListener);
    addView(defaultView);
  }

  /**
   * Sets whether adaptive selections (consisting of more than one track) can be made using this
   * selection view.
   *
   * <p>For the view to enable adaptive selection it is necessary both for this feature to be
   * enabled, and for the target renderer to support adaptation between the available tracks.
   *
   * @param allowAdaptiveSelections Whether adaptive selection is enabled.
   */
  public void setAllowAdaptiveSelections(boolean allowAdaptiveSelections) {
    if (this.allowAdaptiveSelections != allowAdaptiveSelections) {
      this.allowAdaptiveSelections = allowAdaptiveSelections;
      updateViews();
    }
  }

  /**
   * Sets whether tracks from multiple track groups can be selected. This results in multiple {@link
   * TrackSelectionOverride TrackSelectionOverrides} being returned by {@link #getOverrides()}.
   *
   * @param allowMultipleOverrides Whether tracks from multiple track groups can be selected.
   */
  public void setAllowMultipleOverrides(boolean allowMultipleOverrides) {
    if (this.allowMultipleOverrides != allowMultipleOverrides) {
      this.allowMultipleOverrides = allowMultipleOverrides;
      if (!allowMultipleOverrides && overrides.size() > 1) {
        // Re-filter the overrides to retain only one of them.
        Map<TrackGroup, TrackSelectionOverride> filteredOverrides =
            filterOverrides(overrides, trackGroups, /* allowMultipleOverrides= */ false);
        overrides.clear();
        overrides.putAll(filteredOverrides);
      }
      updateViews();
    }
  }

  /**
   * Sets whether the disabled option can be selected.
   *
   * @param showDisableOption Whether the disabled option can be selected.
   */
  public void setShowDisableOption(boolean showDisableOption) {
    disableView.setVisibility(showDisableOption ? View.VISIBLE : View.GONE);
  }

  /**
   * Sets the {@link TrackNameProvider} used to generate the user visible name of each track and
   * updates the view with track names queried from the specified provider.
   *
   * @param trackNameProvider The {@link TrackNameProvider} to use.
   */
  public void setTrackNameProvider(TrackNameProvider trackNameProvider) {
    this.trackNameProvider = Assertions.checkNotNull(trackNameProvider);
    updateViews();
  }

  /**
   * Initialize the view to select tracks from a specified list of track groups.
   *
   * @param trackGroups The {@link Tracks.Group track groups}.
   * @param isDisabled Whether the disabled option should be initially selected.
   * @param overrides The initially selected track overrides. Any overrides that do not correspond
   *     to track groups in {@code trackGroups} will be ignored. If {@link
   *     #setAllowMultipleOverrides(boolean)} hasn't been set to {@code true} then all but one
   *     override will be ignored. The retained override will be the one whose track group is first
   *     in {@code trackGroups}.
   * @param trackFormatComparator An optional comparator used to determine the display order of the
   *     tracks within each track group.
   * @param listener An optional listener to receive selection updates.
   */
  public void init(
      List<Tracks.Group> trackGroups,
      boolean isDisabled,
      Map<TrackGroup, TrackSelectionOverride> overrides,
      @Nullable Comparator<Format> trackFormatComparator,
      @Nullable TrackSelectionListener listener) {
    this.isDisabled = isDisabled;
    this.trackInfoComparator =
        trackFormatComparator == null
            ? null
            : (o1, o2) -> trackFormatComparator.compare(o1.getFormat(), o2.getFormat());
    this.listener = listener;

    this.trackGroups.clear();
    this.trackGroups.addAll(trackGroups);
    this.overrides.clear();
    this.overrides.putAll(filterOverrides(overrides, trackGroups, allowMultipleOverrides));
    updateViews();
  }

  /** Returns whether the disabled option is selected. */
  public boolean getIsDisabled() {
    return isDisabled;
  }

  /** Returns the selected track overrides. */
  public Map<TrackGroup, TrackSelectionOverride> getOverrides() {
    return overrides;
  }

  // Private methods.

  private void updateViews() {
    // Remove previous per-track views.
    for (int i = getChildCount() - 1; i >= 3; i--) {
      removeViewAt(i);
    }

    if (trackGroups.isEmpty()) {
      // The view is not initialized.
      disableView.setEnabled(false);
      defaultView.setEnabled(false);
      return;
    }
    disableView.setEnabled(true);
    defaultView.setEnabled(true);

    // Add per-track views.
    trackViews = new CheckedTextView[trackGroups.size()][];
    boolean enableMultipleChoiceForMultipleOverrides = shouldEnableMultiGroupSelection();
    for (int trackGroupIndex = 0; trackGroupIndex < trackGroups.size(); trackGroupIndex++) {
      Tracks.Group trackGroup = trackGroups.get(trackGroupIndex);
      boolean enableMultipleChoiceForAdaptiveSelections = shouldEnableAdaptiveSelection(trackGroup);
      trackViews[trackGroupIndex] = new CheckedTextView[trackGroup.length];

      TrackInfo[] trackInfos = new TrackInfo[trackGroup.length];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        trackInfos[trackIndex] = new TrackInfo(trackGroup, trackIndex);
      }
      if (trackInfoComparator != null) {
        Arrays.sort(trackInfos, trackInfoComparator);
      }

      for (int trackIndex = 0; trackIndex < trackInfos.length; trackIndex++) {
        if (trackIndex == 0) {
          addView(inflater.inflate(R.layout.exo_list_divider, this, false));
        }
        int trackViewLayoutId =
            enableMultipleChoiceForAdaptiveSelections || enableMultipleChoiceForMultipleOverrides
                ? android.R.layout.simple_list_item_multiple_choice
                : android.R.layout.simple_list_item_single_choice;
        CheckedTextView trackView =
            (CheckedTextView) inflater.inflate(trackViewLayoutId, this, false);
        trackView.setBackgroundResource(selectableItemBackgroundResourceId);
        trackView.setText(trackNameProvider.getTrackName(trackInfos[trackIndex].getFormat()));
        trackView.setTag(trackInfos[trackIndex]);
        if (trackGroup.isTrackSupported(trackIndex)) {
          trackView.setFocusable(true);
          trackView.setOnClickListener(componentListener);
        } else {
          trackView.setFocusable(false);
          trackView.setEnabled(false);
        }
        trackViews[trackGroupIndex][trackIndex] = trackView;
        addView(trackView);
      }
    }

    updateViewStates();
  }

  private void updateViewStates() {
    disableView.setChecked(isDisabled);
    defaultView.setChecked(!isDisabled && overrides.size() == 0);
    for (int i = 0; i < trackViews.length; i++) {
      @Nullable
      TrackSelectionOverride override = overrides.get(trackGroups.get(i).getMediaTrackGroup());
      for (int j = 0; j < trackViews[i].length; j++) {
        if (override != null) {
          TrackInfo trackInfo = (TrackInfo) Assertions.checkNotNull(trackViews[i][j].getTag());
          trackViews[i][j].setChecked(override.trackIndices.contains(trackInfo.trackIndex));
        } else {
          trackViews[i][j].setChecked(false);
        }
      }
    }
  }

  private void onClick(View view) {
    if (view == disableView) {
      onDisableViewClicked();
    } else if (view == defaultView) {
      onDefaultViewClicked();
    } else {
      onTrackViewClicked(view);
    }
    updateViewStates();
    if (listener != null) {
      listener.onTrackSelectionChanged(getIsDisabled(), getOverrides());
    }
  }

  private void onDisableViewClicked() {
    isDisabled = true;
    overrides.clear();
  }

  private void onDefaultViewClicked() {
    isDisabled = false;
    overrides.clear();
  }

  private void onTrackViewClicked(View view) {
    isDisabled = false;
    TrackInfo trackInfo = (TrackInfo) Assertions.checkNotNull(view.getTag());
    TrackGroup mediaTrackGroup = trackInfo.trackGroup.getMediaTrackGroup();
    int trackIndex = trackInfo.trackIndex;
    @Nullable TrackSelectionOverride override = overrides.get(mediaTrackGroup);
    if (override == null) {
      // Start new override.
      if (!allowMultipleOverrides && overrides.size() > 0) {
        // Removed other overrides if we don't allow multiple overrides.
        overrides.clear();
      }
      overrides.put(
          mediaTrackGroup,
          new TrackSelectionOverride(mediaTrackGroup, ImmutableList.of(trackIndex)));
    } else {
      // An existing override is being modified.
      ArrayList<Integer> trackIndices = new ArrayList<>(override.trackIndices);
      boolean isCurrentlySelected = ((CheckedTextView) view).isChecked();
      boolean isAdaptiveAllowed = shouldEnableAdaptiveSelection(trackInfo.trackGroup);
      boolean isUsingCheckBox = isAdaptiveAllowed || shouldEnableMultiGroupSelection();
      if (isCurrentlySelected && isUsingCheckBox) {
        // Remove the track from the override.
        trackIndices.remove((Integer) trackIndex);
        if (trackIndices.isEmpty()) {
          // The last track has been removed, so remove the whole override.
          overrides.remove(mediaTrackGroup);
        } else {
          overrides.put(mediaTrackGroup, new TrackSelectionOverride(mediaTrackGroup, trackIndices));
        }
      } else if (!isCurrentlySelected) {
        if (isAdaptiveAllowed) {
          // Add new track to adaptive override.
          trackIndices.add(trackIndex);
          overrides.put(mediaTrackGroup, new TrackSelectionOverride(mediaTrackGroup, trackIndices));
        } else {
          // Replace existing track in override.
          overrides.put(
              mediaTrackGroup,
              new TrackSelectionOverride(mediaTrackGroup, ImmutableList.of(trackIndex)));
        }
      }
    }
  }

  private boolean shouldEnableAdaptiveSelection(Tracks.Group trackGroup) {
    return allowAdaptiveSelections && trackGroup.isAdaptiveSupported();
  }

  private boolean shouldEnableMultiGroupSelection() {
    return allowMultipleOverrides && trackGroups.size() > 1;
  }

  // Internal classes.

  private class ComponentListener implements OnClickListener {

    @Override
    public void onClick(View view) {
      TrackSelectionView.this.onClick(view);
    }
  }

  private static final class TrackInfo {
    public final Tracks.Group trackGroup;
    public final int trackIndex;

    public TrackInfo(Tracks.Group trackGroup, int trackIndex) {
      this.trackGroup = trackGroup;
      this.trackIndex = trackIndex;
    }

    public Format getFormat() {
      return trackGroup.getTrackFormat(trackIndex);
    }
  }
}
