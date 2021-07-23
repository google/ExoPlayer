/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import androidx.annotation.IntDef;
import com.google.android.exoplayer2.util.Assertions;
import com.google.common.base.StandardSystemProperty;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Helper class to enable assertions based on golden-data dump files.
 *
 * <p>Allows the golden files to be easily updated with new data (see more info in the docs on
 * {@link #DUMP_FILE_ACTION}).
 *
 * <p>Compatible with {@link Dumper.Dumpable} but can also be used directly with Strings generated
 * through different means.
 */
public class DumpFileAsserts {

  /** The default test asset directory used if no other directory is specified. */
  public static final String DEFAULT_TEST_ASSET_DIRECTORY = "../../testdata/src/test/assets";

  private static final String DUMP_UPDATE_INSTRUCTIONS =
      "To update the dump file, change DumpFileAsserts#DUMP_FILE_ACTION to WRITE_TO_LOCAL (for"
          + " Robolectric tests) or WRITE_TO_DEVICE (for instrumentation tests) and re-run the"
          + " test.";

  /** Possible actions to take with the dumps passed to {@link #assertOutput}. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      flag = true,
      value = {COMPARE_WITH_EXISTING, WRITE_TO_LOCAL, WRITE_TO_DEVICE})
  private @interface DumpFilesAction {}
  /** Compare output with existing dump file. */
  private static final int COMPARE_WITH_EXISTING = 0;
  /**
   * Write output to the project folder {@code testdata/src/test}.
   *
   * <p>Enabling this option works when tests are run in Android Studio. It may not work when the
   * tests are run in another environment.
   */
  private static final int WRITE_TO_LOCAL = 1;
  /** Write output to folder {@code /storage/emulated/0/Android/data} of device. */
  private static final int WRITE_TO_DEVICE = 1 << 1;

  @DumpFilesAction private static final int DUMP_FILE_ACTION = COMPARE_WITH_EXISTING;

  private DumpFileAsserts() {}

  /**
   * Asserts that the dump output of {@code actual} is equal to the contents of {@code dumpFile} in
   * the {@link #DEFAULT_TEST_ASSET_DIRECTORY}.
   *
   * <p>If the assertion fails because of an intended change in the output or a new dump file needs
   * to be created, set {@link #DUMP_FILE_ACTION} to {@link #WRITE_TO_LOCAL} for local tests and to
   * {@link #WRITE_TO_DEVICE} for instrumentation tests, and run the test again. Instead of
   * assertion, {@code actual} will be written to {@code dumpFile}. For instrumentation tests, this
   * new dump file needs to be copied to the project asset folder manually.
   *
   * @param context A context.
   * @param actual The actual data.
   * @param dumpFile The file path of the dump file in the assets directory.
   */
  public static void assertOutput(Context context, Dumper.Dumpable actual, String dumpFile)
      throws IOException {
    assertOutput(
        context, new Dumper().add(actual).toString(), DEFAULT_TEST_ASSET_DIRECTORY, dumpFile);
  }

  /**
   * Asserts that the dump output of {@code actual} is equal to the contents of {@code dumpFile} in
   * the {@code assetDirectory}.
   *
   * <p>If the assertion fails because of an intended change in the output or a new dump file needs
   * to be created, set {@link #DUMP_FILE_ACTION} to {@link #WRITE_TO_LOCAL} for local tests and to
   * {@link #WRITE_TO_DEVICE} for instrumentation tests, and run the test again. Instead of
   * assertion, {@code actual} will be written to {@code dumpFile}. For instrumentation tests, this
   * new dump file needs to be copied to the project asset folder manually.
   *
   * @param context A context.
   * @param actual The actual data.
   * @param assetDirectory The directory of the assets relative to the project working directory.
   *     Only used when {@link #DUMP_FILE_ACTION} is set to {@link #WRITE_TO_LOCAL}.
   * @param dumpFile The file path of the dump file in the assets directory.
   */
  public static void assertOutput(
      Context context, Dumper.Dumpable actual, String assetDirectory, String dumpFile)
      throws IOException {
    assertOutput(context, new Dumper().add(actual).toString(), assetDirectory, dumpFile);
  }

  /**
   * Asserts that {@code actual} is equal to the contents of {@code dumpFile} in the {@link
   * #DEFAULT_TEST_ASSET_DIRECTORY}.
   *
   * <p>If the assertion fails because of an intended change in the output or a new dump file needs
   * to be created, set {@link #DUMP_FILE_ACTION} to {@link #WRITE_TO_LOCAL} for local tests and to
   * {@link #WRITE_TO_DEVICE} for instrumentation tests, and run the test again. Instead of
   * assertion, {@code actual} will be written to {@code dumpFile}. For instrumentation tests, this
   * new dump file needs to be copied to the project asset folder manually.
   *
   * @param context A context.
   * @param actual The actual data.
   * @param dumpFile The file path of the dump file in the assets directory.
   */
  public static void assertOutput(Context context, String actual, String dumpFile)
      throws IOException {
    assertOutput(context, actual, DEFAULT_TEST_ASSET_DIRECTORY, dumpFile);
  }

  /**
   * Asserts that {@code actual} is equal to the contents of {@code dumpFile} in {@code
   * assetDirectory}.
   *
   * <p>If the assertion fails because of an intended change in the output or a new dump file needs
   * to be created, set {@link #DUMP_FILE_ACTION} to {@link #WRITE_TO_LOCAL} for local tests and to
   * {@link #WRITE_TO_DEVICE} for instrumentation tests, and run the test again. Instead of
   * assertion, {@code actual} will be written to {@code dumpFile}. For instrumentation tests, this
   * new dump file needs to be copied to the project asset folder manually.
   *
   * @param context A context.
   * @param actual The actual data.
   * @param assetDirectory The directory of the assets relative to the project working directory.
   *     Only used when {@link #DUMP_FILE_ACTION} is set to {@link #WRITE_TO_LOCAL}.
   * @param dumpFile The file path of the dump file in the assets directory.
   */
  public static void assertOutput(
      Context context, String actual, String assetDirectory, String dumpFile) throws IOException {
    if (DUMP_FILE_ACTION == COMPARE_WITH_EXISTING) {
      String expected;
      try {
        expected = TestUtil.getString(context, dumpFile);
      } catch (FileNotFoundException e) {
        throw new IOException("Dump file not found. " + DUMP_UPDATE_INSTRUCTIONS, e);
      }
      assertWithMessage(
              "Actual data doesn't match dump file: %s\n%s", dumpFile, DUMP_UPDATE_INSTRUCTIONS)
          .that(actual)
          .isEqualTo(expected);
    } else {
      File file =
          DUMP_FILE_ACTION == WRITE_TO_LOCAL
              ? new File(StandardSystemProperty.USER_DIR.value(), assetDirectory)
              : context.getExternalFilesDir(null);
      file = new File(file, dumpFile);
      Assertions.checkStateNotNull(file.getParentFile()).mkdirs();
      PrintWriter out = new PrintWriter(file);
      out.print(actual);
      out.close();
    }
  }
}
