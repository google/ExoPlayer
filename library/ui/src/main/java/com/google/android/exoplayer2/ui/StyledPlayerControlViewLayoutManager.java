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

  private final Runnable showAllBarsRunnable;
  private final Runnable hideAllBarsRunnable;
  private final Runnable hideProgressBarRunnable;
  private final Runnable hideMainBarsRunnable;
  private final Runnable hideControllerRunnable;
  private final OnLayoutChangeListener onLayoutChangeListener;

  private final List<View> shownButtons;

  private int uxState;
  private boolean initiallyHidden;
  private boolean isMinimalMode;
  private boolean needToShowBars;
  private boolean animationEnabled;

  @Nullable private StyledPlayerControlView styledPlayerControlView;

  @Nullable private ViewGroup embeddedTransportControls;
  @Nullable private ViewGroup bottomBar;
  @Nullable private ViewGroup minimalControls;
  @Nullable private ViewGroup basicControls;
  @Nullable private ViewGroup extraControls;
  @Nullable private ViewGroup extraControlsScrollView;
  @Nullable private ViewGroup timeView;
  @Nullable private View timeBar;
  @Nullable private View overflowShowButton;

  @Nullable private AnimatorSet hideMainBarsAnimator;
  @Nullable private AnimatorSet hideProgressBarAnimator;
  @Nullable private AnimatorSet hideAllBarsAnimator;
  @Nullable private AnimatorSet showMainBarsAnimator;
  @Nullable private AnimatorSet showAllBarsAnimator;
  @Nullable private ValueAnimator overflowShowAnimator;
  @Nullable private ValueAnimator overflowHideAnimator;

  public StyledPlayerControlViewLayoutManager() {
    showAllBarsRunnable = this::showAllBars;
    hideAllBarsRunnable = this::hideAllBars;
    hideProgressBarRunnable = this::hideProgressBar;
    hideMainBarsRunnable = this::hideMainBars;
    hideControllerRunnable = this::hideController;
    onLayoutChangeListener = this::onLayoutChange;
    animationEnabled = true;
    uxState = UX_STATE_ALL_VISIBLE;
    shownButtons = new ArrayList<>();
  }

  public void show() {
    initiallyHidden = false;
    if (this.styledPlayerControlView == null) {
      return;
    }
    StyledPlayerControlView styledPlayerControlView = this.styledPlayerControlView;
    if (!styledPlayerControlView.isVisible()) {
      styledPlayerControlView.setVisibility(View.VISIBLE);
      styledPlayerControlView.updateAll();
      styledPlayerControlView.requestPlayPauseFocus();
    }
    styledPlayerControlView.post(showAllBarsRunnable);
  }

  public void hide() {
    initiallyHidden = true;
    if (styledPlayerControlView == null
        || uxState == UX_STATE_ANIMATING_HIDE
        || uxState == UX_STATE_NONE_VISIBLE) {
      return;
    }
    removeHideCallbacks();
    if (!animationEnabled) {
      postDelayedRunnable(hideControllerRunnable, 0);
    } else if (uxState == UX_STATE_ONLY_PROGRESS_VISIBLE) {
      postDelayedRunnable(hideProgressBarRunnable, 0);
    } else {
      postDelayedRunnable(hideAllBarsRunnable, 0);
    }
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
    int showTimeoutMs =
        styledPlayerControlView != null ? styledPlayerControlView.getShowTimeoutMs() : 0;
    if (showTimeoutMs > 0) {
      if (!animationEnabled) {
        postDelayedRunnable(hideControllerRunnable, showTimeoutMs);
      } else if (uxState == UX_STATE_ONLY_PROGRESS_VISIBLE) {
        postDelayedRunnable(hideProgressBarRunnable, ANIMATION_INTERVAL_MS);
      } else {
        postDelayedRunnable(hideMainBarsRunnable, showTimeoutMs);
      }
    }
  }

  public void removeHideCallbacks() {
    if (styledPlayerControlView == null) {
      return;
    }
    styledPlayerControlView.removeCallbacks(hideControllerRunnable);
    styledPlayerControlView.removeCallbacks(hideAllBarsRunnable);
    styledPlayerControlView.removeCallbacks(hideMainBarsRunnable);
    styledPlayerControlView.removeCallbacks(hideProgressBarRunnable);
  }

  // TODO(insun): Pass StyledPlayerControlView to constructor and reduce multiple nullchecks.
  public void onViewAttached(StyledPlayerControlView v) {
    styledPlayerControlView = v;

    v.setVisibility(initiallyHidden ? View.GONE : View.VISIBLE);

    v.addOnLayoutChangeListener(onLayoutChangeListener);

    // Relating to Center View
    ViewGroup centerView = v.findViewById(R.id.exo_center_view);
    embeddedTransportControls = v.findViewById(R.id.exo_embedded_transport_controls);

    // Relating to Minimal Layout
    minimalControls = v.findViewById(R.id.exo_minimal_controls);

    // Relating to Bottom Bar View
    ViewGroup bottomBar = v.findViewById(R.id.exo_bottom_bar);

    // Relating to Bottom Bar Left View
    timeView = v.findViewById(R.id.exo_time);
    View timeBar = v.findViewById(R.id.exo_progress);

    // Relating to Bottom Bar Right View
    basicControls = v.findViewById(R.id.exo_basic_controls);
    extraControls = v.findViewById(R.id.exo_extra_controls);
    extraControlsScrollView = v.findViewById(R.id.exo_extra_controls_scroll_view);
    overflowShowButton = v.findViewById(R.id.exo_overflow_show);
    View overflowHideButton = v.findViewById(R.id.exo_overflow_hide);
    if (overflowShowButton != null && overflowHideButton != null) {
      overflowShowButton.setOnClickListener(this::onOverflowButtonClick);
      overflowHideButton.setOnClickListener(this::onOverflowButtonClick);
    }

    this.bottomBar = bottomBar;
    this.timeBar = timeBar;

    Resources resources = v.getResources();
    float progressBarHeight = resources.getDimension(R.dimen.exo_custom_progress_thumb_size);
    float bottomBarHeight = resources.getDimension(R.dimen.exo_bottom_bar_height);

    ValueAnimator fadeOutAnimator = ValueAnimator.ofFloat(1.0f, 0.0f);
    fadeOutAnimator.setInterpolator(new LinearInterpolator());
    fadeOutAnimator.addUpdateListener(
        animation -> {
          float animatedValue = (float) animation.getAnimatedValue();

          if (centerView != null) {
            centerView.setAlpha(animatedValue);
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
            if (centerView != null) {
              centerView.setVisibility(View.INVISIBLE);
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

          if (centerView != null) {
            centerView.setAlpha(animatedValue);
          }
          if (minimalControls != null) {
            minimalControls.setAlpha(animatedValue);
          }
        });
    fadeInAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationStart(Animator animation) {
            if (centerView != null) {
              centerView.setVisibility(View.VISIBLE);
            }
            if (minimalControls != null) {
              minimalControls.setVisibility(isMinimalMode ? View.VISIBLE : View.INVISIBLE);
            }
            if (timeBar instanceof DefaultTimeBar && !isMinimalMode) {
              ((DefaultTimeBar) timeBar).showScrubber(DURATION_FOR_SHOWING_ANIMATION_MS);
            }
          }
        });

    hideMainBarsAnimator = new AnimatorSet();
    hideMainBarsAnimator.setDuration(DURATION_FOR_HIDING_ANIMATION_MS);
    hideMainBarsAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationStart(Animator animation) {
            setUxState(UX_STATE_ANIMATING_HIDE);
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            setUxState(UX_STATE_ONLY_PROGRESS_VISIBLE);
            if (needToShowBars) {
              if (styledPlayerControlView != null) {
                styledPlayerControlView.post(showAllBarsRunnable);
              }
              needToShowBars = false;
            }
          }
        });
    hideMainBarsAnimator
        .play(fadeOutAnimator)
        .with(ofTranslationY(0, bottomBarHeight, timeBar))
        .with(ofTranslationY(0, bottomBarHeight, bottomBar));

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
              if (styledPlayerControlView != null) {
                styledPlayerControlView.post(showAllBarsRunnable);
              }
              needToShowBars = false;
            }
          }
        });
    hideProgressBarAnimator
        .play(ofTranslationY(bottomBarHeight, bottomBarHeight + progressBarHeight, timeBar))
        .with(ofTranslationY(bottomBarHeight, bottomBarHeight + progressBarHeight, bottomBar));

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
              if (styledPlayerControlView != null) {
                styledPlayerControlView.post(showAllBarsRunnable);
              }
              needToShowBars = false;
            }
          }
        });
    hideAllBarsAnimator
        .play(fadeOutAnimator)
        .with(ofTranslationY(0, bottomBarHeight + progressBarHeight, timeBar))
        .with(ofTranslationY(0, bottomBarHeight + progressBarHeight, bottomBar));

    showMainBarsAnimator = new AnimatorSet();
    showMainBarsAnimator.setDuration(DURATION_FOR_SHOWING_ANIMATION_MS);
    showMainBarsAnimator.addListener(
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
    showMainBarsAnimator
        .play(fadeInAnimator)
        .with(ofTranslationY(bottomBarHeight, 0, timeBar))
        .with(ofTranslationY(bottomBarHeight, 0, bottomBar));

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
        .with(ofTranslationY(bottomBarHeight + progressBarHeight, 0, timeBar))
        .with(ofTranslationY(bottomBarHeight + progressBarHeight, 0, bottomBar));

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

  public void onViewDetached(StyledPlayerControlView v) {
    v.removeOnLayoutChangeListener(onLayoutChangeListener);
  }

  public boolean isFullyVisible() {
    if (styledPlayerControlView == null) {
      return false;
    }
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
    if (styledPlayerControlView != null) {
      StyledPlayerControlView styledPlayerControlView = this.styledPlayerControlView;
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

    boolean shouldBeMinimalMode = shouldBeMinimalMode();
    if (isMinimalMode != shouldBeMinimalMode) {
      isMinimalMode = shouldBeMinimalMode;
      v.post(this::updateLayoutForSizeChange);
    }
    boolean widthChanged = (right - left) != (oldRight - oldLeft);
    if (!isMinimalMode && widthChanged) {
      v.post(this::onLayoutWidthChanged);
    }
  }

  private void onOverflowButtonClick(View v) {
    resetHideCallbacks();
    if (v.getId() == R.id.exo_overflow_show && overflowShowAnimator != null) {
      overflowShowAnimator.start();
    } else if (v.getId() == R.id.exo_overflow_hide && overflowHideAnimator != null) {
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
        if (showAllBarsAnimator != null) {
          showAllBarsAnimator.start();
        }
        break;
      case UX_STATE_ONLY_PROGRESS_VISIBLE:
        if (showMainBarsAnimator != null) {
          showMainBarsAnimator.start();
        }
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
    if (hideAllBarsAnimator == null) {
      return;
    }
    hideAllBarsAnimator.start();
  }

  private void hideProgressBar() {
    if (hideProgressBarAnimator == null) {
      return;
    }
    hideProgressBarAnimator.start();
  }

  private void hideMainBars() {
    if (hideMainBarsAnimator == null) {
      return;
    }
    hideMainBarsAnimator.start();
    postDelayedRunnable(hideProgressBarRunnable, ANIMATION_INTERVAL_MS);
  }

  private void hideController() {
    setUxState(UX_STATE_NONE_VISIBLE);
  }

  private static ObjectAnimator ofTranslationY(float startValue, float endValue, View target) {
    return ObjectAnimator.ofFloat(target, "translationY", startValue, endValue);
  }

  private void postDelayedRunnable(Runnable runnable, long interval) {
    if (styledPlayerControlView != null && interval >= 0) {
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

  private boolean shouldBeMinimalMode() {
    if (this.styledPlayerControlView == null) {
      return isMinimalMode;
    }
    ViewGroup playerControlView = this.styledPlayerControlView;

    int width =
        playerControlView.getWidth()
            - playerControlView.getPaddingLeft()
            - playerControlView.getPaddingRight();
    int height =
        playerControlView.getHeight()
            - playerControlView.getPaddingBottom()
            - playerControlView.getPaddingTop();
    int defaultModeWidth =
        Math.max(
            getWidth(embeddedTransportControls), getWidth(timeView) + getWidth(overflowShowButton));
    int defaultModeHeight =
        getHeight(embeddedTransportControls) + getHeight(timeBar) + getHeight(bottomBar);

    return (width <= defaultModeWidth || height <= defaultModeHeight);
  }

  private void updateLayoutForSizeChange() {
    if (this.styledPlayerControlView == null) {
      return;
    }
    StyledPlayerControlView playerControlView = this.styledPlayerControlView;

    if (minimalControls != null) {
      minimalControls.setVisibility(isMinimalMode ? View.VISIBLE : View.INVISIBLE);
    }

    View fullScreenButton = playerControlView.findViewById(R.id.exo_fullscreen);
    if (fullScreenButton != null) {
      ViewGroup parent = (ViewGroup) fullScreenButton.getParent();
      parent.removeView(fullScreenButton);

      if (isMinimalMode && minimalControls != null) {
        minimalControls.addView(fullScreenButton);
      } else if (!isMinimalMode && basicControls != null) {
        int index = Math.max(0, basicControls.getChildCount() - 1);
        basicControls.addView(fullScreenButton, index);
      } else {
        parent.addView(fullScreenButton);
      }
    }
    if (timeBar != null) {
      View timeBar = this.timeBar;
      MarginLayoutParams timeBarParams = (MarginLayoutParams) timeBar.getLayoutParams();
      int timeBarMarginBottom =
          playerControlView
              .getResources()
              .getDimensionPixelSize(R.dimen.exo_custom_progress_margin_bottom);
      timeBarParams.bottomMargin = (isMinimalMode ? 0 : timeBarMarginBottom);
      timeBar.setLayoutParams(timeBarParams);
      if (timeBar instanceof DefaultTimeBar
          && uxState != UX_STATE_ANIMATING_HIDE
          && uxState != UX_STATE_ANIMATING_SHOW) {
        if (isMinimalMode || uxState != UX_STATE_ALL_VISIBLE) {
          ((DefaultTimeBar) timeBar).hideScrubber();
        } else {
          ((DefaultTimeBar) timeBar).showScrubber();
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
    ViewGroup basicControls = this.basicControls;
    ViewGroup extraControls = this.extraControls;

    int width =
        (styledPlayerControlView != null
            ? styledPlayerControlView.getWidth()
                - styledPlayerControlView.getPaddingLeft()
                - styledPlayerControlView.getPaddingRight()
            : 0);
    int basicBottomBarWidth = getWidth(timeView);
    for (int i = 0; i < basicControls.getChildCount(); ++i) {
      basicBottomBarWidth += basicControls.getChildAt(i).getWidth();
    }

    // BasicControls keeps overflow button at least.
    int minBasicControlsChildCount = 1;
    // ExtraControls keeps overflow button and settings button at least.
    int minExtraControlsChildCount = 2;

    if (basicBottomBarWidth > width) {
      // move control views from basicControls to extraControls
      ArrayList<View> movingChildren = new ArrayList<>();
      int movingWidth = 0;
      int endIndex = basicControls.getChildCount() - minBasicControlsChildCount;
      for (int index = 0; index < endIndex; index++) {
        View child = basicControls.getChildAt(index);
        movingWidth += child.getWidth();
        movingChildren.add(child);
        if (basicBottomBarWidth - movingWidth <= width) {
          break;
        }
      }

      if (!movingChildren.isEmpty()) {
        basicControls.removeViews(0, movingChildren.size());

        for (View child : movingChildren) {
          int index = extraControls.getChildCount() - minExtraControlsChildCount;
          extraControls.addView(child, index);
        }
      }

    } else {
      // move controls from extraControls to basicControls if possible, else do nothing
      ArrayList<View> movingChildren = new ArrayList<>();
      int movingWidth = 0;
      int startIndex = extraControls.getChildCount() - minExtraControlsChildCount - 1;
      for (int index = startIndex; index >= 0; index--) {
        View child = extraControls.getChildAt(index);
        movingWidth += child.getWidth();
        if (basicBottomBarWidth + movingWidth > width) {
          break;
        }
        movingChildren.add(child);
      }

      if (!movingChildren.isEmpty()) {
        extraControls.removeViews(startIndex - movingChildren.size() + 1, movingChildren.size());

        for (View child : movingChildren) {
          basicControls.addView(child, 0);
        }
      }
    }
  }

  private static int getWidth(@Nullable View v) {
    return (v != null ? v.getWidth() : 0);
  }

  private static int getHeight(@Nullable View v) {
    return (v != null ? v.getHeight() : 0);
  }
}
