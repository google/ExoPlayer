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
package com.google.android.exoplayer2.trackselection;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.Context;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.accessibility.CaptioningManager;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Locale;

/** Constraint parameters for track selection. */
public class TrackSelectionParameters implements Parcelable {

  /**
   * A builder for {@link TrackSelectionParameters}. See the {@link TrackSelectionParameters}
   * documentation for explanations of the parameters that can be configured using this builder.
   */
  public static class Builder {

    /* package */ ImmutableList<String> preferredAudioLanguages;
    /* package */ ImmutableList<String> preferredTextLanguages;
    @C.RoleFlags /* package */ int preferredTextRoleFlags;
    /* package */ boolean selectUndeterminedTextLanguage;
    @C.SelectionFlags /* package */ int disabledTextTrackSelectionFlags;

    /**
     * Creates a builder with default initial values.
     *
     * @param context Any context.
     */
    @SuppressWarnings({"deprecation", "nullness:method.invocation.invalid"})
    public Builder(Context context) {
      this();
      setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(context);
    }

    /**
     * @deprecated {@link Context} constraints will not be set when using this constructor. Use
     *     {@link #Builder(Context)} instead.
     */
    @Deprecated
    public Builder() {
      preferredAudioLanguages = ImmutableList.of();
      preferredTextLanguages = ImmutableList.of();
      preferredTextRoleFlags = 0;
      selectUndeterminedTextLanguage = false;
      disabledTextTrackSelectionFlags = 0;
    }

    /**
     * @param initialValues The {@link TrackSelectionParameters} from which the initial values of
     *     the builder are obtained.
     */
    /* package */ Builder(TrackSelectionParameters initialValues) {
      preferredAudioLanguages = initialValues.preferredAudioLanguages;
      preferredTextLanguages = initialValues.preferredTextLanguages;
      preferredTextRoleFlags = initialValues.preferredTextRoleFlags;
      selectUndeterminedTextLanguage = initialValues.selectUndeterminedTextLanguage;
      disabledTextTrackSelectionFlags = initialValues.disabledTextTrackSelectionFlags;
    }

    /**
     * Sets the preferred language for audio and forced text tracks.
     *
     * @param preferredAudioLanguage Preferred audio language as an IETF BCP 47 conformant tag, or
     *     {@code null} to select the default track, or the first track if there's no default.
     * @return This builder.
     */
    public Builder setPreferredAudioLanguage(@Nullable String preferredAudioLanguage) {
      return preferredAudioLanguage == null
          ? setPreferredAudioLanguages()
          : setPreferredAudioLanguages(preferredAudioLanguage);
    }

    /**
     * Sets the preferred languages for audio and forced text tracks.
     *
     * @param preferredAudioLanguages Preferred audio languages as IETF BCP 47 conformant tags in
     *     order of preference, or an empty array to select the default track, or the first track if
     *     there's no default.
     * @return This builder.
     */
    public Builder setPreferredAudioLanguages(String... preferredAudioLanguages) {
      ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
      for (String language : checkNotNull(preferredAudioLanguages)) {
        listBuilder.add(Util.normalizeLanguageCode(checkNotNull(language)));
      }
      this.preferredAudioLanguages = listBuilder.build();
      return this;
    }

    /**
     * Sets the preferred language and role flags for text tracks based on the accessibility
     * settings of {@link CaptioningManager}.
     *
     * <p>Does nothing for API levels &lt; 19 or when the {@link CaptioningManager} is disabled.
     *
     * @param context A {@link Context}.
     * @return This builder.
     */
    public Builder setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(
        Context context) {
      if (Util.SDK_INT >= 19) {
        setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettingsV19(context);
      }
      return this;
    }

    /**
     * Sets the preferred language for text tracks.
     *
     * @param preferredTextLanguage Preferred text language as an IETF BCP 47 conformant tag, or
     *     {@code null} to select the default track if there is one, or no track otherwise.
     * @return This builder.
     */
    public Builder setPreferredTextLanguage(@Nullable String preferredTextLanguage) {
      return preferredTextLanguage == null
          ? setPreferredTextLanguages()
          : setPreferredTextLanguages(preferredTextLanguage);
    }

    /**
     * Sets the preferred languages for text tracks.
     *
     * @param preferredTextLanguages Preferred text languages as IETF BCP 47 conformant tags in
     *     order of preference, or an empty array to select the default track if there is one, or no
     *     track otherwise.
     * @return This builder.
     */
    public Builder setPreferredTextLanguages(String... preferredTextLanguages) {
      ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
      for (String language : checkNotNull(preferredTextLanguages)) {
        listBuilder.add(Util.normalizeLanguageCode(checkNotNull(language)));
      }
      this.preferredTextLanguages = listBuilder.build();
      return this;
    }

    /**
     * Sets the preferred {@link C.RoleFlags} for text tracks.
     *
     * @param preferredTextRoleFlags Preferred text role flags.
     * @return This builder.
     */
    public Builder setPreferredTextRoleFlags(@C.RoleFlags int preferredTextRoleFlags) {
      this.preferredTextRoleFlags = preferredTextRoleFlags;
      return this;
    }

    /**
     * Sets whether a text track with undetermined language should be selected if no track with
     * {@link #setPreferredTextLanguages(String...) a preferred language} is available, or if the
     * preferred language is unset.
     *
     * @param selectUndeterminedTextLanguage Whether a text track with undetermined language should
     *     be selected if no preferred language track is available.
     * @return This builder.
     */
    public Builder setSelectUndeterminedTextLanguage(boolean selectUndeterminedTextLanguage) {
      this.selectUndeterminedTextLanguage = selectUndeterminedTextLanguage;
      return this;
    }

    /**
     * Sets a bitmask of selection flags that are disabled for text track selections.
     *
     * @param disabledTextTrackSelectionFlags A bitmask of {@link C.SelectionFlags} that are
     *     disabled for text track selections.
     * @return This builder.
     */
    public Builder setDisabledTextTrackSelectionFlags(
        @C.SelectionFlags int disabledTextTrackSelectionFlags) {
      this.disabledTextTrackSelectionFlags = disabledTextTrackSelectionFlags;
      return this;
    }

    /** Builds a {@link TrackSelectionParameters} instance with the selected values. */
    public TrackSelectionParameters build() {
      return new TrackSelectionParameters(
          // Audio
          preferredAudioLanguages,
          // Text
          preferredTextLanguages,
          preferredTextRoleFlags,
          selectUndeterminedTextLanguage,
          disabledTextTrackSelectionFlags);
    }

    @RequiresApi(19)
    private void setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettingsV19(
        Context context) {
      if (Util.SDK_INT < 23 && Looper.myLooper() == null) {
        // Android platform bug (pre-Marshmallow) that causes RuntimeExceptions when
        // CaptioningService is instantiated from a non-Looper thread. See [internal: b/143779904].
        return;
      }
      CaptioningManager captioningManager =
          (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
      if (captioningManager == null || !captioningManager.isEnabled()) {
        return;
      }
      preferredTextRoleFlags = C.ROLE_FLAG_CAPTION | C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND;
      Locale preferredLocale = captioningManager.getLocale();
      if (preferredLocale != null) {
        preferredTextLanguages = ImmutableList.of(Util.getLocaleLanguageTag(preferredLocale));
      }
    }
  }

  /**
   * An instance with default values, except those obtained from the {@link Context}.
   *
   * <p>If possible, use {@link #getDefaults(Context)} instead.
   *
   * <p>This instance will not have the following settings:
   *
   * <ul>
   *   <li>{@link Builder#setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(Context)
   *       Preferred text language and role flags} configured to the accessibility settings of
   *       {@link CaptioningManager}.
   * </ul>
   */
  @SuppressWarnings("deprecation")
  public static final TrackSelectionParameters DEFAULT_WITHOUT_CONTEXT = new Builder().build();

  /**
   * @deprecated This instance is not configured using {@link Context} constraints. Use {@link
   *     #getDefaults(Context)} instead.
   */
  @Deprecated public static final TrackSelectionParameters DEFAULT = DEFAULT_WITHOUT_CONTEXT;

  /** Returns an instance configured with default values. */
  public static TrackSelectionParameters getDefaults(Context context) {
    return new Builder(context).build();
  }

  /**
   * The preferred languages for audio and forced text tracks as IETF BCP 47 conformant tags in
   * order of preference. An empty list selects the default track, or the first track if there's no
   * default. The default value is an empty list.
   */
  public final ImmutableList<String> preferredAudioLanguages;
  /**
   * The preferred languages for text tracks as IETF BCP 47 conformant tags in order of preference.
   * An empty list selects the default track if there is one, or no track otherwise. The default
   * value is an empty list, or the language of the accessibility {@link CaptioningManager} if
   * enabled.
   */
  public final ImmutableList<String> preferredTextLanguages;
  /**
   * The preferred {@link C.RoleFlags} for text tracks. {@code 0} selects the default track if there
   * is one, or no track otherwise. The default value is {@code 0}, or {@link C#ROLE_FLAG_SUBTITLE}
   * | {@link C#ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND} if the accessibility {@link CaptioningManager}
   * is enabled.
   */
  @C.RoleFlags public final int preferredTextRoleFlags;
  /**
   * Whether a text track with undetermined language should be selected if no track with {@link
   * #preferredTextLanguages} is available, or if {@link #preferredTextLanguages} is unset. The
   * default value is {@code false}.
   */
  public final boolean selectUndeterminedTextLanguage;
  /**
   * Bitmask of selection flags that are disabled for text track selections. See {@link
   * C.SelectionFlags}. The default value is {@code 0} (i.e. no flags).
   */
  @C.SelectionFlags public final int disabledTextTrackSelectionFlags;

  /* package */ TrackSelectionParameters(
      ImmutableList<String> preferredAudioLanguages,
      ImmutableList<String> preferredTextLanguages,
      @C.RoleFlags int preferredTextRoleFlags,
      boolean selectUndeterminedTextLanguage,
      @C.SelectionFlags int disabledTextTrackSelectionFlags) {
    // Audio
    this.preferredAudioLanguages = preferredAudioLanguages;
    // Text
    this.preferredTextLanguages = preferredTextLanguages;
    this.preferredTextRoleFlags = preferredTextRoleFlags;
    this.selectUndeterminedTextLanguage = selectUndeterminedTextLanguage;
    this.disabledTextTrackSelectionFlags = disabledTextTrackSelectionFlags;
  }

  /* package */ TrackSelectionParameters(Parcel in) {
    ArrayList<String> preferredAudioLanguages = new ArrayList<>();
    in.readList(preferredAudioLanguages, /* loader= */ null);
    this.preferredAudioLanguages = ImmutableList.copyOf(preferredAudioLanguages);
    ArrayList<String> preferredTextLanguages = new ArrayList<>();
    in.readList(preferredTextLanguages, /* loader= */ null);
    this.preferredTextLanguages = ImmutableList.copyOf(preferredTextLanguages);
    this.preferredTextRoleFlags = in.readInt();
    this.selectUndeterminedTextLanguage = Util.readBoolean(in);
    this.disabledTextTrackSelectionFlags = in.readInt();
  }

  /** Creates a new {@link Builder}, copying the initial values from this instance. */
  public Builder buildUpon() {
    return new Builder(this);
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    TrackSelectionParameters other = (TrackSelectionParameters) obj;
    return preferredAudioLanguages.equals(other.preferredAudioLanguages)
        && preferredTextLanguages.equals(other.preferredTextLanguages)
        && preferredTextRoleFlags == other.preferredTextRoleFlags
        && selectUndeterminedTextLanguage == other.selectUndeterminedTextLanguage
        && disabledTextTrackSelectionFlags == other.disabledTextTrackSelectionFlags;
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + preferredAudioLanguages.hashCode();
    result = 31 * result + preferredTextLanguages.hashCode();
    result = 31 * result + preferredTextRoleFlags;
    result = 31 * result + (selectUndeterminedTextLanguage ? 1 : 0);
    result = 31 * result + disabledTextTrackSelectionFlags;
    return result;
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeList(preferredAudioLanguages);
    dest.writeList(preferredTextLanguages);
    dest.writeInt(preferredTextRoleFlags);
    Util.writeBoolean(dest, selectUndeterminedTextLanguage);
    dest.writeInt(disabledTextTrackSelectionFlags);
  }

  public static final Creator<TrackSelectionParameters> CREATOR =
      new Creator<TrackSelectionParameters>() {

        @Override
        public TrackSelectionParameters createFromParcel(Parcel in) {
          return new TrackSelectionParameters(in);
        }

        @Override
        public TrackSelectionParameters[] newArray(int size) {
          return new TrackSelectionParameters[size];
        }
      };
}
