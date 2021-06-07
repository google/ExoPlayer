/*
 * Copyright 2020 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.animation.LinearInterpolator;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/* package */ final class StyledPlayerControlViewLayoutManager {
  private static final long ANIMATION_INTERVAL_MS = 2_000;
  private static final long DURATION_FOR_HIDING_ANIMATION_MS = 250;
  private static final long DURATION_FOR_SHOWING_ANIMATION_MS = 250;

  // Int for defining the UX state where all the views (ProgressBar, BottomBar) are
  // all visible.
  private static final int UX_STATE_ALL_VISIBLE = 0;
  // Int for defining the UX state where only the ProgressBar view is visible.
  private static final int UX_STATE_ONLY_PROGRESS_VISIBLE = 1;
  // Int for defining the UX state where none of the views are visible.
  private static final int UX_STATE_NONE_VISIBLE = 2;
  // Int for defining the UX state where the views are being animated to be hidden.
  private static final int UX_STATE_ANIMATING_HIDE = 3;
  // Int for defining the UX state where the views are being animated to be shown.
  private static final int UX_STATE_ANIMATING_SHOW = 4;

  private final StyledPlayerControlView styledPlayerControlView;

  @Nullable private final View controlsBackground;
  @Nullable private final ViewGroup centerControls;
  @Nullable private final ViewGroup bottomBar;
  @Nullable private final ViewGroup minimalControls;
  @Nullable private final ViewGroup basicControls;
  @Nullable private final ViewGroup extraControls;
  @Nullable private final ViewGroup extraControlsScrollView;
  @Nullable private final ViewGroup timeView;
  @Nullable private final View timeBar;
  @Nullable private final View overflowShowButton;

  private final AnimatorSet hideMainBarAnimator;
  private final AnimatorSet hideProgressBarAnimator;
  private final AnimatorSet hideAllBarsAnimator;
  private final AnimatorSet showMainBarAnimator;
  private final AnimatorSet showAllBarsAnimator;
  private final ValueAnimator overflowShowAnimator;
  private final ValueAnimator overflowHideAnimator;

  private final Runnable showAllBarsRunnable;
  private final Runnable hideAllBarsRunnable;
  private final Runnable hideProgressBarRunnable;
  private final Runnable hideMainBarRunnable;
  private final Runnable hideControllerRunnable;
  private final OnLayoutChangeListener onLayoutChangeListener;

  private final List<View> shownButtons;

  private int uxState;
  private boolean isMinimalMode;
  private boolean needToShowBars;
  private boolean animationEnabled;

  @SuppressWarnings({
    "nullness:method.invocation.invalid",
    "nullness:methodref.receiver.bound.invalid"
  })
  public StyledPlayerControlViewLayoutManager(StyledPlayerControlView styledPlayerControlView) {
    this.styledPlayerControlView = styledPlayerControlView;
    showAllBarsRunnable = this::showAllBars;
    hideAllBarsRunnable = this::hideAllBars;
    hideProgressBarRunnable = this::hideProgressBar;
    hideMainBarRunnable = this::hideMainBar;
    hideControllerRunnable = this::hideController;
    onLayoutChangeListener = this::onLayoutChange;
    animationEnabled = true;
    uxState = UX_STATE_ALL_VISIBLE;
    shownButtons = new ArrayList<>();

    // Relating to Center View
    controlsBackground = styledPlayerControlView.findViewById(R.id.exo_controls_background);
    centerControls = styledPlayerControlView.findViewById(R.id.exo_center_controls);

    // Relating to Minimal Layout
    minimalControls = styledPlayerControlView.findViewById(R.id.exo_minimal_controls);

    // Relating to Bottom Bar View
    bottomBar = styledPlayerControlView.findViewById(R.id.exo_bottom_bar);

    // Relating to Bottom Bar Left View
    timeView = styledPlayerControlView.findViewById(R.id.exo_time);
    timeBar = styledPlayerControlView.findViewById(R.id.exo_progress);

    // Relating to Bottom Bar Right View
    basicControls = styledPlayerControlView.findViewById(R.id.exo_basic_controls);
    extraControls = styledPlayerControlView.findViewById(R.id.exo_extra_controls);
    extraControlsScrollView =
        styledPlayerControlView.findViewById(R.id.exo_extra_controls_scroll_view);
    overflowShowButton = styledPlayerControlView.findViewById(R.id.exo_overflow_show);
    View overflowHideButton = styledPlayerControlView.findViewById(R.id.exo_overflow_hide);
    if (overflowShowButton != null && overflowHideButton != null) {
      overflowShowButton.setOnClickListener(this::onOverflowButtonClick);
      overflowHideButton.setOnClickListener(this::onOverflowButtonClick);
    }

    ValueAnimator fadeOutAnimator = ValueAnimator.ofFloat(1.0f, 0.0f);
    fadeOutAnimator.setInterpolator(new LinearInterpolator());
    fadeOutAnimator.addUpdateListener(
        animation -> {
          float animatedValue = (float) animation.getAnimatedValue();
          if (controlsBackground != null) {
            controlsBackground.setAlpha(animatedValue);
          }
          if (centerControls != null) {
            centerControls.setAlpha(animatedValue);
          }
          if (minimalControls != null) {
            minimalControls.setAlpha(animatedValue);
          }
        });
    fadeOutAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationStart(Animator animation) {
            if (timeBar instanceof DefaultTimeBar && !isMinimalMode) {
              ((DefaultTimeBar) timeBar).hideScrubber(DURATION_FOR_HIDING_ANIMATION_MS);
            }
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            if (controlsBackground != null) {
              controlsBackground.setVisibility(View.INVISIBLE);
            }
            if (centerControls != null) {
              centerControls.setVisibility(View.INVISIBLE);
            }
            if (minimalControls != null) {
              minimalControls.setVisibility(View.INVISIBLE);
            }
          }
        });

    ValueAnimator fadeInAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
    fadeInAnimator.setInterpolator(new LinearInterpolator());
    fadeInAnimator.addUpdateListener(
        animation -> {
          float animatedValue = (float) animation.getAnimatedValue();
          if (controlsBackground != null) {
            controlsBackground.setAlpha(animatedValue);
          }
          if (centerControls != null) {
            centerControls.setAlpha(animatedValue);
          }
          if (minimalControls != null) {
            minimalControls.setAlpha(animatedValue);
          }
        });
    fadeInAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationStart(Animator animation) {
            if (controlsBackground != null) {
              controlsBackground.setVisibility(View.VISIBLE);
            }
            if (centerControls != null) {
              centerControls.setVisibility(View.VISIBLE);
            }
            if (minimalControls != null) {
              minimalControls.setVisibility(isMinimalMode ? View.VISIBLE : View.INVISIBLE);
            }
            if (timeBar instanceof DefaultTimeBar && !isMinimalMode) {
              ((DefaultTimeBar) timeBar).showScrubber(DURATION_FOR_SHOWING_ANIMATION_MS);
            }
          }
        });

    Resources resources = styledPlayerControlView.getResources();
    float translationYForProgressBar =
        resources.getDimension(R.dimen.exo_styled_bottom_bar_height)
            - resources.getDimension(R.dimen.exo_styled_progress_bar_height);
    float translationYForNoBars = resources.getDimension(R.dimen.exo_styled_bottom_bar_height);

    hideMainBarAnimator = new AnimatorSet();
    hideMainBarAnimator.setDuration(DURATION_FOR_HIDING_ANIMATION_MS);
    hideMainBarAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationStart(Animator animation) {
            setUxState(UX_STATE_ANIMATING_HIDE);
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            setUxState(UX_STATE_ONLY_PROGRESS_VISIBLE);
            if (needToShowBars) {
              styledPlayerControlView.post(showAllBarsRunnable);
              needToShowBars = false;
            }
          }
        });
    hideMainBarAnimator
        .play(fadeOutAnimator)
        .with(ofTranslationY(0, translationYForProgressBar, timeBar))
        .with(ofTranslationY(0, translationYForProgressBar, bottomBar));

    hideProgressBarAnimator = new AnimatorSet();
    hideProgressBarAnimator.setDuration(DURATION_FOR_HIDING_ANIMATION_MS);
    hideProgressBarAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationStart(Animator animation) {
            setUxState(UX_STATE_ANIMATING_HIDE);
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            setUxState(UX_STATE_NONE_VISIBLE);
            if (needToShowBars) {
              styledPlayerControlView.post(showAllBarsRunnable);
              needToShowBars = false;
            }
          }
        });
    hideProgressBarAnimator
        .play(ofTranslationY(translationYForProgressBar, translationYForNoBars, timeBar))
        .with(ofTranslationY(translationYForProgressBar, translationYForNoBars, bottomBar));

    hideAllBarsAnimator = new AnimatorSet();
    hideAllBarsAnimator.setDuration(DURATION_FOR_HIDING_ANIMATION_MS);
    hideAllBarsAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationStart(Animator animation) {
            setUxState(UX_STATE_ANIMATING_HIDE);
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            setUxState(UX_STATE_NONE_VISIBLE);
            if (needToShowBars) {
              styledPlayerControlView.post(showAllBarsRunnable);
              needToShowBars = false;
            }
          }
        });
    hideAllBarsAnimator
        .play(fadeOutAnimator)
        .with(ofTranslationY(0, translationYForNoBars, timeBar))
        .with(ofTranslationY(0, translationYForNoBars, bottomBar));

    showMainBarAnimator = new AnimatorSet();
    showMainBarAnimator.setDuration(DURATION_FOR_SHOWING_ANIMATION_MS);
    showMainBarAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationStart(Animator animation) {
            setUxState(UX_STATE_ANIMATING_SHOW);
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            setUxState(UX_STATE_ALL_VISIBLE);
          }
        });
    showMainBarAnimator
        .play(fadeInAnimator)
        .with(ofTranslationY(translationYForProgressBar, 0, timeBar))
        .with(ofTranslationY(translationYForProgressBar, 0, bottomBar));

    showAllBarsAnimator = new AnimatorSet();
    showAllBarsAnimator.setDuration(DURATION_FOR_SHOWING_ANIMATION_MS);
    showAllBarsAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationStart(Animator animation) {
            setUxState(UX_STATE_ANIMATING_SHOW);
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            setUxState(UX_STATE_ALL_VISIBLE);
          }
        });
    showAllBarsAnimator
        .play(fadeInAnimator)
        .with(ofTranslationY(translationYForNoBars, 0, timeBar))
        .with(ofTranslationY(translationYForNoBars, 0, bottomBar));

    overflowShowAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
    overflowShowAnimator.setDuration(DURATION_FOR_SHOWING_ANIMATION_MS);
    overflowShowAnimator.addUpdateListener(
        animation -> animateOverflow((float) animation.getAnimatedValue()));
    overflowShowAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationStart(Animator animation) {
            if (extraControlsScrollView != null) {
              extraControlsScrollView.setVisibility(View.VISIBLE);
              extraControlsScrollView.setTranslationX(extraControlsScrollView.getWidth());
              extraControlsScrollView.scrollTo(extraControlsScrollView.getWidth(), 0);
            }
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            if (basicControls != null) {
              basicControls.setVisibility(View.INVISIBLE);
            }
          }
        });

    overflowHideAnimator = ValueAnimator.ofFloat(1.0f, 0.0f);
    overflowHideAnimator.setDuration(DURATION_FOR_SHOWING_ANIMATION_MS);
    overflowHideAnimator.addUpdateListener(
        animation -> animateOverflow((float) animation.getAnimatedValue()));
    overflowHideAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationStart(Animator animation) {
            if (basicControls != null) {
              basicControls.setVisibility(View.VISIBLE);
            }
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            if (extraControlsScrollView != null) {
              extraControlsScrollView.setVisibility(View.INVISIBLE);
            }
          }
        });
  }

  public void show() {
    if (!styledPlayerControlView.isVisible()) {
      styledPlayerControlView.setVisibility(View.VISIBLE);
      styledPlayerControlView.updateAll();
      styledPlayerControlView.requestPlayPauseFocus();
    }
    showAllBars();
  }

  public void hide() {
    if (uxState == UX_STATE_ANIMATING_HIDE || uxState == UX_STATE_NONE_VISIBLE) {
      return;
    }
    removeHideCallbacks();
    if (!animationEnabled) {
      hideController();
    } else if (uxState == UX_STATE_ONLY_PROGRESS_VISIBLE) {
      hideProgressBar();
    } else {
      hideAllBars();
    }
  }

  public void hideImmediately() {
    if (uxState == UX_STATE_ANIMATING_HIDE || uxState == UX_STATE_NONE_VISIBLE) {
      return;
    }
    removeHideCallbacks();
    hideController();
  }

  public void setAnimationEnabled(boolean animationEnabled) {
    this.animationEnabled = animationEnabled;
  }

  public boolean isAnimationEnabled() {
    return animationEnabled;
  }

  public void resetHideCallbacks() {
    if (uxState == UX_STATE_ANIMATING_HIDE) {
      return;
    }
    removeHideCallbacks();
    int showTimeoutMs = styledPlayerControlView.getShowTimeoutMs();
    if (showTimeoutMs > 0) {
      if (!animationEnabled) {
        postDelayedRunnable(hideControllerRunnable, showTimeoutMs);
      } else if (uxState == UX_STATE_ONLY_PROGRESS_VISIBLE) {
        postDelayedRunnable(hideProgressBarRunnable, ANIMATION_INTERVAL_MS);
      } else {
        postDelayedRunnable(hideMainBarRunnable, showTimeoutMs);
      }
    }
  }

  public void removeHideCallbacks() {
    styledPlayerControlView.removeCallbacks(hideControllerRunnable);
    styledPlayerControlView.removeCallbacks(hideAllBarsRunnable);
    styledPlayerControlView.removeCallbacks(hideMainBarRunnable);
    styledPlayerControlView.removeCallbacks(hideProgressBarRunnable);
  }

  public void onAttachedToWindow() {
    styledPlayerControlView.addOnLayoutChangeListener(onLayoutChangeListener);
  }

  public void onDetachedFromWindow() {
    styledPlayerControlView.removeOnLayoutChangeListener(onLayoutChangeListener);
  }

  public boolean isFullyVisible() {
    return uxState == UX_STATE_ALL_VISIBLE && styledPlayerControlView.isVisible();
  }

  public void setShowButton(@Nullable View button, boolean showButton) {
    if (button == null) {
      return;
    }
    if (!showButton) {
      button.setVisibility(View.GONE);
      shownButtons.remove(button);
      return;
    }
    if (isMinimalMode && shouldHideInMinimalMode(button)) {
      button.setVisibility(View.INVISIBLE);
    } else {
      button.setVisibility(View.VISIBLE);
    }
    shownButtons.add(button);
  }

  public boolean getShowButton(@Nullable View button) {
    return button != null && shownButtons.contains(button);
  }

  private void setUxState(int uxState) {
    int prevUxState = this.uxState;
    this.uxState = uxState;
    if (uxState == UX_STATE_NONE_VISIBLE) {
      styledPlayerControlView.setVisibility(View.GONE);
    } else if (prevUxState == UX_STATE_NONE_VISIBLE) {
      styledPlayerControlView.setVisibility(View.VISIBLE);
    }
    // TODO(insun): Notify specific uxState. Currently reuses legacy visibility listener for API
    //  compatibility.
    if (prevUxState != uxState) {
      styledPlayerControlView.notifyOnVisibilityChange();
    }
  }

  public void onLayout(boolean changed, int left, int top, int right, int bottom) {
    if (controlsBackground != null) {
      // The background view should occupy the entirety of the parent. This is done in code rather
      // than in layout XML to stop the background view from influencing the size of the parent if
      // it uses "wrap_content". See: https://github.com/google/ExoPlayer/issues/8726.
      controlsBackground.layout(0, 0, right - left, bottom - top);
    }
  }

  private void onLayoutChange(
      View v,
      int left,
      int top,
      int right,
      int bottom,
      int oldLeft,
      int oldTop,
      int oldRight,
      int oldBottom) {

    boolean useMinimalMode = useMinimalMode();
    if (isMinimalMode != useMinimalMode) {
      isMinimalMode = useMinimalMode;
      v.post(this::updateLayoutForSizeChange);
    }
    boolean widthChanged = (right - left) != (oldRight - oldLeft);
    if (!isMinimalMode && widthChanged) {
      v.post(this::onLayoutWidthChanged);
    }
  }

  private void onOverflowButtonClick(View v) {
    resetHideCallbacks();
    if (v.getId() == R.id.exo_overflow_show) {
      overflowShowAnimator.start();
    } else if (v.getId() == R.id.exo_overflow_hide) {
      overflowHideAnimator.start();
    }
  }

  private void showAllBars() {
    if (!animationEnabled) {
      setUxState(UX_STATE_ALL_VISIBLE);
      resetHideCallbacks();
      return;
    }

    switch (uxState) {
      case UX_STATE_NONE_VISIBLE:
        showAllBarsAnimator.start();
        break;
      case UX_STATE_ONLY_PROGRESS_VISIBLE:
        showMainBarAnimator.start();
        break;
      case UX_STATE_ANIMATING_HIDE:
        needToShowBars = true;
        break;
      case UX_STATE_ANIMATING_SHOW:
        return;
      default:
        break;
    }
    resetHideCallbacks();
  }

  private void hideAllBars() {
    hideAllBarsAnimator.start();
  }

  private void hideProgressBar() {
    hideProgressBarAnimator.start();
  }

  private void hideMainBar() {
    hideMainBarAnimator.start();
    postDelayedRunnable(hideProgressBarRunnable, ANIMATION_INTERVAL_MS);
  }

  private void hideController() {
    setUxState(UX_STATE_NONE_VISIBLE);
  }

  private static ObjectAnimator ofTranslationY(float startValue, float endValue, View target) {
    return ObjectAnimator.ofFloat(target, "translationY", startValue, endValue);
  }

  private void postDelayedRunnable(Runnable runnable, long interval) {
    if (interval >= 0) {
      styledPlayerControlView.postDelayed(runnable, interval);
    }
  }

  private void animateOverflow(float animatedValue) {
    if (extraControlsScrollView != null) {
      int extraControlTranslationX =
          (int) (extraControlsScrollView.getWidth() * (1 - animatedValue));
      extraControlsScrollView.setTranslationX(extraControlTranslationX);
    }

    if (timeView != null) {
      timeView.setAlpha(1 - animatedValue);
    }
    if (basicControls != null) {
      basicControls.setAlpha(1 - animatedValue);
    }
  }

  private boolean useMinimalMode() {
    int width =
        styledPlayerControlView.getWidth()
            - styledPlayerControlView.getPaddingLeft()
            - styledPlayerControlView.getPaddingRight();
    int height =
        styledPlayerControlView.getHeight()
            - styledPlayerControlView.getPaddingBottom()
            - styledPlayerControlView.getPaddingTop();

    int centerControlWidth =
        getWidthWithMargins(centerControls)
            - (centerControls != null
                ? (centerControls.getPaddingLeft() + centerControls.getPaddingRight())
                : 0);
    int centerControlHeight =
        getHeightWithMargins(centerControls)
            - (centerControls != null
                ? (centerControls.getPaddingTop() + centerControls.getPaddingBottom())
                : 0);

    int defaultModeMinimumWidth =
        Math.max(
            centerControlWidth,
            getWidthWithMargins(timeView) + getWidthWithMargins(overflowShowButton));
    int defaultModeMinimumHeight = centerControlHeight + (2 * getHeightWithMargins(bottomBar));

    return width <= defaultModeMinimumWidth || height <= defaultModeMinimumHeight;
  }

  private void updateLayoutForSizeChange() {
    if (minimalControls != null) {
      minimalControls.setVisibility(isMinimalMode ? View.VISIBLE : View.INVISIBLE);
    }

    if (timeBar != null) {
      MarginLayoutParams timeBarParams = (MarginLayoutParams) timeBar.getLayoutParams();
      int timeBarMarginBottom =
          styledPlayerControlView
              .getResources()
              .getDimensionPixelSize(R.dimen.exo_styled_progress_margin_bottom);
      timeBarParams.bottomMargin = (isMinimalMode ? 0 : timeBarMarginBottom);
      timeBar.setLayoutParams(timeBarParams);
      if (timeBar instanceof DefaultTimeBar) {
        DefaultTimeBar defaultTimeBar = (DefaultTimeBar) timeBar;
        if (isMinimalMode) {
          defaultTimeBar.hideScrubber(/* disableScrubberPadding= */ true);
        } else if (uxState == UX_STATE_ONLY_PROGRESS_VISIBLE) {
          defaultTimeBar.hideScrubber(/* disableScrubberPadding= */ false);
        } else if (uxState != UX_STATE_ANIMATING_HIDE) {
          defaultTimeBar.showScrubber();
        }
      }
    }

    for (View v : shownButtons) {
      v.setVisibility(isMinimalMode && shouldHideInMinimalMode(v) ? View.INVISIBLE : View.VISIBLE);
    }
  }

  private boolean shouldHideInMinimalMode(View button) {
    int id = button.getId();
    return (id == R.id.exo_bottom_bar
        || id == R.id.exo_prev
        || id == R.id.exo_next
        || id == R.id.exo_rew
        || id == R.id.exo_rew_with_amount
        || id == R.id.exo_ffwd
        || id == R.id.exo_ffwd_with_amount);
  }

  private void onLayoutWidthChanged() {
    if (basicControls == null || extraControls == null) {
      return;
    }

    int width =
        styledPlayerControlView.getWidth()
            - styledPlayerControlView.getPaddingLeft()
            - styledPlayerControlView.getPaddingRight();

    // Reset back to all controls being basic controls and the overflow not being needed. The last
    // child of extraControls is the overflow hide button, which shouldn't be moved back.
    while (extraControls.getChildCount() > 1) {
      int controlViewIndex = extraControls.getChildCount() - 2;
      View controlView = extraControls.getChildAt(controlViewIndex);
      extraControls.removeViewAt(controlViewIndex);
      basicControls.addView(controlView, /* index= */ 0);
    }
    if (overflowShowButton != null) {
      overflowShowButton.setVisibility(View.GONE);
    }

    // Calculate how much of the available width is occupied. The last child of basicControls is the
    // overflow show button, which we're currently assuming will not be visible.
    int occupiedWidth = getWidthWithMargins(timeView);
    int endIndex = basicControls.getChildCount() - 1;
    for (int i = 0; i < endIndex; i++) {
      View controlView = basicControls.getChildAt(i);
      occupiedWidth += getWidthWithMargins(controlView);
    }

    if (occupiedWidth > width) {
      // We need to move some controls to extraControls.
      if (overflowShowButton != null) {
        overflowShowButton.setVisibility(View.VISIBLE);
        occupiedWidth += getWidthWithMargins(overflowShowButton);
      }
      ArrayList<View> controlsToMove = new ArrayList<>();
      // The last child of basicControls is the overflow show button, which shouldn't be moved.
      for (int i = 0; i < endIndex; i++) {
        View control = basicControls.getChildAt(i);
        occupiedWidth -= getWidthWithMargins(control);
        controlsToMove.add(control);
        if (occupiedWidth <= width) {
          break;
        }
      }
      if (!controlsToMove.isEmpty()) {
        basicControls.removeViews(/* start= */ 0, controlsToMove.size());
        for (int i = 0; i < controlsToMove.size(); i++) {
          // The last child of extraControls is the overflow hide button. Add controls before it.
          int index = extraControls.getChildCount() - 1;
          extraControls.addView(controlsToMove.get(i), index);
        }
      }
    } else {
      // If extraControls are visible, hide them since they're now empty.
      if (extraControlsScrollView != null
          && extraControlsScrollView.getVisibility() == View.VISIBLE
          && !overflowHideAnimator.isStarted()) {
        overflowShowAnimator.cancel();
        overflowHideAnimator.start();
      }
    }
  }

  private static int getWidthWithMargins(@Nullable View v) {
    if (v == null) {
      return 0;
    }
    int width = v.getWidth();
    LayoutParams layoutParams = v.getLayoutParams();
    if (layoutParams instanceof MarginLayoutParams) {
      MarginLayoutParams marginLayoutParams = (MarginLayoutParams) layoutParams;
      width += marginLayoutParams.leftMargin + marginLayoutParams.rightMargin;
    }
    return width;
  }

  private static int getHeightWithMargins(@Nullable View v) {
    if (v == null) {
      return 0;
    }
    int height = v.getHeight();
    LayoutParams layoutParams = v.getLayoutParams();
    if (layoutParams instanceof MarginLayoutParams) {
      MarginLayoutParams marginLayoutParams = (MarginLayoutParams) layoutParams;
      height += marginLayoutParams.topMargin + marginLayoutParams.bottomMargin;
    }
    return height;
  }
}
