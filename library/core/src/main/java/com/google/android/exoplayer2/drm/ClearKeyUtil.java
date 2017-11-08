/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.drm;

import android.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Utility methods for ClearKey.
 */
/* package */ final class ClearKeyUtil {

  private static final String TAG = "ClearKeyUtil";
  private static final Pattern REQUEST_KIDS_PATTERN = Pattern.compile("\"kids\":\\[\"(.*?)\"]");

  private ClearKeyUtil() {}

  /**
   * Adjusts ClearKey request data obtained from the Android ClearKey CDM to be spec compliant.
   *
   * @param request The request data.
   * @return The adjusted request data.
   */
  public static byte[] adjustRequestData(byte[] request) {
    if (Util.SDK_INT >= 27) {
      return request;
    }
    // Prior to O-MR1 the ClearKey CDM encoded the values in the "kids" array using Base64 rather
    // than Base64Url. See [Internal: b/64388098]. Any "/" characters that ended up in the request
    // as a result were not escaped as "\/". We know the exact request format from the platform's
    // InitDataParser.cpp, so we can use a regexp rather than parsing the JSON.
    String requestString = Util.fromUtf8Bytes(request);
    Matcher requestKidsMatcher = REQUEST_KIDS_PATTERN.matcher(requestString);
    if (!requestKidsMatcher.find()) {
      Log.e(TAG, "Failed to adjust request data: " + requestString);
      return request;
    }
    int kidsStartIndex = requestKidsMatcher.start(1);
    int kidsEndIndex = requestKidsMatcher.end(1);
    StringBuilder adjustedRequestBuilder = new StringBuilder(requestString);
    base64ToBase64Url(adjustedRequestBuilder, kidsStartIndex, kidsEndIndex);
    return Util.getUtf8Bytes(adjustedRequestBuilder.toString());
  }

  /**
   * Adjusts ClearKey response data to be suitable for providing to the Android ClearKey CDM.
   *
   * @param response The response data.
   * @return The adjusted response data.
   */
  public static byte[] adjustResponseData(byte[] response) {
    if (Util.SDK_INT >= 27) {
      return response;
    }
    // Prior to O-MR1 the ClearKey CDM expected Base64 encoding rather than Base64Url encoding for
    // the "k" and "kid" strings. See [Internal: b/64388098].
    try {
      JSONObject responseJson = new JSONObject(Util.fromUtf8Bytes(response));
      JSONArray keysArray = responseJson.getJSONArray("keys");
      for (int i = 0; i < keysArray.length(); i++) {
        JSONObject key = keysArray.getJSONObject(i);
        key.put("k", base64UrlToBase64(key.getString("k")));
        key.put("kid", base64UrlToBase64(key.getString("kid")));
      }
      return Util.getUtf8Bytes(responseJson.toString());
    } catch (JSONException e) {
      Log.e(TAG, "Failed to adjust response data: " + Util.fromUtf8Bytes(response), e);
      return response;
    }
  }

  private static void base64ToBase64Url(StringBuilder base64, int startIndex, int endIndex) {
    for (int i = startIndex; i < endIndex; i++) {
      switch (base64.charAt(i)) {
        case '+':
          base64.setCharAt(i, '-');
          break;
        case '/':
          base64.setCharAt(i, '_');
          break;
        default:
          break;
      }
    }
  }

  private static String base64UrlToBase64(String base64) {
    return base64.replace('-', '+').replace('_', '/');
  }

}
