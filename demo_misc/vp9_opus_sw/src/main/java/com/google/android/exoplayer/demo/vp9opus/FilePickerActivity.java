/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer.demo.vp9opus;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple file picker.
 */
public class FilePickerActivity extends ListActivity {

  public static final String FILENAME_EXTRA_ID = "filename";

  private List<String> listItems;
  private List<File> itemPaths;
  private TextView currentPathView;
  private File root;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.file_picker_activity);
    setResult(Activity.RESULT_CANCELED);
    currentPathView = (TextView) findViewById(R.id.path);
    root = new File(Environment.getExternalStorageDirectory().getPath());
    setDirectory(root);
  }

  private void setDirectory(File directory) {
    currentPathView.setText(getString(R.string.current_path, directory.getAbsolutePath()));
    listItems = new ArrayList<>();
    itemPaths = new ArrayList<>();
    File[] files = directory.listFiles();

    if (!directory.getAbsolutePath().equals(root.getAbsolutePath())) {
      listItems.add(root.getAbsolutePath());
      itemPaths.add(root);
      listItems.add("../");
      itemPaths.add(new File(directory.getParent()));
    }

    if (files != null) {
      for (File file : files) {
        if (!file.isHidden() && file.canRead()) {
          itemPaths.add(file);
          if (file.isDirectory()) {
            listItems.add(file.getName() + File.separator);
          } else {
            listItems.add(file.getName());
          }
        }
      }
    }

    setListAdapter(new ArrayAdapter<>(this, R.layout.rows, listItems));
  }

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    File file = itemPaths.get(position);
    if (file.isDirectory() && file.canRead()) {
      setDirectory(itemPaths.get(position));
    } else {
      Intent intent = new Intent();
      intent.putExtra(FILENAME_EXTRA_ID, file.getAbsolutePath());
      setResult(Activity.RESULT_OK, intent);
      finish();
    }
  }
}
