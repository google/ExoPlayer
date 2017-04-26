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
package com.google.android.exoplayer2.util;

import junit.framework.TestCase;

/**
 * Unit tests for {@link UriUtil}.
 */
public class UriUtilTest extends TestCase {

  /**
   * Tests normal usage of {@link UriUtil#resolve(String, String)}.
   * <p>
   * The test cases are taken from RFC-3986 5.4.1.
   */
  public void testResolveNormal() {
    String base = "http://a/b/c/d;p?q";

    assertEquals("g:h", UriUtil.resolve(base, "g:h"));
    assertEquals("http://a/b/c/g", UriUtil.resolve(base, "g"));
    assertEquals("http://a/b/c/g/", UriUtil.resolve(base, "g/"));
    assertEquals("http://a/g", UriUtil.resolve(base, "/g"));
    assertEquals("http://g", UriUtil.resolve(base, "//g"));
    assertEquals("http://a/b/c/d;p?y", UriUtil.resolve(base, "?y"));
    assertEquals("http://a/b/c/g?y", UriUtil.resolve(base, "g?y"));
    assertEquals("http://a/b/c/d;p?q#s", UriUtil.resolve(base, "#s"));
    assertEquals("http://a/b/c/g#s", UriUtil.resolve(base, "g#s"));
    assertEquals("http://a/b/c/g?y#s", UriUtil.resolve(base, "g?y#s"));
    assertEquals("http://a/b/c/;x", UriUtil.resolve(base, ";x"));
    assertEquals("http://a/b/c/g;x", UriUtil.resolve(base, "g;x"));
    assertEquals("http://a/b/c/g;x?y#s", UriUtil.resolve(base, "g;x?y#s"));
    assertEquals("http://a/b/c/d;p?q", UriUtil.resolve(base, ""));
    assertEquals("http://a/b/c/", UriUtil.resolve(base, "."));
    assertEquals("http://a/b/c/", UriUtil.resolve(base, "./"));
    assertEquals("http://a/b/", UriUtil.resolve(base, ".."));
    assertEquals("http://a/b/", UriUtil.resolve(base, "../"));
    assertEquals("http://a/b/g", UriUtil.resolve(base, "../g"));
    assertEquals("http://a/", UriUtil.resolve(base, "../.."));
    assertEquals("http://a/", UriUtil.resolve(base, "../../"));
    assertEquals("http://a/g", UriUtil.resolve(base, "../../g"));
  }

  /**
   * Tests abnormal usage of {@link UriUtil#resolve(String, String)}.
   * <p>
   * The test cases are taken from RFC-3986 5.4.2.
   */
  public void testResolveAbnormal() {
    String base = "http://a/b/c/d;p?q";

    assertEquals("http://a/g", UriUtil.resolve(base, "../../../g"));
    assertEquals("http://a/g", UriUtil.resolve(base, "../../../../g"));

    assertEquals("http://a/g", UriUtil.resolve(base, "/./g"));
    assertEquals("http://a/g", UriUtil.resolve(base, "/../g"));
    assertEquals("http://a/b/c/g.", UriUtil.resolve(base, "g."));
    assertEquals("http://a/b/c/.g", UriUtil.resolve(base, ".g"));
    assertEquals("http://a/b/c/g..", UriUtil.resolve(base, "g.."));
    assertEquals("http://a/b/c/..g", UriUtil.resolve(base, "..g"));

    assertEquals("http://a/b/g", UriUtil.resolve(base, "./../g"));
    assertEquals("http://a/b/c/g/", UriUtil.resolve(base, "./g/."));
    assertEquals("http://a/b/c/g/h", UriUtil.resolve(base, "g/./h"));
    assertEquals("http://a/b/c/h", UriUtil.resolve(base, "g/../h"));
    assertEquals("http://a/b/c/g;x=1/y", UriUtil.resolve(base, "g;x=1/./y"));
    assertEquals("http://a/b/c/y", UriUtil.resolve(base, "g;x=1/../y"));

    assertEquals("http://a/b/c/g?y/./x", UriUtil.resolve(base, "g?y/./x"));
    assertEquals("http://a/b/c/g?y/../x", UriUtil.resolve(base, "g?y/../x"));
    assertEquals("http://a/b/c/g#s/./x", UriUtil.resolve(base, "g#s/./x"));
    assertEquals("http://a/b/c/g#s/../x", UriUtil.resolve(base, "g#s/../x"));

    assertEquals("http:g", UriUtil.resolve(base, "http:g"));
  }

  /**
   * Tests additional abnormal usage of {@link UriUtil#resolve(String, String)}.
   */
  public void testResolveAbnormalAdditional() {
    assertEquals("c:e", UriUtil.resolve("http://a/b", "c:d/../e"));
    assertEquals("a:c", UriUtil.resolve("a:b", "../c"));
  }

}
