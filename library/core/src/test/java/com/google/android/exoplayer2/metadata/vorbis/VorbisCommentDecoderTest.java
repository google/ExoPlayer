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
package com.google.android.exoplayer2.metadata.vorbis;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.FlacStreamInfo;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link FlacStreamInfo}'s conversion of {@link ArrayList} to {@link Metadata}. */
@RunWith(AndroidJUnit4.class)
public final class VorbisCommentDecoderTest {

  @Test
  public void decode() {
    ArrayList<String> commentsList = new ArrayList<>();

    commentsList.add("Title=Song");
    commentsList.add("Artist=Singer");

    Metadata metadata = new FlacStreamInfo(0, 0, 0, 0, 0, 0, 0, 0, commentsList).vorbisComments;

    assertThat(metadata.length()).isEqualTo(2);
    VorbisCommentFrame commentFrame = (VorbisCommentFrame) metadata.get(0);
    assertThat(commentFrame.key).isEqualTo("Title");
    assertThat(commentFrame.value).isEqualTo("Song");
    commentFrame = (VorbisCommentFrame) metadata.get(1);
    assertThat(commentFrame.key).isEqualTo("Artist");
    assertThat(commentFrame.value).isEqualTo("Singer");
  }

  @Test
  public void decodeEmptyList() {
    ArrayList<String> commentsList = new ArrayList<>();

    Metadata metadata = new FlacStreamInfo(0, 0, 0, 0, 0, 0, 0, 0, commentsList).vorbisComments;

    assertThat(metadata).isNull();
  }

  @Test
  public void decodeTwoSeparators() {
    ArrayList<String> commentsList = new ArrayList<>();

    commentsList.add("Title=Song");
    commentsList.add("Artist=Sing=er");

    Metadata metadata = new FlacStreamInfo(0, 0, 0, 0, 0, 0, 0, 0, commentsList).vorbisComments;

    assertThat(metadata.length()).isEqualTo(2);
    VorbisCommentFrame commentFrame = (VorbisCommentFrame) metadata.get(0);
    assertThat(commentFrame.key).isEqualTo("Title");
    assertThat(commentFrame.value).isEqualTo("Song");
    commentFrame = (VorbisCommentFrame) metadata.get(1);
    assertThat(commentFrame.key).isEqualTo("Artist");
    assertThat(commentFrame.value).isEqualTo("Sing=er");
  }

  @Test
  public void decodeNoSeparators() {
    ArrayList<String> commentsList = new ArrayList<>();

    commentsList.add("TitleSong");
    commentsList.add("Artist=Singer");

    Metadata metadata = new FlacStreamInfo(0, 0, 0, 0, 0, 0, 0, 0, commentsList).vorbisComments;

    assertThat(metadata.length()).isEqualTo(1);
    VorbisCommentFrame commentFrame = (VorbisCommentFrame) metadata.get(0);
    assertThat(commentFrame.key).isEqualTo("Artist");
    assertThat(commentFrame.value).isEqualTo("Singer");
  }
}
