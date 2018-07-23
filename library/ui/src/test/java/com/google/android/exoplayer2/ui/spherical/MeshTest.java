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
package com.google.android.exoplayer2.ui.spherical;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link Mesh}. */
@RunWith(RobolectricTestRunner.class)
public class MeshTest {
  private static final float EPSILON = .00001f;
  // This is a copy of Mesh.COORDS_PER_VERTEX which is private.
  private static final int COORDS_PER_VERTEX = 7;

  // Default 360 sphere.
  private static final float RADIUS = 1;
  private static final int LATITUDES = 12;
  private static final int LONGITUDES = 24;
  private static final float VERTICAL_FOV_DEGREES = 180;
  private static final float HORIZONTAL_FOV_DEGREES = 360;

  @Test
  public void testSphericalMesh() throws Exception {
    // Only the first param is important in this test.
    float[] data =
        Mesh.createUvSphereVertexData(
            RADIUS,
            LATITUDES,
            LONGITUDES,
            VERTICAL_FOV_DEGREES,
            HORIZONTAL_FOV_DEGREES,
            Mesh.MEDIA_STEREO_TOP_BOTTOM);

    assertThat(data.length).isGreaterThan(LATITUDES * LONGITUDES * COORDS_PER_VERTEX);
    assertEquals(0, data.length % COORDS_PER_VERTEX);

    for (int i = 0; i < data.length / COORDS_PER_VERTEX; ++i) {
      float x = data[i * COORDS_PER_VERTEX + 0];
      float y = data[i * COORDS_PER_VERTEX + 1];
      float z = data[i * COORDS_PER_VERTEX + 2];

      assertEquals(RADIUS, Math.sqrt(x * x + y * y + z * z), EPSILON);
    }
  }

  @Test
  public void testMeshTextureCoordinates() throws Exception {
    // 360 mono video.
    float[] data =
        Mesh.createUvSphereVertexData(
            RADIUS,
            LATITUDES,
            LONGITUDES,
            VERTICAL_FOV_DEGREES,
            HORIZONTAL_FOV_DEGREES,
            Mesh.MEDIA_MONOSCOPIC);
    // There should be more vertices than quads.
    assertThat(data.length).isGreaterThan(LATITUDES * LONGITUDES * COORDS_PER_VERTEX);
    assertEquals(0, data.length % COORDS_PER_VERTEX);

    for (int i = 0; i < data.length; i += COORDS_PER_VERTEX) {
      // For monoscopic meshes, the (3, 4) and (5, 6) tex coords in each vertex should be the same.
      assertEquals(data[i + 3], data[i + 5], EPSILON);
      assertEquals(data[i + 4], data[i + 6], EPSILON);
    }

    // Hemispherical stereo where longitudes := latitudes. This is not exactly Wally format, but
    // it's close.
    data =
        Mesh.createUvSphereVertexData(
            RADIUS,
            LATITUDES,
            LATITUDES,
            VERTICAL_FOV_DEGREES,
            VERTICAL_FOV_DEGREES,
            Mesh.MEDIA_STEREO_LEFT_RIGHT);
    assertThat(data.length).isGreaterThan(LATITUDES * LATITUDES * COORDS_PER_VERTEX);
    assertEquals(0, data.length % COORDS_PER_VERTEX);

    for (int i = 0; i < data.length; i += COORDS_PER_VERTEX) {
      // U coordinates should be on the left & right halves of the texture.
      assertThat(data[i + 3]).isAtMost(.5f);
      assertThat(data[i + 5]).isAtLeast(.5f);
      // V coordinates should be the same.
      assertEquals(data[i + 4], data[i + 6], EPSILON);
    }

    // Flat stereo.
    data =
        Mesh.createUvSphereVertexData(
            RADIUS,
            1,
            1, // Single quad.
            30,
            60, // Approximate "cinematic" screen.
            Mesh.MEDIA_STEREO_TOP_BOTTOM);
    assertEquals(0, data.length % COORDS_PER_VERTEX);

    for (int i = 0; i < data.length; i += COORDS_PER_VERTEX) {
      // U coordinates should be the same
      assertEquals(data[i + 3], data[i + 5], EPSILON);
      // V coordinates should be on the top & bottom halves of the texture.
      assertThat(data[i + 4]).isAtMost(.5f);
      assertThat(data[i + 6]).isAtLeast(.5f);
    }
  }

  @Test
  public void testArgumentValidation() {
    checkIllegalArgumentException(0, 1, 1, 1, 1);
    checkIllegalArgumentException(1, 0, 1, 1, 1);
    checkIllegalArgumentException(1, 1, 0, 1, 1);
    checkIllegalArgumentException(1, 1, 1, 0, 1);
    checkIllegalArgumentException(1, 1, 1, 181, 1);
    checkIllegalArgumentException(1, 1, 1, 1, 0);
    checkIllegalArgumentException(1, 1, 1, 1, 361);
  }

  private void checkIllegalArgumentException(
      float radius,
      int latitudes,
      int longitudes,
      float verticalFovDegrees,
      float horizontalFovDegrees) {
    try {
      Mesh.createUvSphereVertexData(
          radius,
          latitudes,
          longitudes,
          verticalFovDegrees,
          horizontalFovDegrees,
          Mesh.MEDIA_MONOSCOPIC);
      fail();
    } catch (IllegalArgumentException e) {
      // Do nothing. Expected.
    }
  }
}
