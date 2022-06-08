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
package com.google.android.exoplayer2.demo;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.ui.TrackSelectionView;
import com.google.android.material.tabs.TabLayout;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Dialog to select tracks. */
public final class TrackSelectionDialog extends DialogFragment {

  /** Called when tracks are selected. */
  public interface TrackSelectionListener {

    /**
     * Called when tracks are selected.
     *
     * @param trackSelectionParameters A {@link TrackSelectionParameters} representing the selected
     *     tracks. Any manual selections are defined by {@link
     *     TrackSelectionParameters#disabledTrackTypes} and {@link
     *     TrackSelectionParameters#overrides}.
     */
    void onTracksSelected(TrackSelectionParameters trackSelectionParameters);
  }

  public static final ImmutableList<Integer> SUPPORTED_TRACK_TYPES =
      ImmutableList.of(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT);

  private final SparseArray<TrackSelectionViewFragment> tabFragments;
  private final ArrayList<Integer> tabTrackTypes;

  private int titleId;
  private DialogInterface.OnClickListener onClickListener;
  private DialogInterface.OnDismissListener onDismissListener;

  /**
   * Returns whether a track selection dialog will have content to display if initialized with the
   * specified {@link Player}.
   */
  public static boolean willHaveContent(Player player) {
    return willHaveContent(player.getCurrentTracks());
  }

  /**
   * Returns whether a track selection dialog will have content to display if initialized with the
   * specified {@link Tracks}.
   */
  public static boolean willHaveContent(Tracks tracks) {
    for (Tracks.Group trackGroup : tracks.getGroups()) {
      if (SUPPORTED_TRACK_TYPES.contains(trackGroup.getType())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Creates a dialog for a given {@link Player}, whose parameters will be automatically updated
   * when tracks are selected.
   *
   * @param player The {@link Player}.
   * @param onDismissListener A {@link DialogInterface.OnDismissListener} to call when the dialog is
   *     dismissed.
   */
  public static TrackSelectionDialog createForPlayer(
      Player player, DialogInterface.OnDismissListener onDismissListener) {
    return createForTracksAndParameters(
        R.string.track_selection_title,
        player.getCurrentTracks(),
        player.getTrackSelectionParameters(),
        /* allowAdaptiveSelections= */ true,
        /* allowMultipleOverrides= */ false,
        player::setTrackSelectionParameters,
        onDismissListener);
  }

  /**
   * Creates a dialog for given {@link Tracks} and {@link TrackSelectionParameters}.
   *
   * @param titleId The resource id of the dialog title.
   * @param tracks The {@link Tracks} describing the tracks to display.
   * @param trackSelectionParameters The initial {@link TrackSelectionParameters}.
   * @param allowAdaptiveSelections Whether adaptive selections (consisting of more than one track)
   *     can be made.
   * @param allowMultipleOverrides Whether tracks from multiple track groups can be selected.
   * @param trackSelectionListener Called when tracks are selected.
   * @param onDismissListener {@link DialogInterface.OnDismissListener} called when the dialog is
   *     dismissed.
   */
  public static TrackSelectionDialog createForTracksAndParameters(
      int titleId,
      Tracks tracks,
      TrackSelectionParameters trackSelectionParameters,
      boolean allowAdaptiveSelections,
      boolean allowMultipleOverrides,
      TrackSelectionListener trackSelectionListener,
      DialogInterface.OnDismissListener onDismissListener) {
    TrackSelectionDialog trackSelectionDialog = new TrackSelectionDialog();
    trackSelectionDialog.init(
        tracks,
        trackSelectionParameters,
        titleId,
        allowAdaptiveSelections,
        allowMultipleOverrides,
        /* onClickListener= */ (dialog, which) -> {
          TrackSelectionParameters.Builder builder = trackSelectionParameters.buildUpon();
          for (int i = 0; i < SUPPORTED_TRACK_TYPES.size(); i++) {
            int trackType = SUPPORTED_TRACK_TYPES.get(i);
            builder.setTrackTypeDisabled(trackType, trackSelectionDialog.getIsDisabled(trackType));
            builder.clearOverridesOfType(trackType);
            Map<TrackGroup, TrackSelectionOverride> overrides =
                trackSelectionDialog.getOverrides(trackType);
            for (TrackSelectionOverride override : overrides.values()) {
              builder.addOverride(override);
            }
          }
          trackSelectionListener.onTracksSelected(builder.build());
        },
        onDismissListener);
    return trackSelectionDialog;
  }

  public TrackSelectionDialog() {
    tabFragments = new SparseArray<>();
    tabTrackTypes = new ArrayList<>();
    // Retain instance across activity re-creation to prevent losing access to init data.
    setRetainInstance(true);
  }

  private void init(
      Tracks tracks,
      TrackSelectionParameters trackSelectionParameters,
      int titleId,
      boolean allowAdaptiveSelections,
      boolean allowMultipleOverrides,
      DialogInterface.OnClickListener onClickListener,
      DialogInterface.OnDismissListener onDismissListener) {
    this.titleId = titleId;
    this.onClickListener = onClickListener;
    this.onDismissListener = onDismissListener;

    for (int i = 0; i < SUPPORTED_TRACK_TYPES.size(); i++) {
      @C.TrackType int trackType = SUPPORTED_TRACK_TYPES.get(i);
      ArrayList<Tracks.Group> trackGroups = new ArrayList<>();
      for (Tracks.Group trackGroup : tracks.getGroups()) {
        if (trackGroup.getType() == trackType) {
          trackGroups.add(trackGroup);
        }
      }
      if (!trackGroups.isEmpty()) {
        TrackSelectionViewFragment tabFragment = new TrackSelectionViewFragment();
        tabFragment.init(
            trackGroups,
            trackSelectionParameters.disabledTrackTypes.contains(trackType),
            trackSelectionParameters.overrides,
            allowAdaptiveSelections,
            allowMultipleOverrides);
        tabFragments.put(trackType, tabFragment);
        tabTrackTypes.add(trackType);
      }
    }
  }

  /**
   * Returns whether the disabled option is selected for the specified track type.
   *
   * @param trackType The track type.
   * @return Whether the disabled option is selected for the track type.
   */
  public boolean getIsDisabled(int trackType) {
    TrackSelectionViewFragment trackView = tabFragments.get(trackType);
    return trackView != null && trackView.isDisabled;
  }

  /**
   * Returns the selected track overrides for the specified track type.
   *
   * @param trackType The track type.
   * @return The track overrides for the track type.
   */
  public Map<TrackGroup, TrackSelectionOverride> getOverrides(int trackType) {
    TrackSelectionViewFragment trackView = tabFragments.get(trackType);
    return trackView == null ? Collections.emptyMap() : trackView.overrides;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    // We need to own the view to let tab layout work correctly on all API levels. We can't use
    // AlertDialog because it owns the view itself, so we use AppCompatDialog instead, themed using
    // the AlertDialog theme overlay with force-enabled title.
    AppCompatDialog dialog =
        new AppCompatDialog(getActivity(), R.style.TrackSelectionDialogThemeOverlay);
    dialog.setTitle(titleId);
    return dialog;
  }

  @Override
  public void onDismiss(DialogInterface dialog) {
    super.onDismiss(dialog);
    onDismissListener.onDismiss(dialog);
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View dialogView = inflater.inflate(R.layout.track_selection_dialog, container, false);
    TabLayout tabLayout = dialogView.findViewById(R.id.track_selection_dialog_tab_layout);
    ViewPager viewPager = dialogView.findViewById(R.id.track_selection_dialog_view_pager);
    Button cancelButton = dialogView.findViewById(R.id.track_selection_dialog_cancel_button);
    Button okButton = dialogView.findViewById(R.id.track_selection_dialog_ok_button);
    viewPager.setAdapter(new FragmentAdapter(getChildFragmentManager()));
    tabLayout.setupWithViewPager(viewPager);
    tabLayout.setVisibility(tabFragments.size() > 1 ? View.VISIBLE : View.GONE);
    cancelButton.setOnClickListener(view -> dismiss());
    okButton.setOnClickListener(
        view -> {
          onClickListener.onClick(getDialog(), DialogInterface.BUTTON_POSITIVE);
          dismiss();
        });
    return dialogView;
  }

  private static String getTrackTypeString(Resources resources, @C.TrackType int trackType) {
    switch (trackType) {
      case C.TRACK_TYPE_VIDEO:
        return resources.getString(R.string.exo_track_selection_title_video);
      case C.TRACK_TYPE_AUDIO:
        return resources.getString(R.string.exo_track_selection_title_audio);
      case C.TRACK_TYPE_TEXT:
        return resources.getString(R.string.exo_track_selection_title_text);
      default:
        throw new IllegalArgumentException();
    }
  }

  private final class FragmentAdapter extends FragmentPagerAdapter {

    public FragmentAdapter(FragmentManager fragmentManager) {
      super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
    }

    @Override
    public Fragment getItem(int position) {
      return tabFragments.get(tabTrackTypes.get(position));
    }

    @Override
    public int getCount() {
      return tabTrackTypes.size();
    }

    @Override
    public CharSequence getPageTitle(int position) {
      return getTrackTypeString(getResources(), tabTrackTypes.get(position));
    }
  }

  /** Fragment to show a track selection in tab of the track selection dialog. */
  public static final class TrackSelectionViewFragment extends Fragment
      implements TrackSelectionView.TrackSelectionListener {

    private List<Tracks.Group> trackGroups;
    private boolean allowAdaptiveSelections;
    private boolean allowMultipleOverrides;

    /* package */ boolean isDisabled;
    /* package */ Map<TrackGroup, TrackSelectionOverride> overrides;

    public TrackSelectionViewFragment() {
      // Retain instance across activity re-creation to prevent losing access to init data.
      setRetainInstance(true);
    }

    public void init(
        List<Tracks.Group> trackGroups,
        boolean isDisabled,
        Map<TrackGroup, TrackSelectionOverride> overrides,
        boolean allowAdaptiveSelections,
        boolean allowMultipleOverrides) {
      this.trackGroups = trackGroups;
      this.isDisabled = isDisabled;
      this.allowAdaptiveSelections = allowAdaptiveSelections;
      this.allowMultipleOverrides = allowMultipleOverrides;
      // TrackSelectionView does this filtering internally, but we need to do it here as well to
      // handle the case where the TrackSelectionView is never created.
      this.overrides =
          new HashMap<>(
              TrackSelectionView.filterOverrides(overrides, trackGroups, allowMultipleOverrides));
    }

    @Override
    public View onCreateView(
        LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState) {
      View rootView =
          inflater.inflate(
              R.layout.exo_track_selection_dialog, container, /* attachToRoot= */ false);
      TrackSelectionView trackSelectionView = rootView.findViewById(R.id.exo_track_selection_view);
      trackSelectionView.setShowDisableOption(true);
      trackSelectionView.setAllowMultipleOverrides(allowMultipleOverrides);
      trackSelectionView.setAllowAdaptiveSelections(allowAdaptiveSelections);
      trackSelectionView.init(
          trackGroups,
          isDisabled,
          overrides,
          /* trackFormatComparator= */ null,
          /* listener= */ this);
      return rootView;
    }

    @Override
    public void onTrackSelectionChanged(
        boolean isDisabled, Map<TrackGroup, TrackSelectionOverride> overrides) {
      this.isDisabled = isDisabled;
      this.overrides = overrides;
    }
  }
}
