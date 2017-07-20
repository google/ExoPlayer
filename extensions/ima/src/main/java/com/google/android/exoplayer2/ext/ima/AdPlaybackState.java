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
package com.google.android.exoplayer2.ext.ima;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import java.util.Arrays;

/**
 * Represents the structure of ads to play and the state of loaded/played ads.
 */
/* package */ final class AdPlaybackState {

  /**
   * The number of ad groups.
   */
  public final int adGroupCount;
  /**
   * The times of ad groups, in microseconds. A final element with the value
   * {@link C#TIME_END_OF_SOURCE} indicates a postroll ad.
   */
  public final long[] adGroupTimesUs;
  /**
   * The number of ads in each ad group. An element may be {@link C#LENGTH_UNSET} if the number of
   * ads is not yet known.
   */
  public final int[] adCounts;
  /**
   * The number of ads loaded so far in each ad group.
   */
  public final int[] adsLoadedCounts;
  /**
   * The number of ads played so far in each ad group.
   */
  public final int[] adsPlayedCounts;
  /**
   * The URI of each ad in each ad group.
   */
  public final Uri[][] adUris;

  /**
   * The position offset in the first unplayed ad at which to begin playback, in microseconds.
   */
  public long adResumePositionUs;

  /**
   * Creates a new ad playback state with the specified ad group times.
   *
   * @param adGroupTimesUs The times of ad groups in microseconds. A final element with the value
   *     {@link C#TIME_END_OF_SOURCE} indicates that there is a postroll ad.
   */
  public AdPlaybackState(long[] adGroupTimesUs) {
    this.adGroupTimesUs = adGroupTimesUs;
    adGroupCount = adGroupTimesUs.length;
    adsPlayedCounts = new int[adGroupCount];
    adCounts = new int[adGroupCount];
    Arrays.fill(adCounts, C.LENGTH_UNSET);
    adUris = new Uri[adGroupCount][];
    Arrays.fill(adUris, new Uri[0]);
    adsLoadedCounts = new int[adGroupTimesUs.length];
  }

  private AdPlaybackState(long[] adGroupTimesUs, int[] adCounts, int[] adsLoadedCounts,
      int[] adsPlayedCounts, Uri[][] adUris, long adResumePositionUs) {
    this.adGroupTimesUs = adGroupTimesUs;
    this.adCounts = adCounts;
    this.adsLoadedCounts = adsLoadedCounts;
    this.adsPlayedCounts = adsPlayedCounts;
    this.adUris = adUris;
    this.adResumePositionUs = adResumePositionUs;
    adGroupCount = adGroupTimesUs.length;
  }

  /**
   * Returns a deep copy of this instance.
   */
  public AdPlaybackState copy() {
    Uri[][] adUris = new Uri[adGroupTimesUs.length][];
    for (int i = 0; i < this.adUris.length; i++) {
      adUris[i] = Arrays.copyOf(this.adUris[i], this.adUris[i].length);
    }
    return new AdPlaybackState(Arrays.copyOf(adGroupTimesUs, adGroupCount),
        Arrays.copyOf(adCounts, adGroupCount), Arrays.copyOf(adsLoadedCounts, adGroupCount),
        Arrays.copyOf(adsPlayedCounts, adGroupCount), adUris, adResumePositionUs);
  }

  /**
   * Sets the number of ads in the specified ad group.
   */
  public void setAdCount(int adGroupIndex, int adCount) {
    adCounts[adGroupIndex] = adCount;
  }

  /**
   * Adds an ad to the specified ad group.
   */
  public void addAdUri(int adGroupIndex, Uri uri) {
    int adIndexInAdGroup = adUris[adGroupIndex].length;
    adUris[adGroupIndex] = Arrays.copyOf(adUris[adGroupIndex], adIndexInAdGroup + 1);
    adUris[adGroupIndex][adIndexInAdGroup] = uri;
    adsLoadedCounts[adGroupIndex]++;
  }

  /**
   * Marks the last ad in the specified ad group as played.
   */
  public void playedAd(int adGroupIndex) {
    adResumePositionUs = 0;
    adsPlayedCounts[adGroupIndex]++;
  }

  /**
   * Marks all ads in the specified ad group as played.
   */
  public void playedAdGroup(int adGroupIndex) {
    adResumePositionUs = 0;
    if (adCounts[adGroupIndex] == C.LENGTH_UNSET) {
      adCounts[adGroupIndex] = 0;
    }
    adsPlayedCounts[adGroupIndex] = adCounts[adGroupIndex];
  }

  /**
   * Sets the position offset in the first unplayed ad at which to begin playback, in microseconds.
   */
  public void setAdResumePositionUs(long adResumePositionUs) {
    this.adResumePositionUs = adResumePositionUs;
  }

}
