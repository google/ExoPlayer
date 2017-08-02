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
package com.google.android.exoplayer2.demo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.SelectionOverride;
import com.google.android.exoplayer2.trackselection.RandomTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import java.util.Arrays;

/**
 * Helper class for displaying track selection dialogs.
 */
/* package */ final class TrackSelectionHelper implements View.OnClickListener,
    DialogInterface.OnClickListener {

  private static final TrackSelection.Factory FIXED_FACTORY = new FixedTrackSelection.Factory();
  private static final TrackSelection.Factory RANDOM_FACTORY = new RandomTrackSelection.Factory();

  private final MappingTrackSelector selector;
  private final TrackSelection.Factory adaptiveTrackSelectionFactory;

  private MappedTrackInfo trackInfo;
  private int rendererIndex;
  private TrackGroupArray trackGroups;
  private boolean[] trackGroupsAdaptive;
  private boolean isDisabled;
  private SelectionOverride override;

  private CheckedTextView disableView;
  private CheckedTextView defaultView;
  private CheckedTextView enableRandomAdaptationView;
  private CheckedTextView[][] trackViews;

  /**
   * @param selector The track selector.
   * @param adaptiveTrackSelectionFactory A factory for adaptive {@link TrackSelection}s, or null
   *     if the selection helper should not support adaptive tracks.
   */
  public TrackSelectionHelper(MappingTrackSelector selector,
      TrackSelection.Factory adaptiveTrackSelectionFactory) {
    this.selector = selector;
    this.adaptiveTrackSelectionFactory = adaptiveTrackSelectionFactory;
  }

  /**
   * Shows the selection dialog for a given renderer.
   *
   * @param activity The parent activity.
   * @param title The dialog's title.
   * @param trackInfo The current track information.
   * @param rendererIndex The index of the renderer.
   */
  public void showSelectionDialog(Activity activity, CharSequence title, MappedTrackInfo trackInfo,
      int rendererIndex) {
    this.trackInfo = trackInfo;
    this.rendererIndex = rendererIndex;

    trackGroups = trackInfo.getTrackGroups(rendererIndex);
    trackGroupsAdaptive = new boolean[trackGroups.length];
    for (int i = 0; i < trackGroups.length; i++) {
      trackGroupsAdaptive[i] = adaptiveTrackSelectionFactory != null
          && trackInfo.getAdaptiveSupport(rendererIndex, i, false)
              != RendererCapabilities.ADAPTIVE_NOT_SUPPORTED
          && trackGroups.get(i).length > 1;
    }
    isDisabled = selector.getRendererDisabled(rendererIndex);
    override = selector.getSelectionOverride(rendererIndex, trackGroups);

    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    builder.setTitle(title)
        .setView(buildView(builder.getContext()))
        .setPositiveButton(android.R.string.ok, this)
        .setNegativeButton(android.R.string.cancel, null)
        .create()
        .show();
  }

  @SuppressLint("InflateParams")
  private View buildView(Context context) {
    LayoutInflater inflater = LayoutInflater.from(context);
    View view = inflater.inflate(R.layout.track_selection_dialog, null);
    ViewGroup root = (ViewGroup) view.findViewById(R.id.root);

    TypedArray attributeArray = context.getTheme().obtainStyledAttributes(
        new int[] {android.R.attr.selectableItemBackground});
    int selectableItemBackgroundResourceId = attributeArray.getResourceId(0, 0);
    attributeArray.recycle();

    // View for disabling the renderer.
    disableView = (CheckedTextView) inflater.inflate(
        android.R.layout.simple_list_item_single_choice, root, false);
    disableView.setBackgroundResource(selectableItemBackgroundResourceId);
    disableView.setText(R.string.selection_disabled);
    disableView.setFocusable(true);
    disableView.setOnClickListener(this);
    root.addView(disableView);

    // View for clearing the override to allow the selector to use its default selection logic.
    defaultView = (CheckedTextView) inflater.inflate(
        android.R.layout.simple_list_item_single_choice, root, false);
    defaultView.setBackgroundResource(selectableItemBackgroundResourceId);
    defaultView.setText(R.string.selection_default);
    defaultView.setFocusable(true);
    defaultView.setOnClickListener(this);
    root.addView(inflater.inflate(R.layout.list_divider, root, false));
    root.addView(defaultView);

    // Per-track views.
    boolean haveAdaptiveTracks = false;
    trackViews = new CheckedTextView[trackGroups.length][];
    for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
      TrackGroup group = trackGroups.get(groupIndex);
      boolean groupIsAdaptive = trackGroupsAdaptive[groupIndex];
      haveAdaptiveTracks |= groupIsAdaptive;
      trackViews[groupIndex] = new CheckedTextView[group.length];
      for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
        if (trackIndex == 0) {
          root.addView(inflater.inflate(R.layout.list_divider, root, false));
        }
        int trackViewLayoutId = groupIsAdaptive ? android.R.layout.simple_list_item_multiple_choice
            : android.R.layout.simple_list_item_single_choice;
        CheckedTextView trackView = (CheckedTextView) inflater.inflate(
            trackViewLayoutId, root, false);
        trackView.setBackgroundResource(selectableItemBackgroundResourceId);
        trackView.setText(DemoUtil.buildTrackName(group.getFormat(trackIndex)));
        if (trackInfo.getTrackFormatSupport(rendererIndex, groupIndex, trackIndex)
            == RendererCapabilities.FORMAT_HANDLED) {
          trackView.setFocusable(true);
          trackView.setTag(Pair.create(groupIndex, trackIndex));
          trackView.setOnClickListener(this);
        } else {
          trackView.setFocusable(false);
          trackView.setEnabled(false);
        }
        trackViews[groupIndex][trackIndex] = trackView;
        root.addView(trackView);
      }
    }

    if (haveAdaptiveTracks) {
      // View for using random adaptation.
      enableRandomAdaptationView = (CheckedTextView) inflater.inflate(
          android.R.layout.simple_list_item_multiple_choice, root, false);
      enableRandomAdaptationView.setBackgroundResource(selectableItemBackgroundResourceId);
      enableRandomAdaptationView.setText(R.string.enable_random_adaptation);
      enableRandomAdaptationView.setOnClickListener(this);
      root.addView(inflater.inflate(R.layout.list_divider, root, false));
      root.addView(enableRandomAdaptationView);
    }

    updateViews();
    return view;
  }

  private void updateViews() {
    disableView.setChecked(isDisabled);
    defaultView.setChecked(!isDisabled && override == null);
    for (int i = 0; i < trackViews.length; i++) {
      for (int j = 0; j < trackViews[i].length; j++) {
        trackViews[i][j].setChecked(override != null && override.groupIndex == i
            && override.containsTrack(j));
      }
    }
    if (enableRandomAdaptationView != null) {
      boolean enableView = !isDisabled && override != null && override.length > 1;
      enableRandomAdaptationView.setEnabled(enableView);
      enableRandomAdaptationView.setFocusable(enableView);
      if (enableView) {
        enableRandomAdaptationView.setChecked(!isDisabled
            && override.factory instanceof RandomTrackSelection.Factory);
      }
    }
  }

  // DialogInterface.OnClickListener

  @Override
  public void onClick(DialogInterface dialog, int which) {
    selector.setRendererDisabled(rendererIndex, isDisabled);
    if (override != null) {
      selector.setSelectionOverride(rendererIndex, trackGroups, override);
    } else {
      selector.clearSelectionOverrides(rendererIndex);
    }
  }

  // View.OnClickListener

  @Override
  public void onClick(View view) {
    if (view == disableView) {
      isDisabled = true;
      override = null;
    } else if (view == defaultView) {
      isDisabled = false;
      override = null;
    } else if (view == enableRandomAdaptationView) {
      setOverride(override.groupIndex, override.tracks, !enableRandomAdaptationView.isChecked());
    } else {
      isDisabled = false;
      @SuppressWarnings("unchecked")
      Pair<Integer, Integer> tag = (Pair<Integer, Integer>) view.getTag();
      int groupIndex = tag.first;
      int trackIndex = tag.second;
      if (!trackGroupsAdaptive[groupIndex] || override == null
          || override.groupIndex != groupIndex) {
        override = new SelectionOverride(FIXED_FACTORY, groupIndex, trackIndex);
      } else {
        // The group being modified is adaptive and we already have a non-null override.
        boolean isEnabled = ((CheckedTextView) view).isChecked();
        int overrideLength = override.length;
        if (isEnabled) {
          // Remove the track from the override.
          if (overrideLength == 1) {
            // The last track is being removed, so the override becomes empty.
            override = null;
            isDisabled = true;
          } else {
            setOverride(groupIndex, getTracksRemoving(override, trackIndex),
                enableRandomAdaptationView.isChecked());
          }
        } else {
          // Add the track to the override.
          setOverride(groupIndex, getTracksAdding(override, trackIndex),
              enableRandomAdaptationView.isChecked());
        }
      }
    }
    // Update the views with the new state.
    updateViews();
  }

  private void setOverride(int group, int[] tracks, boolean enableRandomAdaptation) {
    TrackSelection.Factory factory = tracks.length == 1 ? FIXED_FACTORY
        : (enableRandomAdaptation ? RANDOM_FACTORY : adaptiveTrackSelectionFactory);
    override = new SelectionOverride(factory, group, tracks);
  }

  // Track array manipulation.

  private static int[] getTracksAdding(SelectionOverride override, int addedTrack) {
    int[] tracks = override.tracks;
    tracks = Arrays.copyOf(tracks, tracks.length + 1);
    tracks[tracks.length - 1] = addedTrack;
    return tracks;
  }

  private static int[] getTracksRemoving(SelectionOverride override, int removedTrack) {
    int[] tracks = new int[override.length - 1];
    int trackCount = 0;
    for (int i = 0; i < tracks.length + 1; i++) {
      int track = override.tracks[i];
      if (track != removedTrack) {
        tracks[trackCount++] = track;
      }
    }
    return tracks;
  }

}
