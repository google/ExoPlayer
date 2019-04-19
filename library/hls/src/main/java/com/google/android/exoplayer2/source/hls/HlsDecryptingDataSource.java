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
package com.google.android.exoplayer2.source.hls;

import android.net.Uri;
import com.google.android.exoplayer2.upstream.DataSource;


/**
 * This defines a {@link DataSource} type that supports implementing HLS
 * decryption outside of the standard implementation provided by the
 * {@link HlsChunkSource} class.
 *
 * If the {@link DataSource} that is created by the {@link HlsDataSourceFactory}
 * instance used by the {@link HlsChunkSource} instance is an instance of
 * {@link HlsDecryptingDataSource}, then the {@link HlsChunkSource} will not perform
 * AES decryption of the data itself, but instead will allow the instance of
 * {@link HlsDecryptingDataSource} to do it.
 **/
public interface HlsDecryptingDataSource extends DataSource
{
    /**
     * Returns a {@link DataSource} that will decrypt the data before
     * returning it.  The upstream data is provided by this instance of {@link
     * HlsDecryptingDataSource} itself.  The returned {@link DataSource} may
     * be the same instance as this {@link HlsDecryptingDataSource}, or it may
     * be a separate {@link DataSource} instance that will do the decryption.
     *
     * @param keyUri is the uri of the key that was provided in the HLS
     *   Playlist for decryption of the Media Segment that is loaded by this
     *   {@link HlsDecryptingDataSource}
     * @param iv is the initialization vector to be used when decrypting
     *   the Media Segment this is loaded by this
     *   {@link HlsDecryptingDataSource}
     * @return The {@link DataSource} instance which will provide the
     *   decrypted Media Segment data
     **/
    DataSource getDecryptingDataSource(Uri keyUri, String iv);
}
