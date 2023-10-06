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
package androidx.media3.demo.transformer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/** A {@link RecyclerView.Adapter} that displays assets in a sequence in a {@link RecyclerView}. */
public final class AssetItemAdapter extends RecyclerView.Adapter<AssetItemAdapter.ViewHolder> {
  private static final String TAG = "AssetItemAdapter";

  private final List<String> dataSet;

  /**
   * Creates a new instance
   *
   * @param data A list of items to populate RecyclerView with.
   */
  public AssetItemAdapter(List<String> data) {
    this.dataSet = new ArrayList<>(data);
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.preset_item, parent, false);
    return new ViewHolder(v);
  }

  @Override
  public void onBindViewHolder(ViewHolder holder, int position) {
    holder.getTextView().setText(dataSet.get(position));
  }

  @Override
  public int getItemCount() {
    return dataSet.size();
  }

  /** A {@link RecyclerView.ViewHolder} used to build {@link AssetItemAdapter}. */
  public static final class ViewHolder extends RecyclerView.ViewHolder {
    private final TextView textView;

    private ViewHolder(View view) {
      super(view);
      textView = view.findViewById(R.id.preset_name_text);
    }

    private TextView getTextView() {
      return textView;
    }
  }
}
