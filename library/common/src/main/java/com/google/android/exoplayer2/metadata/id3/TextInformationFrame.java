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
package com.google.android.exoplayer2.metadata.id3;

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.List;

/** Text information ID3 frame. */
public final class TextInformationFrame extends Id3Frame {

  @Nullable public final String description;
  public final String value;

  public TextInformationFrame(String id, @Nullable String description, String value) {
    super(id);
    this.description = description;
    this.value = value;
  }

  /* package */ TextInformationFrame(Parcel in) {
    super(castNonNull(in.readString()));
    description = in.readString();
    value = castNonNull(in.readString());
  }

  @Override
  public void populateMediaMetadata(MediaMetadata.Builder builder) {
    switch (id) {
      case "TT2":
      case "TIT2":
        builder.setTitle(value);
        break;
      case "TP1":
      case "TPE1":
        builder.setArtist(value);
        break;
      case "TP2":
      case "TPE2":
        builder.setAlbumArtist(value);
        break;
      case "TAL":
      case "TALB":
        builder.setAlbumTitle(value);
        break;
      case "TRK":
      case "TRCK":
        String[] trackNumbers = Util.split(value, "/");
        try {
          int trackNumber = Integer.parseInt(trackNumbers[0]);
          @Nullable
          Integer totalTrackCount =
              trackNumbers.length > 1 ? Integer.parseInt(trackNumbers[1]) : null;
          builder.setTrackNumber(trackNumber).setTotalTrackCount(totalTrackCount);
        } catch (NumberFormatException e) {
          // Do nothing, invalid input.
        }
        break;
      case "TYE":
      case "TYER":
        try {
          builder.setRecordingYear(Integer.parseInt(value));
        } catch (NumberFormatException e) {
          // Do nothing, invalid input.
        }
        break;
      case "TDA":
      case "TDAT":
        try {
          int month = Integer.parseInt(value.substring(2, 4));
          int day = Integer.parseInt(value.substring(0, 2));
          builder.setRecordingMonth(month).setRecordingDay(day);
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
          // Do nothing, invalid input.
        }
        break;
      case "TDRC":
        List<Integer> recordingDate = parseId3v2point4TimestampFrameForDate(value);
        switch (recordingDate.size()) {
          case 3:
            builder.setRecordingDay(recordingDate.get(2));
            // fall through
          case 2:
            builder.setRecordingMonth(recordingDate.get(1));
            // fall through
          case 1:
            builder.setRecordingYear(recordingDate.get(0));
            // fall through
            break;
          default:
            // Do nothing.
            break;
        }
        break;
      case "TDRL":
        List<Integer> releaseDate = parseId3v2point4TimestampFrameForDate(value);
        switch (releaseDate.size()) {
          case 3:
            builder.setReleaseDay(releaseDate.get(2));
            // fall through
          case 2:
            builder.setReleaseMonth(releaseDate.get(1));
            // fall through
          case 1:
            builder.setReleaseYear(releaseDate.get(0));
            // fall through
            break;
          default:
            // Do nothing.
            break;
        }
        break;
      case "TCM":
      case "TCOM":
        builder.setComposer(value);
        break;
      case "TP3":
      case "TPE3":
        builder.setConductor(value);
        break;
      case "TXT":
      case "TEXT":
        builder.setWriter(value);
        break;
      default:
        break;
    }
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    TextInformationFrame other = (TextInformationFrame) obj;
    return Util.areEqual(id, other.id)
        && Util.areEqual(description, other.description)
        && Util.areEqual(value, other.value);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + id.hashCode();
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + (value != null ? value.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return id + ": description=" + description + ": value=" + value;
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(id);
    dest.writeString(description);
    dest.writeString(value);
  }

  public static final Parcelable.Creator<TextInformationFrame> CREATOR =
      new Parcelable.Creator<TextInformationFrame>() {

        @Override
        public TextInformationFrame createFromParcel(Parcel in) {
          return new TextInformationFrame(in);
        }

        @Override
        public TextInformationFrame[] newArray(int size) {
          return new TextInformationFrame[size];
        }
      };

  // Private methods

  private static List<Integer> parseId3v2point4TimestampFrameForDate(String value) {
    // Timestamp string format is ISO-8601, can be `yyyy-MM-ddTHH:mm:ss`, or reduced precision
    // at each point, for example `yyyy-MM` or `yyyy-MM-ddTHH:mm`.
    List<Integer> dates = new ArrayList<>();
    try {
      if (value.length() >= 10) {
        dates.add(Integer.parseInt(value.substring(0, 4)));
        dates.add(Integer.parseInt(value.substring(5, 7)));
        dates.add(Integer.parseInt(value.substring(8, 10)));
      } else if (value.length() >= 7) {
        dates.add(Integer.parseInt(value.substring(0, 4)));
        dates.add(Integer.parseInt(value.substring(5, 7)));
      } else if (value.length() >= 4) {
        dates.add(Integer.parseInt(value.substring(0, 4)));
      }
    } catch (NumberFormatException e) {
      // Invalid output, return.
      return new ArrayList<>();
    }
    return dates;
  }
}
