package androidx.media3.common;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/** A Label, as defined by ISO 23009-1, 4th edition, 5.3.7.2. */
@UnstableApi
public class Label implements Parcelable, Bundleable {
  /** Declares the language code(s) for this Label. */
  @Nullable public final String lang;

  /** The value for this Label. */
  public final String value;

  /**
   * @param lang The lang code.
   * @param value The value.
   */
  public Label(@Nullable String lang, String value) {
    this.lang = lang;
    this.value = value;
  }

  /* package */ Label(Parcel in) {
    lang = in.readString();
    value = in.readString();
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Label label = (Label) o;
    return Util.areEqual(lang, label.lang) && Util.areEqual(value, label.value);
  }

  @Override
  public int hashCode() {
    int result = value.hashCode();
    result = 31 * result + (lang != null ? lang.hashCode() : 0);
    return result;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(@NonNull Parcel dest, int flags) {
    dest.writeString(lang);
    dest.writeString(value);
  }

  public static final Parcelable.Creator<Label> CREATOR =
      new Parcelable.Creator<Label>() {

        @Override
        public Label createFromParcel(Parcel in) {
          return new Label(in);
        }

        @Override
        public Label[] newArray(int size) {
          return new Label[size];
        }
      };

  private static final String FIELD_LANG_INDEX = Util.intToStringMaxRadix(0);
  private static final String FIELD_VALUE_INDEX = Util.intToStringMaxRadix(1);

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putString(FIELD_LANG_INDEX, lang);
    bundle.putString(FIELD_VALUE_INDEX, value);
    return bundle;
  }

  /**
   * Constructs an instance of {@link Label} from a {@link Bundle} produced by {@link #toBundle()}.
   */
  public static Label fromBundle(Bundle bundle) {
    return new Label(
        bundle.getString(FIELD_LANG_INDEX), checkNotNull(bundle.getString(FIELD_VALUE_INDEX)));
  }
}
