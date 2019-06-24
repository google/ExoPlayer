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
goog.module('exoplayer.cast.samplesinternal');

const {appendSamples} = goog.require('exoplayer.cast.debug');

appendSamples([
  {
    title: 'DAS: VOD',
    mimeType: 'application/dash+xml',
    media: {
      uri: 'https://demo-dash-pvr.zahs.tv/hd/manifest.mpd',
    },
  },
  {
    title: 'MP3',
    mimeType: 'audio/*',
    media: {
      uri: 'http://www.noiseaddicts.com/samples_1w72b820/4190.mp3',
    },
  },
  {
    title: 'DASH: live',
    mimeType: 'application/dash+xml',
    media: {
      uri: 'https://demo-dash-live.zahs.tv/sd/manifest.mpd',
    },
  },
  {
    title: 'HLS: live',
    mimeType: 'application/vnd.apple.mpegurl',
    media: {
      uri: 'https://demo-hls5-live.zahs.tv/sd/master.m3u8',
    },
  },
  {
    title: 'Live DASH (HD/Widevine)',
    mimeType: 'application/dash+xml',
    media: {
      uri: 'https://demo-dashenc-live.zahs.tv/hd/widevine.mpd',
    },
    drmSchemes: [
      {
        licenseServer: {
          uri: 'https://demo-dashenc-live.zahs.tv/hd/widevine-license',
        },
        uuid: 'edef8ba9-79d6-4ace-a3c8-27dcd51d21ed',
      },
    ],
  },
  {
    title: 'VOD DASH (HD/Widevine)',
    mimeType: 'application/dash+xml',
    media: {
      uri: 'https://demo-dashenc-pvr.zahs.tv/hd/widevine.mpd',
    },
    drmSchemes: [
      {
        licenseServer: {
          uri: 'https://demo-dashenc-live.zahs.tv/hd/widevine-license',
        },
        uuid: 'edef8ba9-79d6-4ace-a3c8-27dcd51d21ed',
      },
    ],
  },
]);
