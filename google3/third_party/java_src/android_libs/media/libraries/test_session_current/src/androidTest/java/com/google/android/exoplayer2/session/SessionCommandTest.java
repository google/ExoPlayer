/*
 * Copyright 2018 The Android Open Source Project
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

package com.google.android.exoplayer2.session;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link SessionCommand} and {@link SessionCommands}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class SessionCommandTest {
  // Prefix for all command codes
  private static final String PREFIX_COMMAND_CODE = "COMMAND_CODE_";

  private static final List<String> PREFIX_COMMAND_CODES = new ArrayList<>();

  static {
    PREFIX_COMMAND_CODES.add("COMMAND_CODE_PLAYER_");
    PREFIX_COMMAND_CODES.add("COMMAND_CODE_VOLUME_");
    PREFIX_COMMAND_CODES.add("COMMAND_CODE_SESSION_");
    PREFIX_COMMAND_CODES.add("COMMAND_CODE_LIBRARY_");
  }

  /** Test possible typos in naming */
  @Test
  public void codes_name() {
    List<Field> fields = getSessionCommandsFields("");
    for (int i = 0; i < fields.size(); i++) {
      String name = fields.get(i).getName();

      boolean matches = false;
      if (name.startsWith("COMMAND_VERSION_") || name.equals("COMMAND_CODE_CUSTOM")) {
        matches = true;
      }
      if (!matches) {
        for (int j = 0; j < PREFIX_COMMAND_CODES.size(); j++) {
          if (name.startsWith(PREFIX_COMMAND_CODES.get(j))) {
            matches = true;
            break;
          }
        }
      }
      assertWithMessage("Unexpected constant " + name).that(matches).isTrue();
    }
  }

  /** Tests possible code duplications in values */
  @Test
  public void codes_valueDuplication() throws IllegalAccessException {
    List<Field> fields = getSessionCommandsFields(PREFIX_COMMAND_CODE);
    Set<Integer> values = new HashSet<>();
    for (int i = 0; i < fields.size(); i++) {
      Integer value = fields.get(i).getInt(null);
      assertThat(values.add(value)).isTrue();
    }
  }

  /** Tests whether codes are continuous */
  @Test
  @Ignore
  public void codes_valueContinuous() throws IllegalAccessException {
    for (int i = 0; i < PREFIX_COMMAND_CODES.size(); i++) {
      List<Field> fields = getSessionCommandsFields(PREFIX_COMMAND_CODES.get(i));
      List<Integer> values = new ArrayList<>();
      for (int j = 0; j < fields.size(); j++) {
        values.add(fields.get(j).getInt(null));
      }
      Collections.sort(values);
      for (int j = 1; j < values.size(); j++) {
        assertWithMessage(
                "Command code isn't continuous. Missing "
                    + (values.get(j - 1) + 1)
                    + " in "
                    + PREFIX_COMMAND_CODES.get(i))
            .that((int) values.get(j))
            .isEqualTo(((int) values.get(j - 1)) + 1);
      }
    }
  }

  @Test
  public void addAllPredefinedCommands_withVersion1_notHaveVersion2Commands() {
    SessionCommands commands =
        new SessionCommands.Builder()
            .addAllPredefinedCommands(SessionCommand.COMMAND_VERSION_1)
            .build();
    assertThat(commands.contains(SessionCommand.COMMAND_CODE_SESSION_SET_MEDIA_URI)).isFalse();
  }

  @Test
  public void addAllPredefinedCommands_withVersion2_hasVersion2Commands() {
    SessionCommands commands =
        new SessionCommands.Builder()
            .addAllPredefinedCommands(SessionCommand.COMMAND_VERSION_2)
            .build();
    assertThat(commands.contains(SessionCommand.COMMAND_CODE_SESSION_SET_MEDIA_URI)).isTrue();
  }

  private static List<Field> getSessionCommandsFields(String prefix) {
    List<Field> list = new ArrayList<>();
    Field[] fields = SessionCommand.class.getFields();
    if (fields != null) {
      for (int i = 0; i < fields.length; i++) {
        if (isPublicStaticFinalInt(fields[i]) && fields[i].getName().startsWith(prefix)) {
          list.add(fields[i]);
        }
      }
    }
    return list;
  }

  private static boolean isPublicStaticFinalInt(Field field) {
    if (field.getType() != int.class) {
      return false;
    }
    int modifier = field.getModifiers();
    return Modifier.isPublic(modifier) && Modifier.isStatic(modifier) && Modifier.isFinal(modifier);
  }
}
