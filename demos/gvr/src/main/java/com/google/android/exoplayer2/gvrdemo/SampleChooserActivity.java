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
package com.google.android.exoplayer2.gvrdemo;

import static com.google.android.exoplayer2.gvrdemo.PlayerActivity.SPHERICAL_STEREO_MODE_LEFT_RIGHT;
import static com.google.android.exoplayer2.gvrdemo.PlayerActivity.SPHERICAL_STEREO_MODE_MONO;
import static com.google.android.exoplayer2.gvrdemo.PlayerActivity.SPHERICAL_STEREO_MODE_TOP_BOTTOM;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/** An activity for selecting from a list of media samples. */
public class SampleChooserActivity extends Activity {

  private final Sample[] samples =
      new Sample[] {
        new Sample(
            "Congo (360 top-bottom stereo)",
            "https://storage.googleapis.com/exoplayer-test-media-1/360/congo.mp4",
            SPHERICAL_STEREO_MODE_TOP_BOTTOM),
        new Sample(
            "Sphericalv2 (180 top-bottom stereo)",
            "https://storage.googleapis.com/exoplayer-test-media-1/360/sphericalv2.mp4",
            SPHERICAL_STEREO_MODE_TOP_BOTTOM),
        new Sample(
            "Iceland (360 top-bottom stereo ts)",
            "https://storage.googleapis.com/exoplayer-test-media-1/360/iceland0.ts",
            SPHERICAL_STEREO_MODE_TOP_BOTTOM),
        new Sample(
            "Camera motion metadata test",
            "https://storage.googleapis.com/exoplayer-test-media-internal-"
                + "63834241aced7884c2544af1a3452e01/vr180/synthetic_with_camm.mp4",
            SPHERICAL_STEREO_MODE_TOP_BOTTOM),
        new Sample(
            "actual_camera_cat",
            "https://storage.googleapis.com/exoplayer-test-media-internal-"
                + "63834241aced7884c2544af1a3452e01/vr180/actual_camera_cat.mp4",
            SPHERICAL_STEREO_MODE_TOP_BOTTOM),
        new Sample(
            "johnny_stitched",
            "https://storage.googleapis.com/exoplayer-test-media-internal-"
                + "63834241aced7884c2544af1a3452e01/vr180/johnny_stitched.mp4",
            SPHERICAL_STEREO_MODE_TOP_BOTTOM),
        new Sample(
            "lenovo_birds.vr",
            "https://storage.googleapis.com/exoplayer-test-media-internal-"
                + "63834241aced7884c2544af1a3452e01/vr180/lenovo_birds.vr.mp4",
            SPHERICAL_STEREO_MODE_TOP_BOTTOM),
        new Sample(
            "mono_v1_sample",
            "https://storage.googleapis.com/exoplayer-test-media-internal-"
                + "63834241aced7884c2544af1a3452e01/vr180/mono_v1_sample.mp4",
            SPHERICAL_STEREO_MODE_MONO),
        new Sample(
            "not_vr180_actually_shot_with_moto_mod",
            "https://storage.googleapis.com/exoplayer-test-media-internal-"
                + "63834241aced7884c2544af1a3452e01/vr180/"
                + "not_vr180_actually_shot_with_moto_mod.mp4",
            SPHERICAL_STEREO_MODE_TOP_BOTTOM),
        new Sample(
            "stereo_v1_sample",
            "https://storage.googleapis.com/exoplayer-test-media-internal-"
                + "63834241aced7884c2544af1a3452e01/vr180/stereo_v1_sample.mp4",
            SPHERICAL_STEREO_MODE_TOP_BOTTOM),
        new Sample(
            "yi_giraffes.vr",
            "https://storage.googleapis.com/exoplayer-test-media-internal-"
                + "63834241aced7884c2544af1a3452e01/vr180/yi_giraffes.vr.mp4",
            SPHERICAL_STEREO_MODE_TOP_BOTTOM),
      };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_chooser_activity);
    ListView sampleListView = findViewById(R.id.sample_list);
    sampleListView.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, samples));
    sampleListView.setOnItemClickListener(
        (parent, view, position, id) ->
            startActivity(
                samples[position].buildIntent(/* context= */ SampleChooserActivity.this)));
  }

  private static final class Sample {
    public final String name;
    public final String uri;
    public final String extension;
    public final String sphericalStereoMode;

    public Sample(String name, String uri, String sphericalStereoMode) {
      this(name, uri, sphericalStereoMode, null);
    }

    public Sample(String name, String uri, String sphericalStereoMode, String extension) {
      this.name = name;
      this.uri = uri;
      this.extension = extension;
      this.sphericalStereoMode = sphericalStereoMode;
    }

    public Intent buildIntent(Context context) {
      Intent intent = new Intent(context, PlayerActivity.class);
      return intent
          .setData(Uri.parse(uri))
          .putExtra(PlayerActivity.EXTENSION_EXTRA, extension)
          .putExtra(PlayerActivity.SPHERICAL_STEREO_MODE_EXTRA, sphericalStereoMode);
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
