/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.effect;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.opengl.Matrix;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MatrixUtils}. */
@RunWith(AndroidJUnit4.class)
public class MatrixUtilsTest {

  @Test
  public void clipConvexPolygonToNdcRange_withTwoVertices_throwsException() {
    ImmutableList<float[]> vertices =
        ImmutableList.of(new float[] {1, 0, 1, 1}, new float[] {-0.5f, 0, 1, 1});

    assertThrows(
        IllegalArgumentException.class, () -> MatrixUtils.clipConvexPolygonToNdcRange(vertices));
  }

  @Test
  public void clipConvexPolygonToNdcRange_insideRange_returnsPolygon() {
    ImmutableList<float[]> vertices =
        ImmutableList.of(
            new float[] {-0.5f, 0, 0, 1}, new float[] {0.5f, 0, 0, 1}, new float[] {0, 0.5f, 0, 1});

    ImmutableList<float[]> clippedVertices = MatrixUtils.clipConvexPolygonToNdcRange(vertices);

    assertThat(clippedVertices).isEqualTo(vertices);
  }

  @Test
  public void clipConvexPolygonToNdcRange_onXClippingPlane_returnsPolygon() {
    ImmutableList<float[]> vertices =
        ImmutableList.of(
            new float[] {1, -0.5f, 0, 1}, new float[] {1, 0.5f, 0, 1}, new float[] {1, 0, 0.5f, 1});

    ImmutableList<float[]> clippedVertices = MatrixUtils.clipConvexPolygonToNdcRange(vertices);

    assertThat(clippedVertices).isEqualTo(vertices);
  }

  @Test
  public void clipConvexPolygonToNdcRange_onYClippingPlane_returnsPolygon() {
    ImmutableList<float[]> vertices =
        ImmutableList.of(
            new float[] {0, 1, -0.5f, 1}, new float[] {0, 1, 0.5f, 1}, new float[] {0.5f, 1, 0, 1});

    ImmutableList<float[]> clippedVertices = MatrixUtils.clipConvexPolygonToNdcRange(vertices);

    assertThat(clippedVertices).isEqualTo(vertices);
  }

  @Test
  public void clipConvexPolygonToNdcRange_onZClippingPlane_returnsPolygon() {
    ImmutableList<float[]> vertices =
        ImmutableList.of(
            new float[] {-0.5f, 0, 1, 1}, new float[] {0.5f, 0, 1, 1}, new float[] {0, 0.5f, 1, 1});

    ImmutableList<float[]> clippedVertices = MatrixUtils.clipConvexPolygonToNdcRange(vertices);

    assertThat(clippedVertices).isEqualTo(vertices);
  }

  @Test
  public void clipConvexPolygonToNdcRange_onClippingVolumeCorners_returnsPolygon() {
    ImmutableList<float[]> vertices =
        ImmutableList.of(
            new float[] {-1, 0, 1, 1}, new float[] {1, 0, 1, 1}, new float[] {0, 1, 1, 1});

    ImmutableList<float[]> clippedVertices = MatrixUtils.clipConvexPolygonToNdcRange(vertices);

    assertThat(clippedVertices).isEqualTo(vertices);
  }

  @Test
  public void clipConvexPolygonToNdcRange_outsideRange_returnsEmptyList() {
    ImmutableList<float[]> vertices =
        ImmutableList.of(
            new float[] {-0.5f, 0, 2, 1}, new float[] {0.5f, 0, 2, 1}, new float[] {0, 0.5f, 2, 1});

    ImmutableList<float[]> clippedVertices = MatrixUtils.clipConvexPolygonToNdcRange(vertices);

    assertThat(clippedVertices).isEmpty();
  }

  @Test
  public void clipConvexPolygonToNdcRange_withOneVertexOutsideRange_returnsClippedPolygon() {
    ImmutableList<float[]> vertices =
        ImmutableList.of(
            new float[] {-1, 0, 1, 1}, new float[] {1, 0, 1, 1}, new float[] {1, 2, 1, 1});

    ImmutableList<float[]> actualClippedVertices =
        MatrixUtils.clipConvexPolygonToNdcRange(vertices);

    ImmutableList<float[]> expectedClippedVertices =
        ImmutableList.of(
            new float[] {0, 1, 1, 1},
            new float[] {-1, 0, 1, 1},
            new float[] {1, 0, 1, 1},
            new float[] {1, 1, 1, 1});
    assertThat(actualClippedVertices.toArray()).isEqualTo(expectedClippedVertices.toArray());
  }

  @Test
  public void clipConvexPolygonToNdcRange_withTwoVerticesOutsideRange_returnsClippedPolygon() {
    ImmutableList<float[]> vertices =
        ImmutableList.of(
            new float[] {0, 1, 1, 1}, new float[] {-2, -3, 1, 1}, new float[] {2, -3, 1, 1});

    ImmutableList<float[]> actualClippedVertices =
        MatrixUtils.clipConvexPolygonToNdcRange(vertices);

    ImmutableList<float[]> expectedClippedVertices =
        ImmutableList.of(
            new float[] {1, -1, 1, 1}, new float[] {0, 1, 1, 1}, new float[] {-1, -1, 1, 1});
    assertThat(actualClippedVertices.toArray()).isEqualTo(expectedClippedVertices.toArray());
  }

  @Test
  public void clipConvexPolygonToNdcRange_enclosingRange_returnsRange() {
    ImmutableList<float[]> vertices =
        ImmutableList.of(
            new float[] {-2, -2, 1, 1},
            new float[] {2, -2, 1, 1},
            new float[] {2, 2, 1, 1},
            new float[] {-2, 2, 1, 1});

    ImmutableList<float[]> actualClippedVertices =
        MatrixUtils.clipConvexPolygonToNdcRange(vertices);

    ImmutableList<float[]> expectedClippedVertices =
        ImmutableList.of(
            new float[] {-1, 1, 1, 1},
            new float[] {-1, -1, 1, 1},
            new float[] {1, -1, 1, 1},
            new float[] {1, 1, 1, 1});
    assertThat(actualClippedVertices.toArray()).isEqualTo(expectedClippedVertices.toArray());
  }

  @Test
  public void transformPoints_returnsExpectedResult() {
    ImmutableList<float[]> points =
        ImmutableList.of(
            new float[] {-1, 0, 1, 1}, new float[] {1, 0, 1, 1}, new float[] {0, 1, 1, 1});
    float[] scaleMatrix = GlUtil.create4x4IdentityMatrix();
    Matrix.scaleM(scaleMatrix, /* mOffset= */ 0, /* x= */ 2, /* y= */ 3, /* z= */ 4);

    ImmutableList<float[]> actualTransformedPoints =
        MatrixUtils.transformPoints(scaleMatrix, points);

    ImmutableList<float[]> expectedTransformedPoints =
        ImmutableList.of(
            new float[] {-2, 0, 4, 1}, new float[] {2, 0, 4, 1}, new float[] {0, 3, 4, 1});
    assertThat(actualTransformedPoints.toArray()).isEqualTo(expectedTransformedPoints.toArray());
  }
}
