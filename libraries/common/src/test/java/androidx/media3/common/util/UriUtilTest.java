/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.common.util;

import static androidx.media3.common.util.UriUtil.removeQueryParameter;
import static androidx.media3.common.util.UriUtil.resolve;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link UriUtil}. */
@RunWith(AndroidJUnit4.class)
public final class UriUtilTest {

  /**
   * Tests normal usage of {@link UriUtil#resolve(String, String)}.
   *
   * <p>The test cases are taken from RFC-3986 5.4.1.
   */
  @Test
  public void resolveNormal() {
    String base = "http://a/b/c/d;p?q";

    assertThat(resolve(base, "g:h")).isEqualTo("g:h");
    assertThat(resolve(base, "g")).isEqualTo("http://a/b/c/g");
    assertThat(resolve(base, "g/")).isEqualTo("http://a/b/c/g/");
    assertThat(resolve(base, "/g")).isEqualTo("http://a/g");
    assertThat(resolve(base, "//g")).isEqualTo("http://g");
    assertThat(resolve(base, "//g:80")).isEqualTo("http://g:80");
    assertThat(resolve(base, "?y")).isEqualTo("http://a/b/c/d;p?y");
    assertThat(resolve(base, "g?y")).isEqualTo("http://a/b/c/g?y");
    assertThat(resolve(base, "#s")).isEqualTo("http://a/b/c/d;p?q#s");
    assertThat(resolve(base, "g#s")).isEqualTo("http://a/b/c/g#s");
    assertThat(resolve(base, "g?y#s")).isEqualTo("http://a/b/c/g?y#s");
    assertThat(resolve(base, ";x")).isEqualTo("http://a/b/c/;x");
    assertThat(resolve(base, "g;x")).isEqualTo("http://a/b/c/g;x");
    assertThat(resolve(base, "g;x?y#s")).isEqualTo("http://a/b/c/g;x?y#s");
    assertThat(resolve(base, "")).isEqualTo("http://a/b/c/d;p?q");
    assertThat(resolve(base, ".")).isEqualTo("http://a/b/c/");
    assertThat(resolve(base, "./")).isEqualTo("http://a/b/c/");
    assertThat(resolve(base, "..")).isEqualTo("http://a/b/");
    assertThat(resolve(base, "../")).isEqualTo("http://a/b/");
    assertThat(resolve(base, "../g")).isEqualTo("http://a/b/g");
    assertThat(resolve(base, "../..")).isEqualTo("http://a/");
    assertThat(resolve(base, "../../")).isEqualTo("http://a/");
    assertThat(resolve(base, "../../g")).isEqualTo("http://a/g");
  }

  /**
   * Tests abnormal usage of {@link UriUtil#resolve(String, String)}.
   *
   * <p>The test cases are taken from RFC-3986 5.4.2.
   */
  @Test
  public void resolveAbnormal() {
    String base = "http://a/b/c/d;p?q";

    assertThat(resolve(base, "../../../g")).isEqualTo("http://a/g");
    assertThat(resolve(base, "../../../../g")).isEqualTo("http://a/g");

    assertThat(resolve(base, "/./g")).isEqualTo("http://a/g");
    assertThat(resolve(base, "/../g")).isEqualTo("http://a/g");
    assertThat(resolve(base, "g.")).isEqualTo("http://a/b/c/g.");
    assertThat(resolve(base, ".g")).isEqualTo("http://a/b/c/.g");
    assertThat(resolve(base, "g..")).isEqualTo("http://a/b/c/g..");
    assertThat(resolve(base, "..g")).isEqualTo("http://a/b/c/..g");

    assertThat(resolve(base, "./../g")).isEqualTo("http://a/b/g");
    assertThat(resolve(base, "./g/.")).isEqualTo("http://a/b/c/g/");
    assertThat(resolve(base, "g/./h")).isEqualTo("http://a/b/c/g/h");
    assertThat(resolve(base, "g/../h")).isEqualTo("http://a/b/c/h");
    assertThat(resolve(base, "g;x=1/./y")).isEqualTo("http://a/b/c/g;x=1/y");
    assertThat(resolve(base, "g;x=1/../y")).isEqualTo("http://a/b/c/y");

    assertThat(resolve(base, "g?y/./x")).isEqualTo("http://a/b/c/g?y/./x");
    assertThat(resolve(base, "g?y/../x")).isEqualTo("http://a/b/c/g?y/../x");
    assertThat(resolve(base, "g#s/./x")).isEqualTo("http://a/b/c/g#s/./x");
    assertThat(resolve(base, "g#s/../x")).isEqualTo("http://a/b/c/g#s/../x");

    assertThat(resolve(base, "http:g")).isEqualTo("http:g");
  }

  /** Tests additional abnormal usage of {@link UriUtil#resolve(String, String)}. */
  @Test
  public void resolveAbnormalAdditional() {
    assertThat(resolve("http://a/b", "c:d/../e")).isEqualTo("c:e");
    assertThat(resolve("a:b", "../c")).isEqualTo("a:c");
  }

  @Test
  public void removeOnlyQueryParameter() {
    Uri uri = Uri.parse("http://uri?query=value");
    assertThat(removeQueryParameter(uri, "query").toString()).isEqualTo("http://uri");
  }

  @Test
  public void removeFirstQueryParameter() {
    Uri uri = Uri.parse("http://uri?query=value&second=value2");
    assertThat(removeQueryParameter(uri, "query").toString()).isEqualTo("http://uri?second=value2");
  }

  @Test
  public void removeMiddleQueryParameter() {
    Uri uri = Uri.parse("http://uri?first=value1&query=value&last=value2");
    assertThat(removeQueryParameter(uri, "query").toString())
        .isEqualTo("http://uri?first=value1&last=value2");
  }

  @Test
  public void removeLastQueryParameter() {
    Uri uri = Uri.parse("http://uri?first=value1&query=value");
    assertThat(removeQueryParameter(uri, "query").toString()).isEqualTo("http://uri?first=value1");
  }

  @Test
  public void removeNonExistentQueryParameter() {
    Uri uri = Uri.parse("http://uri");
    assertThat(removeQueryParameter(uri, "foo").toString()).isEqualTo("http://uri");
    uri = Uri.parse("http://uri?query=value");
    assertThat(removeQueryParameter(uri, "foo").toString()).isEqualTo("http://uri?query=value");
  }

  @Test
  public void isAbsolute_absoluteUri_returnsTrue() {
    assertThat(UriUtil.isAbsolute("fo://bar")).isTrue();
  }

  @Test
  public void isAbsolute_emptyString_returnsFalse() {
    assertThat(UriUtil.isAbsolute("")).isFalse();
    assertThat(UriUtil.isAbsolute("      ")).isFalse();
    assertThat(UriUtil.isAbsolute(null)).isFalse();
  }

  @Test
  public void isAbsolute_relativeUri_returnsFalse() {
    assertThat(UriUtil.isAbsolute("//www.google.com")).isFalse();
    assertThat(UriUtil.isAbsolute("//www.google.com:80")).isFalse();
    assertThat(UriUtil.isAbsolute("/path/to/file")).isFalse();
    assertThat(UriUtil.isAbsolute("path/to/file")).isFalse();
  }

  @Test
  public void getRelativePath_withDifferentSchemes_shouldReturnTargetUri() {
    Uri baseUri = Uri.parse("http://uri");
    Uri targetUri = Uri.parse("https://uri");

    assertThat(UriUtil.getRelativePath(baseUri, targetUri)).isEqualTo(targetUri.toString());
  }

  @Test
  public void getRelativePath_withDifferentAuthorities_shouldReturnTargetUri() {
    Uri baseUri = Uri.parse("http://baseUri");
    Uri targetUri = Uri.parse("http://targetUri");

    assertThat(UriUtil.getRelativePath(baseUri, targetUri)).isEqualTo(targetUri.toString());
  }

  @Test
  public void getRelativePath_withoutSchemesAndDifferentAuthorities_shouldReturnTargetUri() {
    Uri baseUri = Uri.parse("//baseUri/a");
    Uri targetUri = Uri.parse("//targetUri/b");

    assertThat(UriUtil.getRelativePath(baseUri, targetUri)).isEqualTo(targetUri.toString());
  }

  @Test
  public void getRelativePath_withoutSchemesAndSameAuthority_shouldReturnCorrectRelativePath() {
    Uri baseUri = Uri.parse("//uri/a");
    Uri targetUri = Uri.parse("//uri/b");

    assertThat(UriUtil.getRelativePath(baseUri, targetUri)).isEqualTo("../b");
  }

  @Test
  public void
      getRelativePath_withoutSchemesAndWithoutAuthorities_shouldReturnCorrectRelativePath() {
    Uri baseUri = Uri.parse("a/b/c");
    Uri targetUri = Uri.parse("d/e/f");

    assertThat(UriUtil.getRelativePath(baseUri, targetUri)).isEqualTo("../../../d/e/f");
  }

  @Test
  public void getRelativePath_withEqualPathSegmentsLength_shouldReturnCorrectRelativePath() {
    Uri baseUri = Uri.parse("http://uri/a/b/c");
    Uri targetUri = Uri.parse("http://uri/d/e/f");

    assertThat(UriUtil.getRelativePath(baseUri, targetUri)).isEqualTo("../../../d/e/f");
  }

  @Test
  public void getRelativePath_withUnEqualPathSegmentsLength_shouldReturnCorrectRelativePath() {
    Uri baseUri = Uri.parse("http://uri/a/b/c");
    Uri targetUri = Uri.parse("http://uri/a/b/d/e/f");

    assertThat(UriUtil.getRelativePath(baseUri, targetUri)).isEqualTo("../d/e/f");
  }

  @Test
  public void getRelativePath_withEqualUris_shouldReturnEmptyString() {
    Uri baseUri = Uri.parse("http://uri/a/b/c");
    Uri targetUri = Uri.parse("http://uri/a/b/c");

    assertThat(UriUtil.getRelativePath(baseUri, targetUri)).isEmpty();
  }

  @Test
  public void getRelativePath_nonHierarchicalUris_shouldReturnCorrectRelativePath() {
    Uri baseUri = Uri.parse("schema:a@b");
    Uri targetUri = Uri.parse("schema:a@c");

    assertThat(UriUtil.getRelativePath(baseUri, targetUri)).isEqualTo(targetUri.toString());
  }
}
