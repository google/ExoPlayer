package com.google.android.exoplayer2.metadata.dvbsi;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.exoplayer2.metadata.Metadata;

public class Ait implements Metadata.Entry {
  /*
  The application shall be started when the service is selected, unless the
  application is already running.
   */
  public static final int CONTROL_CODE_AUTOSTART = 0x01;
  /*
  The application is allowed to run while the service is selected, however it
  shall not start automatically when the service becomes selected.
   */
  public static final int CONTROL_CODE_PRESENT = 0x02;

  public final int controlCode;
  public final String url;

  Ait(int controlCode, String url) {
    this.controlCode = controlCode;
    this.url = url;
  }

  @Override
  public String toString() {
    return "Ait(controlCode = " + controlCode + ", url = " + url + ")";
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel parcel, int i) {
    parcel.writeString(url);
    parcel.writeInt(controlCode);
  }

  public static final Parcelable.Creator<Ait> CREATOR =
      new Parcelable.Creator<Ait>() {
        @Override
        public Ait createFromParcel(Parcel in) {
          String url = in.readString();
          int controlCode = in.readInt();
          return new Ait(controlCode, url);
        }

        @Override
        public Ait[] newArray(int size) {
          return new Ait[size];
        }
      };
}
