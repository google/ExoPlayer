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
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link VorbisCommentDecoder}. */
@RunWith(AndroidJUnit4.class)
public final class VorbisCommentDecoderTest {

  @Test
  public void decode() {
    VorbisCommentDecoder decoder = new VorbisCommentDecoder();
    ArrayList<String> commentsList = new ArrayList<>();

    commentsList.add("Title=Test");
    commentsList.add("Artist=Test2");

    Metadata metadata = decoder.decodeVorbisComments(commentsList);

    assertThat(metadata.length()).isEqualTo(2);
    VorbisCommentFrame commentFrame = (VorbisCommentFrame) metadata.get(0);
    assertThat(commentFrame.key).isEqualTo("Title");
    assertThat(commentFrame.value).isEqualTo("Test");
    commentFrame = (VorbisCommentFrame) metadata.get(1);
    assertThat(commentFrame.key).isEqualTo("Artist");
    assertThat(commentFrame.value).isEqualTo("Test2");
  }

  @Test
  public void decodeEmptyList() {
    VorbisCommentDecoder decoder = new VorbisCommentDecoder();
    ArrayList<String> commentsList = new ArrayList<>();

    Metadata metadata = decoder.decodeVorbisComments(commentsList);

    assertThat(metadata).isNull();
  }

  @Test
  public void decodeTwoSeparators() {
    VorbisCommentDecoder decoder = new VorbisCommentDecoder();
    ArrayList<String> commentsList = new ArrayList<>();

    commentsList.add("Title=Test");
    commentsList.add("Artist=Test=2");

    Metadata metadata = decoder.decodeVorbisComments(commentsList);

    assertThat(metadata.length()).isEqualTo(1);
    VorbisCommentFrame commentFrame = (VorbisCommentFrame) metadata.get(0);
    assertThat(commentFrame.key).isEqualTo("Title");
    assertThat(commentFrame.value).isEqualTo("Test");
  }

  @Test
  public void decodeNoSeparators() {
    VorbisCommentDecoder decoder = new VorbisCommentDecoder();
    ArrayList<String> commentsList = new ArrayList<>();

    commentsList.add("TitleTest");
    commentsList.add("Artist=Test2");

    Metadata metadata = decoder.decodeVorbisComments(commentsList);

    assertThat(metadata.length()).isEqualTo(1);
    VorbisCommentFrame commentFrame = (VorbisCommentFrame) metadata.get(0);
    assertThat(commentFrame.key).isEqualTo("Artist");
    assertThat(commentFrame.value).isEqualTo("Test2");
  }
}
