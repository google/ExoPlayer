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
package com.google.android.exoplayer.demo;

import com.google.android.exoplayer.demo.Samples.Sample;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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

    sampleAdapter.add(new Header("YouTube DASH"));
    sampleAdapter.addAll((Object[]) Samples.YOUTUBE_DASH_MP4);
    sampleAdapter.addAll((Object[]) Samples.YOUTUBE_DASH_WEBM);
    sampleAdapter.add(new Header("Widevine DASH Policy Tests (GTS)"));
    sampleAdapter.addAll((Object[]) Samples.WIDEVINE_GTS);
    sampleAdapter.add(new Header("Widevine HDCP Capabilities Tests"));
    sampleAdapter.addAll((Object[]) Samples.WIDEVINE_HDCP);
    sampleAdapter.add(new Header("Widevine DASH: MP4,H264"));
    sampleAdapter.addAll((Object[]) Samples.WIDEVINE_H264_MP4_CLEAR);
    sampleAdapter.addAll((Object[]) Samples.WIDEVINE_H264_MP4_SECURE);
    sampleAdapter.add(new Header("Widevine DASH: WebM,VP9"));
    sampleAdapter.addAll((Object[]) Samples.WIDEVINE_VP9_WEBM_CLEAR);
    sampleAdapter.addAll((Object[]) Samples.WIDEVINE_VP9_WEBM_SECURE);
    sampleAdapter.add(new Header("Widevine DASH: MP4,H265"));
    sampleAdapter.addAll((Object[]) Samples.WIDEVINE_H265_MP4_CLEAR);
    sampleAdapter.addAll((Object[]) Samples.WIDEVINE_H265_MP4_SECURE);
    sampleAdapter.add(new Header("SmoothStreaming"));
    sampleAdapter.addAll((Object[]) Samples.SMOOTHSTREAMING);
    sampleAdapter.add(new Header("HLS"));
    sampleAdapter.addAll((Object[]) Samples.HLS);
    sampleAdapter.add(new Header("Misc"));
    sampleAdapter.addAll((Object[]) Samples.MISC);

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
    Intent mpdIntent = new Intent(this, PlayerActivity.class)
        .setData(Uri.parse(sample.uri))
        .putExtra(PlayerActivity.CONTENT_ID_EXTRA, sample.contentId)
        .putExtra(PlayerActivity.CONTENT_TYPE_EXTRA, sample.type)
        .putExtra(PlayerActivity.PROVIDER_EXTRA, sample.provider);
    startActivity(mpdIntent);
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
        name = ((Sample) item).name;
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

  private static class Header {

    public final String name;

    public Header(String name) {
      this.name = name;
    }

  }

}
