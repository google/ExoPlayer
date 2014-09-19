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
package com.google.android.exoplayer.util;

import com.google.android.exoplayer.ParserException;

import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

/**
 * Parses a manifest from an {@link InputStream}.
 *
 * @param <T> The type of the manifest being parsed.
 */
public interface ManifestParser<T> {

  /**
   * Parses a manifest from an {@link InputStream}.
   *
   * @param inputStream The input stream to consume.
   * @param inputEncoding The encoding of the input stream. May be null if the input encoding is
   *     unknown.
   * @param contentId The content id to which the manifest corresponds. May be null.
   * @param baseUri If the manifest contains relative uris, this is the uri they are relative to.
   *     May be null.
   * @return The parsed manifest.
   * @throws IOException If an error occurs reading the data.
   * @throws ParserException If an error occurs parsing the data.
   */
  T parse(InputStream inputStream, String inputEncoding, String contentId, Uri baseUri)
      throws IOException, ParserException;

}
