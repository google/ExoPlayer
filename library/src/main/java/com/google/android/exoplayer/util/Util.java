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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.ExoPlayerLibraryInfo;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

  /**
   * Like {@link android.os.Build#DEVICE}, but in a place where it can be conveniently overridden
   * for local testing.
   */
  public static final String DEVICE = android.os.Build.DEVICE;

  private static final Pattern XS_DATE_TIME_PATTERN = Pattern.compile(
      "(\\d\\d\\d\\d)\\-(\\d\\d)\\-(\\d\\d)[Tt]"
      + "(\\d\\d):(\\d\\d):(\\d\\d)(\\.(\\d+))?"
      + "([Zz]|((\\+|\\-)(\\d\\d):(\\d\\d)))?");

  private static final Pattern XS_DURATION_PATTERN =
      Pattern.compile("^(-)?P(([0-9]*)Y)?(([0-9]*)M)?(([0-9]*)D)?"
          + "(T(([0-9]*)H)?(([0-9]*)M)?(([0-9.]*)S)?)?$");

  private static final long MAX_BYTES_TO_DRAIN = 2048;

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
   * Tests whether an {@code items} array contains an object equal to {@code item}, according to
   * {@link Object#equals(Object)}.
   * <p>
   * If {@code item} is null then true is returned if and only if {@code items} contains null.
   *
   * @param items The array of items to search.
   * @param item The item to search for.
   * @return True if the array contains an object equal to the item being searched for.
   */
  public static boolean contains(Object[] items, Object item) {
    for (int i = 0; i < items.length; i++) {
      if (Util.areEqual(items[i], item)) {
        return true;
      }
    }
    return false;
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
   * Closes an {@link OutputStream}, suppressing any {@link IOException} that may occur.
   *
   * @param outputStream The {@link OutputStream} to close.
   */
  public static void closeQuietly(OutputStream outputStream) {
    try {
      outputStream.close();
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
   * Divides a {@code numerator} by a {@code denominator}, returning the ceiled result.
   *
   * @param numerator The numerator to divide.
   * @param denominator The denominator to divide by.
   * @return The ceiled result of the division.
   */
  public static int ceilDivide(int numerator, int denominator) {
    return (numerator + denominator - 1) / denominator;
  }

  /**
   * Divides a {@code numerator} by a {@code denominator}, returning the ceiled result.
   *
   * @param numerator The numerator to divide.
   * @param denominator The denominator to divide by.
   * @return The ceiled result of the division.
   */
  public static long ceilDivide(long numerator, long denominator) {
    return (numerator + denominator - 1) / denominator;
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
      boolean negated = !TextUtils.isEmpty(matcher.group(1));
      // Durations containing years and months aren't completely defined. We assume there are
      // 30.4368 days in a month, and 365.242 days in a year.
      String years = matcher.group(3);
      double durationSeconds = (years != null) ? Double.parseDouble(years) * 31556908 : 0;
      String months = matcher.group(5);
      durationSeconds += (months != null) ? Double.parseDouble(months) * 2629739 : 0;
      String days = matcher.group(7);
      durationSeconds += (days != null) ? Double.parseDouble(days) * 86400 : 0;
      String hours = matcher.group(10);
      durationSeconds += (hours != null) ? Double.parseDouble(hours) * 3600 : 0;
      String minutes = matcher.group(12);
      durationSeconds += (minutes != null) ? Double.parseDouble(minutes) * 60 : 0;
      String seconds = matcher.group(14);
      durationSeconds += (seconds != null) ? Double.parseDouble(seconds) : 0;
      long durationMillis = (long) (durationSeconds * 1000);
      return negated ? -durationMillis : durationMillis;
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
      timezoneShift = ((Integer.parseInt(matcher.group(12)) * 60
          + Integer.parseInt(matcher.group(13))));
      if (matcher.group(11).equals("-")) {
        timezoneShift *= -1;
      }
    }

    Calendar dateTime = new GregorianCalendar(TimeZone.getTimeZone("GMT"));

    dateTime.clear();
    // Note: The month value is 0-based, hence the -1 on group(2)
    dateTime.set(Integer.parseInt(matcher.group(1)),
                 Integer.parseInt(matcher.group(2)) - 1,
                 Integer.parseInt(matcher.group(3)),
                 Integer.parseInt(matcher.group(4)),
                 Integer.parseInt(matcher.group(5)),
                 Integer.parseInt(matcher.group(6)));
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

  /**
   * Applies {@link #scaleLargeTimestamp(long, long, long)} to an array of unscaled timestamps.
   *
   * @param timestamps The timestamps to scale.
   * @param multiplier The multiplier.
   * @param divisor The divisor.
   */
  public static void scaleLargeTimestampsInPlace(long[] timestamps, long multiplier, long divisor) {
    if (divisor >= multiplier && (divisor % multiplier) == 0) {
      long divisionFactor = divisor / multiplier;
      for (int i = 0; i < timestamps.length; i++) {
        timestamps[i] /= divisionFactor;
      }
    } else if (divisor < multiplier && (multiplier % divisor) == 0) {
      long multiplicationFactor = multiplier / divisor;
      for (int i = 0; i < timestamps.length; i++) {
        timestamps[i] *= multiplicationFactor;
      }
    } else {
      double multiplicationFactor = (double) multiplier / divisor;
      for (int i = 0; i < timestamps.length; i++) {
        timestamps[i] = (long) (timestamps[i] * multiplicationFactor);
      }
    }
  }

  /**
   * Converts a list of integers to a primitive array.
   *
   * @param list A list of integers.
   * @return The list in array form, or null if the input list was null.
   */
  public static int[] toArray(List<Integer> list) {
    if (list == null) {
      return null;
    }
    int length = list.size();
    int[] intArray = new int[length];
    for (int i = 0; i < length; i++) {
      intArray[i] = list.get(i);
    }
    return intArray;
  }

  /**
   * On platform API levels 19 and 20, okhttp's implementation of {@link InputStream#close} can
   * block for a long time if the stream has a lot of data remaining. Call this method before
   * closing the input stream to make a best effort to cause the input stream to encounter an
   * unexpected end of input, working around this issue. On other platform API levels, the method
   * does nothing.
   *
   * @param connection The connection whose {@link InputStream} should be terminated.
   * @param bytesRemaining The number of bytes remaining to be read from the input stream if its
   *     length is known. {@link C#LENGTH_UNBOUNDED} otherwise.
   */
  public static void maybeTerminateInputStream(HttpURLConnection connection, long bytesRemaining) {
    if (SDK_INT != 19 && SDK_INT != 20) {
      return;
    }

    try {
      InputStream inputStream = connection.getInputStream();
      if (bytesRemaining == C.LENGTH_UNBOUNDED) {
        // If the input stream has already ended, do nothing. The socket may be re-used.
        if (inputStream.read() == -1) {
          return;
        }
      } else if (bytesRemaining <= MAX_BYTES_TO_DRAIN) {
        // There isn't much data left. Prefer to allow it to drain, which may allow the socket to be
        // re-used.
        return;
      }
      String className = inputStream.getClass().getName();
      if (className.equals("com.android.okhttp.internal.http.HttpTransport$ChunkedInputStream")
          || className.equals(
              "com.android.okhttp.internal.http.HttpTransport$FixedLengthInputStream")) {
        Class<?> superclass = inputStream.getClass().getSuperclass();
        Method unexpectedEndOfInput = superclass.getDeclaredMethod("unexpectedEndOfInput");
        unexpectedEndOfInput.setAccessible(true);
        unexpectedEndOfInput.invoke(inputStream);
      }
    } catch (IOException e) {
      // The connection didn't ever have an input stream, or it was closed already.
    } catch (Exception e) {
      // Something went wrong. The device probably isn't using okhttp.
    }
  }

  /**
   * Given a {@link DataSpec} and a number of bytes already loaded, returns a {@link DataSpec}
   * that represents the remainder of the data.
   *
   * @param dataSpec The original {@link DataSpec}.
   * @param bytesLoaded The number of bytes already loaded.
   * @return A {@link DataSpec} that represents the remainder of the data.
   */
  public static DataSpec getRemainderDataSpec(DataSpec dataSpec, int bytesLoaded) {
    if (bytesLoaded == 0) {
      return dataSpec;
    } else {
      long remainingLength = dataSpec.length == C.LENGTH_UNBOUNDED ? C.LENGTH_UNBOUNDED
          : dataSpec.length - bytesLoaded;
      return new DataSpec(dataSpec.uri, dataSpec.position + bytesLoaded, remainingLength,
          dataSpec.key, dataSpec.flags);
    }
  }

  /**
   * Returns the integer equal to the big-endian concatenation of the characters in {@code string}
   * as bytes. {@code string} must contain four or fewer characters.
   */
  public static int getIntegerCodeForString(String string) {
    int length = string.length();
    Assertions.checkArgument(length <= 4);
    int result = 0;
    for (int i = 0; i < length; i++) {
      result <<= 8;
      result |= string.charAt(i);
    }
    return result;
  }

  /**
   * Returns a hex string representation of the data provided.
   *
   * @param data The byte array containing the data to be turned into a hex string.
   * @param beginIndex The begin index, inclusive.
   * @param endIndex The end index, exclusive.
   * @return A string containing the hex representation of the data provided.
   */
  public static String getHexStringFromBytes(byte[] data, int beginIndex, int endIndex) {
    StringBuffer dataStringBuffer = new StringBuffer(endIndex - beginIndex);
    for (int i = beginIndex; i < endIndex; i++) {
      dataStringBuffer.append(String.format("%02X", data[i]));
    }
    return dataStringBuffer.toString();
  }

  /**
   * Returns a user agent string based on the given application name and the library version.
   *
   * @param context A valid context of the calling application.
   * @param applicationName String that will be prefix'ed to the generated user agent.
   * @return A user agent string generated using the applicationName and the library version.
   */
  public static String getUserAgent(Context context, String applicationName) {
    String versionName;
    try {
      String packageName = context.getPackageName();
      PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
      versionName = info.versionName;
    } catch (NameNotFoundException e) {
      versionName = "?";
    }
    return applicationName + "/" + versionName + " (Linux;Android " + Build.VERSION.RELEASE
        + ") " + "ExoPlayerLib/" + ExoPlayerLibraryInfo.VERSION;
  }

  /**
   * Executes a post request using {@link HttpURLConnection}.
   *
   * @param url The request URL.
   * @param data The request body, or null.
   * @param requestProperties Request properties, or null.
   * @return The response body.
   * @throws IOException If an error occurred making the request.
   */
  // TODO: Remove this and use HttpDataSource once DataSpec supports inclusion of a POST body.
  public static byte[] executePost(String url, byte[] data, Map<String, String> requestProperties)
      throws IOException {
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
      // Write the request body, if there is one.
      if (data != null) {
        OutputStream out = urlConnection.getOutputStream();
        try {
          out.write(data);
        } finally {
          out.close();
        }
      }
      // Read and return the response body.
      InputStream inputStream = urlConnection.getInputStream();
      try {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte scratch[] = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(scratch)) != -1) {
          byteArrayOutputStream.write(scratch, 0, bytesRead);
        }
        return byteArrayOutputStream.toByteArray();
      } finally {
        inputStream.close();
      }
    } finally {
      if (urlConnection != null) {
        urlConnection.disconnect();
      }
    }
  }

}
