/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.demo.vp9opus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * An activity for selecting from a number of samples.
 */
public class SampleChooserActivity extends Activity {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_chooser_activity);

    ListView sampleList = (ListView) findViewById(R.id.sample_list);
    final SampleAdapter sampleAdapter = new SampleAdapter(this);

    sampleAdapter.add(new Header("Local VP9 Video only"));
    sampleAdapter.add(new Sample("S/W Color Conversion - upto 720p", false));
    sampleAdapter.add(new Sample("OpenGL", true));
    sampleAdapter.add(new Header("DASH - VP9 Only"));
    sampleAdapter.add(new Sample("Google Glass",
          "http://demos.webmproject.org/dash/201410/vp9_glass/manifest_vp9.mpd"));
    sampleAdapter.add(new Header("DASH - VP9 and Opus"));
    sampleAdapter.add(new Sample("Google Glass",
          "http://demos.webmproject.org/dash/201410/vp9_glass/manifest_vp9_opus.mpd"));
    sampleAdapter.add(new Header("DASH - VP9 and Vorbis"));
    sampleAdapter.add(new Sample("Google Glass",
          "http://demos.webmproject.org/dash/201410/vp9_glass/manifest_vp9_vorbis.mpd"));

    sampleList.setAdapter(sampleAdapter);
    sampleList.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Object item = sampleAdapter.getItem(position);
        if (item instanceof Sample) {
          onSampleSelected((Sample) item);
        }
      }
    });
  }

  private void onSampleSelected(Sample sample) {
    Intent playerIntent = new Intent(this, VideoPlayer.class)
        .putExtra(VideoPlayer.DASH_MANIFEST_URL_ID_EXTRA, sample.uri)
        .putExtra(VideoPlayer.USE_OPENGL_ID_EXTRA, sample.useOpenGL);
    startActivity(playerIntent);
  }

  private static class SampleAdapter extends ArrayAdapter<Object> {

    public SampleAdapter(Context context) {
      super(context, 0);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        int layoutId = getItemViewType(position) == 1 ? android.R.layout.simple_list_item_1
            : R.layout.sample_chooser_inline_header;
        view = LayoutInflater.from(getContext()).inflate(layoutId, null, false);
      }
      Object item = getItem(position);
      String name = null;
      if (item instanceof Sample) {
        name = ((Sample) item).description;
      } else if (item instanceof Header) {
        name = ((Header) item).name;
      }
      ((TextView) view).setText(name);
      return view;
    }

    @Override
    public int getItemViewType(int position) {
      return (getItem(position) instanceof Sample) ? 1 : 0;
    }

    @Override
    public int getViewTypeCount() {
      return 2;
    }

  }

  private static class Sample {

    public final String description;
    public final String uri;
    public final boolean useOpenGL;

    public Sample(String description, boolean useOpenGL) {
      this(description, null, useOpenGL);
    }

    public Sample(String description, String uri) {
      this(description, uri, true); // always use OpenGL for DASH playbacks.
    }

    public Sample(String description, String uri, boolean useOpenGL) {
      this.description = description;
      this.uri = uri;
      this.useOpenGL = useOpenGL;
    }

  }

  private static class Header {

    public final String name;

    public Header(String name) {
      this.name = name;
    }

  }

}
