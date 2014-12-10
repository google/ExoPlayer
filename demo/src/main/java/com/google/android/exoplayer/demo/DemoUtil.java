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

import com.google.android.exoplayer.ExoPlayerLibraryInfo;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.UUID;

/**
 * Utility methods for the demo application.
 */
public class DemoUtil {

  public static final UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);

  public static final String CONTENT_TYPE_EXTRA = "content_type";
  public static final String CONTENT_ID_EXTRA = "content_id";

  public static final int TYPE_DASH = 0;
  public static final int TYPE_SS = 1;
  public static final int TYPE_OTHER = 2;

  public static final boolean EXPOSE_EXPERIMENTAL_FEATURES = false;

  private static final CookieManager defaultCookieManager;

  static {
    defaultCookieManager = new CookieManager();
    defaultCookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  }

  public static String getUserAgent(Context context) {
    String versionName;
    try {
      String packageName = context.getPackageName();
      PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
      versionName = info.versionName;
    } catch (NameNotFoundException e) {
      versionName = "?";
    }
    return "ExoPlayerDemo/" + versionName + " (Linux;Android " + Build.VERSION.RELEASE +
        ") " + "ExoPlayerLib/" + ExoPlayerLibraryInfo.VERSION;
  }

  public static byte[] executePost(String url, byte[] data, Map<String, String> requestProperties)
      throws MalformedURLException, IOException {
    HttpURLConnection urlConnection = null;
    try {
      urlConnection = (HttpURLConnection) new URL(url).openConnection();
      urlConnection.setRequestMethod("POST");
      urlConnection.setDoOutput(data != null);
      urlConnection.setDoInput(true);
      if (requestProperties != null) {
        for (Map.Entry<String, String> requestProperty : requestProperties.entrySet()) {
          urlConnection.setRequestProperty(requestProperty.getKey(), requestProperty.getValue());
        }
      }
      if (data != null) {
        OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
        out.write(data);
        out.close();
      }
      InputStream in = new BufferedInputStream(urlConnection.getInputStream());
      return convertInputStreamToByteArray(in);
    } finally {
      if (urlConnection != null) {
        urlConnection.disconnect();
      }
    }
  }

  private static byte[] convertInputStreamToByteArray(InputStream inputStream) throws IOException {
    byte[] bytes = null;
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte data[] = new byte[1024];
    int count;
    while ((count = inputStream.read(data)) != -1) {
      bos.write(data, 0, count);
    }
    bos.flush();
    bos.close();
    inputStream.close();
    bytes = bos.toByteArray();
    return bytes;
  }

  public static void setDefaultCookieManager() {
    CookieHandler currentHandler = CookieHandler.getDefault();
    if (currentHandler != defaultCookieManager) {
      CookieHandler.setDefault(defaultCookieManager);
    }
  }

}
