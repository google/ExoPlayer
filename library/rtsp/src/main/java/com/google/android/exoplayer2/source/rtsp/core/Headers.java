/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtsp.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Stores RTSP headers and provides methods to modify the headers in a thread safe way to avoid
 * the potential of creating snapshots of an inconsistent or unintended state.
 */
public final class Headers {

    private final Map<String, String> headers;
    private Map<String, String> headersSnapshot;

    public Headers() {
        headers = new LinkedHashMap<>();
    }

    /**
     * Added a specified header {@code value} for the specified {@code name}. If a header for
     * this name previously existed, the old value is replaced by the specified value.
     *
     * @param name The name of the header.
     * @param value The value of the header.
     */
    public synchronized void add(String name, String value) {
        headersSnapshot = null;
        headers.put(name, value);
    }

    /**
     * Sets the keys and values contained in the map. If a header previously existed, the old
     * value is replaced by the specified value. If a header previously existed and is not in the
     * map, the header is left unchanged.
     *
     * @param properties The request properties.
     */
    public synchronized void set(Map<String, String> properties) {
        headersSnapshot = null;
        headers.putAll(properties);
    }

    /**
     * Removes all headers previously existing and sets the keys and values of the map.
     *
     * @param properties The request properties.
     */
    public synchronized void clearAndSet(Map<String, String> properties) {
        headersSnapshot = null;
        headers.clear();
        headers.putAll(properties);
    }

    /**
     * Removes a header by name.
     *
     * @param name The name of the header to remove.
     */
    public synchronized void remove(String name) {
        headersSnapshot = null;
        headers.remove(name);
    }

    /**
     * Clears all headers.
     */
    public synchronized void clear() {
        headersSnapshot = null;
        headers.clear();
    }

    /**
     * Gets a snapshot of the headers.
     *
     * @return A snapshot of the request properties.
     */
    public synchronized Map<String, String> getSnapshot() {
        if (headersSnapshot == null) {
            headersSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        }
        return headersSnapshot;
    }

    /**
     * Gets the number of headers.
     *
     * @return The number of the headers.
     */
    public int size() {
        return headers.size();
    }

    /**
     * Gets the value of the header given by name.
     *
     * @return The value of the header given by name.
     */
    public String value(Header header) {
        if (header == null) throw new NullPointerException("header == null");
        if (headers.containsKey(header.toString()))
            return headers.get(header.toString());

        return null;
    }

    public boolean contains(Header header) {
        if (header == null) throw new NullPointerException("header == null");
        if (headers.containsKey(header.toString()))
            return true;

        return false;
    }

    public boolean hasBody() {
        if (contains(Header.ContentType) && contains(Header.ContentLength))
            return true;

        return false;
    }

    public long contentLength() {
        return stringToLong(value(Header.ContentLength));
    }

    /**
     * Gets an immutable case-insensitive set of header names.
     *
     * @return An immutable case-insensitive set of header names.
     */
    public Set<String> names() {
        TreeSet<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String header = entry.getKey();
            result.add(header);
        }

        return Collections.unmodifiableSet(result);
    }

    public static long contentLength(Request request) {
        return contentLength(request.headers);
    }

    public static long contentLength(Response response) {
        return contentLength(response.headers);
    }

    public static long contentLength(Headers headers) {
        return stringToLong(headers.value(Header.ContentLength));
    }

    private static long stringToLong(String str) {
        if (str == null) return -1L;
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}