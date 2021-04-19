/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.view.View;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Provides information about an overlay view shown on top of an ad view group. */
public final class AdOverlayInfo {

  /**
   * The purpose of the overlay. One of {@link #PURPOSE_CONTROLS}, {@link #PURPOSE_CLOSE_AD}, {@link
   * #PURPOSE_OTHER} or {@link #PURPOSE_NOT_VISIBLE}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({PURPOSE_CONTROLS, PURPOSE_CLOSE_AD, PURPOSE_OTHER, PURPOSE_NOT_VISIBLE})
  public @interface Purpose {}
  /** Purpose for playback controls overlaying the player. */
  public static final int PURPOSE_CONTROLS = 0;
  /** Purpose for ad close buttons overlaying the player. */
  public static final int PURPOSE_CLOSE_AD = 1;
  /** Purpose for other overlays. */
  public static final int PURPOSE_OTHER = 2;
  /** Purpose for overlays that are not visible. */
  public static final int PURPOSE_NOT_VISIBLE = 3;

  /** The overlay view. */
  public final View view;
  /** The purpose of the overlay view. */
  @Purpose public final int purpose;
  /** An optional, detailed reason that the overlay view is needed. */
  @Nullable public final String reasonDetail;

  /**
   * Creates a new overlay info.
   *
   * @param view The view that is overlaying the player.
   * @param purpose The purpose of the view.
   */
  public AdOverlayInfo(View view, @Purpose int purpose) {
    this(view, purpose, /* detailedReason= */ null);
  }

  /**
   * Creates a new overlay info.
   *
   * @param view The view that is overlaying the player.
   * @param purpose The purpose of the view.
   * @param detailedReason An optional, detailed reason that the view is on top of the player.
   */
  public AdOverlayInfo(View view, @Purpose int purpose, @Nullable String detailedReason) {
    this.view = view;
    this.purpose = purpose;
    this.reasonDetail = detailedReason;
  }
}
