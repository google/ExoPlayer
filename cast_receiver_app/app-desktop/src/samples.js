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
goog.module('exoplayer.cast.samples');

const {appendSamples} = goog.require('exoplayer.cast.debug');

appendSamples([
  {
    title: 'DASH: multi-period',
    mimeType: 'application/dash+xml',
    media: {
      uri: 'https://storage.googleapis.com/exoplayer-test-media-internal-6383' +
          '4241aced7884c2544af1a3452e01/dash/multi-period/two-periods-minimal' +
          '-duration.mpd',
    },
  },
  {
    title: 'HLS: Angel one',
    mimeType: 'application/vnd.apple.mpegurl',
    media: {
      uri: 'https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hl' +
          's.m3u8',
    },
  },
  {
    title: 'MP4: Elephants dream',
    mimeType: 'video/*',
    media: {
      uri: 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/' +
          'ElephantsDream.mp4',
    },
  },
  {
    title: 'MKV: Android screens',
    mimeType: 'video/*',
    media: {
      uri: 'https://storage.googleapis.com/exoplayer-test-media-1/mkv/android' +
          '-screens-lavf-56.36.100-aac-avc-main-1280x720.mkv',
    },
  },
  {
    title: 'WV: HDCP not specified',
    mimeType: 'application/dash+xml',
    media: {
      uri: 'https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd',
    },
    drmSchemes: [
      {
        licenseServer: {
          uri: 'https://proxy.uat.widevine.com/proxy?video_id=d286538032258a1' +
              'c&provider=widevine_test',
        },
        uuid: 'edef8ba9-79d6-4ace-a3c8-27dcd51d21ed',
      },
    ],
  },
]);
