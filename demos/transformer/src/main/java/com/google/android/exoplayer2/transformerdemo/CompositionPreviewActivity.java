/*
 * Copyright 2023 The Android Open Source Project
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
package com.google.android.exoplayer2.transformerdemo;

import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.transformer.Composition;
import com.google.android.exoplayer2.transformer.CompositionPlayer;
import com.google.android.exoplayer2.transformer.EditedMediaItem;
import com.google.android.exoplayer2.transformer.EditedMediaItemSequence;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * An {@link Activity} that previews compositions, using {@link
 * com.google.android.exoplayer2.transformer.CompositionPlayer}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class CompositionPreviewActivity extends AppCompatActivity {
  private static final String TAG = "CompPreviewActivity";
  private static final ImmutableList<Integer> SEQUENCE_FILE_INDICES = ImmutableList.of(0, 2);

  private @MonotonicNonNull StyledPlayerView playerView;
  private @MonotonicNonNull RecyclerView presetList;
  private @MonotonicNonNull AppCompatButton previewButton;

  @Nullable private CompositionPlayer compositionPlayer;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.composition_preview_activity);

    String[] presetFileURIs = getResources().getStringArray(R.array.preset_uris);
    String[] presetFileDescriptions = getResources().getStringArray(R.array.preset_descriptions);

    playerView = findViewById(R.id.composition_player_view);
    presetList = findViewById(R.id.composition_preset_list);
    previewButton = findViewById(R.id.preview_button);
    previewButton.setOnClickListener(view -> previewComposition(view, presetFileURIs));

    LinearLayoutManager layoutManager =
        new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, /* reverseLayout= */ false);
    presetList.setLayoutManager(layoutManager);
    ArrayList<String> sequenceFiles = new ArrayList<>();
    for (int i = 0; i < SEQUENCE_FILE_INDICES.size(); i++) {
      if (SEQUENCE_FILE_INDICES.get(i) < presetFileDescriptions.length) {
        sequenceFiles.add(presetFileDescriptions[SEQUENCE_FILE_INDICES.get(i)]);
      }
    }
    AssetItemAdapter adapter = new AssetItemAdapter(sequenceFiles);
    presetList.setAdapter(adapter);
  }

  @Override
  protected void onStart() {
    super.onStart();
    checkStateNotNull(playerView).onResume();
  }

  @Override
  protected void onStop() {
    super.onStop();
    checkStateNotNull(playerView).onPause();
    releasePlayer();
  }

  private Composition prepareComposition(String[] presetFileURIs) {
    List<EditedMediaItem> mediaItems = new ArrayList<>();
    for (int i = 0; i < SEQUENCE_FILE_INDICES.size(); i++) {
      mediaItems.add(
          new EditedMediaItem.Builder(
                  MediaItem.fromUri(presetFileURIs[SEQUENCE_FILE_INDICES.get(i)]))
              .build());
    }
    EditedMediaItemSequence videoSequence =
        new EditedMediaItemSequence(Collections.unmodifiableList(mediaItems));
    return new Composition.Builder(ImmutableList.of(videoSequence)).build();
  }

  private void previewComposition(View view, String[] presetFileURIs) {
    releasePlayer();
    Composition composition = prepareComposition(presetFileURIs);
    checkStateNotNull(playerView).setPlayer(null);

    CompositionPlayer player = new CompositionPlayer(getApplicationContext(), /* looper= */ null);
    this.compositionPlayer = player;
    checkStateNotNull(playerView).setPlayer(compositionPlayer);
    checkStateNotNull(playerView).setControllerAutoShow(false);
    player.setComposition(composition);
    player.prepare();
    player.play();
  }

  private void releasePlayer() {
    if (compositionPlayer != null) {
      compositionPlayer.release();
      compositionPlayer = null;
    }
  }
}
