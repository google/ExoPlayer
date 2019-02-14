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
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatDialog;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.ui.TrackSelectionView;
import com.google.android.exoplayer2.util.Assertions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Dialog to select tracks. */
public final class TrackSelectionDialog extends DialogFragment {

  private final SparseArray<TrackSelectionViewFragment> tabFragments;
  private final List<CharSequence> tabTitles;

  private int titleId;
  private MappedTrackInfo mappedTrackInfo;
  private DefaultTrackSelector.Parameters initialSelection;
  private boolean allowAdaptiveSelections;
  private boolean allowMultipleOverrides;
  private DialogInterface.OnClickListener onClickListener;
  private DialogInterface.OnDismissListener onDismissListener;

  /**
   * Creates and initialized a dialog with a {@link DefaultTrackSelector} and automatically updates
   * the track selector's parameters when tracks are selected.
   *
   * @param trackSelector A {@link DefaultTrackSelector}.
   */
  public static TrackSelectionDialog createForTrackSelector(DefaultTrackSelector trackSelector) {
    MappedTrackInfo mappedTrackInfo =
        Assertions.checkNotNull(trackSelector.getCurrentMappedTrackInfo());
    TrackSelectionDialog trackSelectionDialog = new TrackSelectionDialog();
    DefaultTrackSelector.Parameters parameters = trackSelector.getParameters();
    trackSelectionDialog.init(
        /* titleId= */ R.string.track_selection_title,
        mappedTrackInfo,
        /* initialSelection = */ parameters,
        /* allowAdaptiveSelections =*/ true,
        /* allowMultipleOverrides= */ false,
        /* onClickListener= */ (dialog, which) -> {
          DefaultTrackSelector.ParametersBuilder builder = parameters.buildUpon();
          for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
            builder
                .clearSelectionOverrides(/* rendererIndex= */ i)
                .setRendererDisabled(
                    /* rendererIndex= */ i,
                    trackSelectionDialog.getIsDisabled(/* rendererIndex= */ i));
            List<SelectionOverride> overrides =
                trackSelectionDialog.getOverrides(/* rendererIndex= */ i);
            if (!overrides.isEmpty()) {
              builder.setSelectionOverride(
                  /* rendererIndex= */ i,
                  mappedTrackInfo.getTrackGroups(/* rendererIndex= */ i),
                  overrides.get(0));
            }
          }
          trackSelector.setParameters(builder);
        },
        /* onDismissListener= */ (dialog) -> {});
    return trackSelectionDialog;
  }

  /** Creates the dialog. */
  public TrackSelectionDialog() {
    tabFragments = new SparseArray<>();
    tabTitles = new ArrayList<>();
    // Retain instance across orientation changes to prevent loosing access to init data.
    setRetainInstance(true);
  }

  /**
   * Initializes the dialog.
   *
   * @param titleId The resource id of the dialog title.
   * @param mappedTrackInfo The {@link MappedTrackInfo} to display.
   * @param initialSelection The {@link DefaultTrackSelector.Parameters} describing the initial
   *     track selection.
   * @param allowAdaptiveSelections Whether adaptive selections (consisting of more than one track)
   *     can be made.
   * @param allowMultipleOverrides Whether tracks from multiple track groups can be selected.
   * @param onClickListener {@link DialogInterface.OnClickListener} called when tracks are selected.
   * @param onDismissListener {@link DialogInterface.OnDismissListener} called when the dialog is
   *     dismissed.
   */
  public void init(
      int titleId,
      MappedTrackInfo mappedTrackInfo,
      DefaultTrackSelector.Parameters initialSelection,
      boolean allowAdaptiveSelections,
      boolean allowMultipleOverrides,
      DialogInterface.OnClickListener onClickListener,
      DialogInterface.OnDismissListener onDismissListener) {
    this.titleId = titleId;
    this.mappedTrackInfo = mappedTrackInfo;
    this.initialSelection = initialSelection;
    this.allowAdaptiveSelections = allowAdaptiveSelections;
    this.allowMultipleOverrides = allowMultipleOverrides;
    this.onClickListener = onClickListener;
    this.onDismissListener = onDismissListener;
  }

  /**
   * Returns whether a renderer is disabled.
   *
   * @param rendererIndex Renderer index.
   * @return Whether the renderer is disabled.
   */
  public boolean getIsDisabled(int rendererIndex) {
    TrackSelectionViewFragment rendererView = tabFragments.get(rendererIndex);
    return rendererView != null && rendererView.trackSelectionView.getIsDisabled();
  }

  /**
   * Returns the list of selected track selection overrides for the specified renderer. There will
   * be at most one override for each track group.
   *
   * @param rendererIndex Renderer index.
   * @return The list of track selection overrides for this renderer.
   */
  public List<SelectionOverride> getOverrides(int rendererIndex) {
    TrackSelectionViewFragment rendererView = tabFragments.get(rendererIndex);
    return rendererView == null
        ? Collections.emptyList()
        : rendererView.trackSelectionView.getOverrides();
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    // We need to own the view to let tab layout work correctly on all API levels. That's why we
    // can't use an AlertDialog as it owns its view itself. Using AppCompatDialog instead. We still
    // want the "alertDialogTheme" style attribute of the current theme instead of AppCompatDialog's
    // default "dialogTheme" style, so obtain that manually.
    TypedValue alertDialogStyle = new TypedValue();
    getActivity().getTheme().resolveAttribute(R.attr.alertDialogTheme, alertDialogStyle, true);
    AppCompatDialog dialog = new AppCompatDialog(getActivity(), alertDialogStyle.resourceId);
    dialog.setTitle(titleId);
    return dialog;
  }

  @Override
  public void onDismiss(DialogInterface dialog) {
    super.onDismiss(dialog);
    onDismissListener.onDismiss(dialog);
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
      TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(i);
      if (trackGroupArray.length == 0) {
        continue;
      }
      String trackTypeString =
          getTrackTypeString(mappedTrackInfo.getRendererType(/* rendererIndex= */ i));
      if (trackTypeString == null) {
        continue;
      }
      TrackSelectionViewFragment tabFragment = new TrackSelectionViewFragment();
      tabFragment.init(
          mappedTrackInfo,
          /* rendererIndex= */ i,
          initialSelection.getRendererDisabled(/* rendererIndex= */ i),
          initialSelection.getSelectionOverride(/* rendererIndex= */ i, trackGroupArray),
          allowAdaptiveSelections,
          allowMultipleOverrides);
      tabFragments.put(i, tabFragment);
      tabTitles.add(trackTypeString);
    }
    View dialogView = inflater.inflate(R.layout.track_selection_dialog, container, false);
    TabLayout tabLayout = dialogView.findViewById(R.id.track_selection_dialog_tab_layout);
    ViewPager viewPager = dialogView.findViewById(R.id.track_selection_dialog_view_pager);
    Button cancelButton = dialogView.findViewById(R.id.track_selection_dialog_cancel_button);
    Button okButton = dialogView.findViewById(R.id.track_selection_dialog_ok_button);
    viewPager.setAdapter(new FragmentAdapter(getChildFragmentManager()));
    tabLayout.setupWithViewPager(viewPager);
    cancelButton.setOnClickListener(view -> dismiss());
    okButton.setOnClickListener(
        view -> {
          onClickListener.onClick(getDialog(), DialogInterface.BUTTON_POSITIVE);
          dismiss();
        });
    return dialogView;
  }

  @Nullable
  private String getTrackTypeString(int trackType) {
    Resources resources = getResources();
    switch (trackType) {
      case C.TRACK_TYPE_VIDEO:
        return resources.getString(R.string.exo_track_selection_title_video);
      case C.TRACK_TYPE_AUDIO:
        return resources.getString(R.string.exo_track_selection_title_audio);
      case C.TRACK_TYPE_TEXT:
        return resources.getString(R.string.exo_track_selection_title_text);
      default:
        return null;
    }
  }

  private final class FragmentAdapter extends FragmentPagerAdapter {

    public FragmentAdapter(FragmentManager fragmentManager) {
      super(fragmentManager);
    }

    @Override
    public Fragment getItem(int position) {
      return tabFragments.valueAt(position);
    }

    @Override
    public int getCount() {
      return tabFragments.size();
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
      return tabTitles.get(position);
    }
  }

  /** Fragment to show a track seleciton in tab of the track selection dialog. */
  public static final class TrackSelectionViewFragment extends Fragment {

    private MappedTrackInfo mappedTrackInfo;
    private int rendererIndex;
    private boolean initialIsDisabled;
    @Nullable private SelectionOverride initialOverride;
    private boolean allowAdaptiveSelections;
    private boolean allowMultipleOverrides;

    /* package */ TrackSelectionView trackSelectionView;

    public void init(
        MappedTrackInfo mappedTrackInfo,
        int rendererIndex,
        boolean initialIsDisabled,
        @Nullable SelectionOverride initialOverride,
        boolean allowAdaptiveSelections,
        boolean allowMultipleOverrides) {
      this.mappedTrackInfo = mappedTrackInfo;
      this.rendererIndex = rendererIndex;
      this.initialIsDisabled = initialIsDisabled;
      this.initialOverride = initialOverride;
      this.allowAdaptiveSelections = allowAdaptiveSelections;
      this.allowMultipleOverrides = allowMultipleOverrides;
    }

    @Nullable
    @Override
    public View onCreateView(
        LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState) {
      View rootView =
          inflater.inflate(
              R.layout.track_selection_dialog_tab, container, /* attachToRoot= */ false);
      trackSelectionView = rootView.findViewById(R.id.download_dialog_track_selection_view);
      trackSelectionView.setShowDisableOption(true);
      trackSelectionView.setAllowMultipleOverrides(allowMultipleOverrides);
      trackSelectionView.setAllowAdaptiveSelections(allowAdaptiveSelections);
      trackSelectionView.init(
          mappedTrackInfo,
          rendererIndex,
          initialIsDisabled,
          initialOverride == null
              ? Collections.emptyList()
              : Collections.singletonList(initialOverride));
      return rootView;
    }
  }
}
