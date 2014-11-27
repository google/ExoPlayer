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

import com.google.android.exoplayer.upstream.DataSource;

import android.net.Uri;
import android.text.TextUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Miscellaneous utility functions.
 */
public final class Util {

  /**
   * Like {@link android.os.Build.VERSION#SDK_INT}, but in a place where it can be conveniently
   * overridden for local testing.
   */
  public static final int SDK_INT = android.os.Build.VERSION.SDK_INT;

  private static final Pattern XS_DATE_TIME_PATTERN = Pattern.compile(
      "(\\d\\d\\d\\d)\\-(\\d\\d)\\-(\\d\\d)[Tt]"
      + "(\\d\\d):(\\d\\d):(\\d\\d)(\\.(\\d+))?"
      + "([Zz]|((\\+|\\-)(\\d\\d):(\\d\\d)))?");

  private static final Pattern XS_DURATION_PATTERN =
      Pattern.compile("^P(([0-9]*)Y)?(([0-9]*)M)?(([0-9]*)D)?"
          + "(T(([0-9]*)H)?(([0-9]*)M)?(([0-9.]*)S)?)?$");

  private Util() {}

  /**
   * Returns true if the URL points to a file on the local device
   *
   * @param url The URL to test
   */
  public static boolean isUrlLocalFile(URL url) {
    return url.getProtocol().equals("file");
  }

  /**
   * Tests two objects for {@link Object#equals(Object)} equality, handling the case where one or
   * both may be null.
   *
   * @param o1 The first object.
   * @param o2 The second object.
   * @return {@code o1 == null ? o2 == null : o1.equals(o2)}.
   */
  public static boolean areEqual(Object o1, Object o2) {
    return o1 == null ? o2 == null : o1.equals(o2);
  }

  /**
   * Instantiates a new single threaded executor whose thread has the specified name.
   *
   * @param threadName The name of the thread.
   * @return The executor.
   */
  public static ExecutorService newSingleThreadExecutor(final String threadName) {
    return Executors.newSingleThreadExecutor(new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
        return new Thread(r, threadName);
      }
    });
  }

  /**
   * Instantiates a new single threaded scheduled executor whose thread has the specified name.
   *
   * @param threadName The name of the thread.
   * @return The executor.
   */
  public static ScheduledExecutorService newSingleThreadScheduledExecutor(final String threadName) {
    return Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
        return new Thread(r, threadName);
      }
    });
  }

  /**
   * Closes a {@link DataSource}, suppressing any {@link IOException} that may occur.
   *
   * @param dataSource The {@link DataSource} to close.
   */
  public static void closeQuietly(DataSource dataSource) {
    try {
      dataSource.close();
    } catch (IOException e) {
      // Ignore.
    }
  }

  /**
   * Converts text to lower case using {@link Locale#US}.
   *
   * @param text The text to convert.
   * @return The lower case text, or null if {@code text} is null.
   */
  public static String toLowerInvariant(String text) {
    return text == null ? null : text.toLowerCase(Locale.US);
  }

  /**
   * Like {@link Uri#parse(String)}, but discards the part of the uri that follows the final
   * forward slash.
   *
   * @param uriString An RFC 2396-compliant, encoded uri.
   * @return The parsed base uri.
   */
  public static Uri parseBaseUri(String uriString) {
    return Uri.parse(uriString.substring(0, uriString.lastIndexOf('/')));
  }

  /**
   * Merges a uri and a string to produce a new uri.
   * <p>
   * The uri is built according to the following rules:
   * <ul>
   * <li>If {@code baseUri} is null or if {@code stringUri} is absolute, then {@code baseUri} is
   * ignored and the uri consists solely of {@code stringUri}.
   * <li>If {@code stringUri} is null, then the uri consists solely of {@code baseUrl}.
   * <li>Otherwise, the uri consists of the concatenation of {@code baseUri} and {@code stringUri}.
   * </ul>
   *
   * @param baseUri A uri that can form the base of the merged uri.
   * @param stringUri A relative or absolute uri in string form.
   * @return The merged uri.
   */
  public static Uri getMergedUri(Uri baseUri, String stringUri) {
    if (stringUri == null) {
      return baseUri;
    }
    if (baseUri == null) {
      return Uri.parse(stringUri);
    }
    if (stringUri.startsWith("/")) {
      stringUri = stringUri.substring(1);
      return new Uri.Builder()
          .scheme(baseUri.getScheme())
          .authority(baseUri.getAuthority())
          .appendEncodedPath(stringUri)
          .build();
    }
    Uri uri = Uri.parse(stringUri);
    if (uri.isAbsolute()) {
      return uri;
    }
    return Uri.withAppendedPath(baseUri, stringUri);
  }

  /**
   * Returns the index of the largest value in an array that is less than (or optionally equal to)
   * a specified key.
   * <p>
   * The search is performed using a binary search algorithm, and so the array must be sorted.
   *
   * @param a The array to search.
   * @param key The key being searched for.
   * @param inclusive If the key is present in the array, whether to return the corresponding index.
   *     If false then the returned index corresponds to the largest value in the array that is
   *     strictly less than the key.
   * @param stayInBounds If true, then 0 will be returned in the case that the key is smaller than
   *     the smallest value in the array. If false then -1 will be returned.
   */
  public static int binarySearchFloor(long[] a, long key, boolean inclusive, boolean stayInBounds) {
    int index = Arrays.binarySearch(a, key);
    index = index < 0 ? -(index + 2) : (inclusive ? index : (index - 1));
    return stayInBounds ? Math.max(0, index) : index;
  }

  /**
   * Returns the index of the smallest value in an array that is greater than (or optionally equal
   * to) a specified key.
   * <p>
   * The search is performed using a binary search algorithm, and so the array must be sorted.
   *
   * @param a The array to search.
   * @param key The key being searched for.
   * @param inclusive If the key is present in the array, whether to return the corresponding index.
   *     If false then the returned index corresponds to the smallest value in the array that is
   *     strictly greater than the key.
   * @param stayInBounds If true, then {@code (a.length - 1)} will be returned in the case that the
   *     key is greater than the largest value in the array. If false then {@code a.length} will be
   *     returned.
   */
  public static int binarySearchCeil(long[] a, long key, boolean inclusive, boolean stayInBounds) {
    int index = Arrays.binarySearch(a, key);
    index = index < 0 ? ~index : (inclusive ? index : (index + 1));
    return stayInBounds ? Math.min(a.length - 1, index) : index;
  }

  /**
   * Returns the index of the largest value in an list that is less than (or optionally equal to)
   * a specified key.
   * <p>
   * The search is performed using a binary search algorithm, and so the list must be sorted.
   *
   * @param list The list to search.
   * @param key The key being searched for.
   * @param inclusive If the key is present in the list, whether to return the corresponding index.
   *     If false then the returned index corresponds to the largest value in the list that is
   *     strictly less than the key.
   * @param stayInBounds If true, then 0 will be returned in the case that the key is smaller than
   *     the smallest value in the list. If false then -1 will be returned.
   */
  public static<T> int binarySearchFloor(List<? extends Comparable<? super T>> list, T key,
      boolean inclusive, boolean stayInBounds) {
    int index = Collections.binarySearch(list, key);
    index = index < 0 ? -(index + 2) : (inclusive ? index : (index - 1));
    return stayInBounds ? Math.max(0, index) : index;
  }

  /**
   * Returns the index of the smallest value in an list that is greater than (or optionally equal
   * to) a specified key.
   * <p>
   * The search is performed using a binary search algorithm, and so the list must be sorted.
   *
   * @param list The list to search.
   * @param key The key being searched for.
   * @param inclusive If the key is present in the list, whether to return the corresponding index.
   *     If false then the returned index corresponds to the smallest value in the list that is
   *     strictly greater than the key.
   * @param stayInBounds If true, then {@code (list.size() - 1)} will be returned in the case that
   *     the key is greater than the largest value in the list. If false then {@code list.size()}
   *     will be returned.
   */
  public static<T> int binarySearchCeil(List<? extends Comparable<? super T>> list, T key,
      boolean inclusive, boolean stayInBounds) {
    int index = Collections.binarySearch(list, key);
    index = index < 0 ? ~index : (inclusive ? index : (index + 1));
    return stayInBounds ? Math.min(list.size() - 1, index) : index;
  }

  /**
   * Parses an xs:duration attribute value, returning the parsed duration in milliseconds.
   *
   * @param value The attribute value to parse.
   * @return The parsed duration in milliseconds.
   */
  public static long parseXsDuration(String value) {
    Matcher matcher = XS_DURATION_PATTERN.matcher(value);
    if (matcher.matches()) {
      // Durations containing years and months aren't completely defined. We assume there are
      // 30.4368 days in a month, and 365.242 days in a year.
      String years = matcher.group(2);
      double durationSeconds = (years != null) ? Double.parseDouble(years) * 31556908 : 0;
      String months = matcher.group(4);
      durationSeconds += (months != null) ? Double.parseDouble(months) * 2629739 : 0;
      String days = matcher.group(6);
      durationSeconds += (days != null) ? Double.parseDouble(days) * 86400 : 0;
      String hours = matcher.group(9);
      durationSeconds += (hours != null) ? Double.parseDouble(hours) * 3600 : 0;
      String minutes = matcher.group(11);
      durationSeconds += (minutes != null) ? Double.parseDouble(minutes) * 60 : 0;
      String seconds = matcher.group(13);
      durationSeconds += (seconds != null) ? Double.parseDouble(seconds) : 0;
      return (long) (durationSeconds * 1000);
    } else {
      return (long) (Double.parseDouble(value) * 3600 * 1000);
    }
  }

  /**
   * Parses an xs:dateTime attribute value, returning the parsed timestamp in milliseconds since
   * the epoch.
   *
   * @param value The attribute value to parse.
   * @return The parsed timestamp in milliseconds since the epoch.
   */
  public static long parseXsDateTime(String value) throws ParseException {
    Matcher matcher = XS_DATE_TIME_PATTERN.matcher(value);
    if (!matcher.matches()) {
      throw new ParseException("Invalid date/time format: " + value, 0);
    }

    int timezoneShift;
    if (matcher.group(9) == null) {
      // No time zone specified.
      timezoneShift = 0;
    } else if (matcher.group(9).equalsIgnoreCase("Z")) {
      timezoneShift = 0;
    } else {
      timezoneShift = ((Integer.valueOf(matcher.group(12)) * 60
          + Integer.valueOf(matcher.group(13))));
      if (matcher.group(11).equals("-")) {
        timezoneShift *= -1;
      }
    }

    Calendar dateTime = new GregorianCalendar(TimeZone.getTimeZone("GMT"));

    dateTime.clear();
    // Note: The month value is 0-based, hence the -1 on group(2)
    dateTime.set(Integer.valueOf(matcher.group(1)),
                 Integer.valueOf(matcher.group(2)) - 1,
                 Integer.valueOf(matcher.group(3)),
                 Integer.valueOf(matcher.group(4)),
                 Integer.valueOf(matcher.group(5)),
                 Integer.valueOf(matcher.group(6)));
    if (!TextUtils.isEmpty(matcher.group(8))) {
      final BigDecimal bd = new BigDecimal("0." + matcher.group(8));
      // we care only for milliseconds, so movePointRight(3)
      dateTime.set(Calendar.MILLISECOND, bd.movePointRight(3).intValue());
    }

    long time = dateTime.getTimeInMillis();
    if (timezoneShift != 0) {
      time -= timezoneShift * 60000;
    }

    return time;
  }

  /**
   * Scales a large timestamp.
   * <p>
   * Logically, scaling consists of a multiplication followed by a division. The actual operations
   * performed are designed to minimize the probability of overflow.
   *
   * @param timestamp The timestamp to scale.
   * @param multiplier The multiplier.
   * @param divisor The divisor.
   * @return The scaled timestamp.
   */
  public static long scaleLargeTimestamp(long timestamp, long multiplier, long divisor) {
    if (divisor >= multiplier && (divisor % multiplier) == 0) {
      long divisionFactor = divisor / multiplier;
      return timestamp / divisionFactor;
    } else if (divisor < multiplier && (multiplier % divisor) == 0) {
      long multiplicationFactor = multiplier / divisor;
      return timestamp * multiplicationFactor;
    } else {
      double multiplicationFactor = (double) multiplier / divisor;
      return (long) (timestamp * multiplicationFactor);
    }
  }

  /**
   * Applies {@link #scaleLargeTimestamp(long, long, long)} to a list of unscaled timestamps.
   *
   * @param timestamps The timestamps to scale.
   * @param multiplier The multiplier.
   * @param divisor The divisor.
   * @return The scaled timestamps.
   */
  public static long[] scaleLargeTimestamps(List<Long> timestamps, long multiplier, long divisor) {
    long[] scaledTimestamps = new long[timestamps.size()];
    if (divisor >= multiplier && (divisor % multiplier) == 0) {
      long divisionFactor = divisor / multiplier;
      for (int i = 0; i < scaledTimestamps.length; i++) {
        scaledTimestamps[i] = timestamps.get(i) / divisionFactor;
      }
    } else if (divisor < multiplier && (multiplier % divisor) == 0) {
      long multiplicationFactor = multiplier / divisor;
      for (int i = 0; i < scaledTimestamps.length; i++) {
        scaledTimestamps[i] = timestamps.get(i) * multiplicationFactor;
      }
    } else {
      double multiplicationFactor = (double) multiplier / divisor;
      for (int i = 0; i < scaledTimestamps.length; i++) {
        scaledTimestamps[i] = (long) (timestamps.get(i) * multiplicationFactor);
      }
    }
    return scaledTimestamps;
  }

}
