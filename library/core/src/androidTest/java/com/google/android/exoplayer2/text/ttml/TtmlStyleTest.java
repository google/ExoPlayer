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
package com.google.android.exoplayer2.text.ttml;

import android.graphics.Color;
import android.test.InstrumentationTestCase;

/**
 * Unit test for {@link TtmlStyle}.
 */
public final class TtmlStyleTest extends InstrumentationTestCase {

    private static final String FONT_FAMILY = "serif";
    private static final String ID = "id";
    public static final int FOREGROUND_COLOR = Color.WHITE;
    public static final int BACKGROUND_COLOR = Color.BLACK;
    private TtmlStyle style;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        style = new TtmlStyle();
    }

    public void testInheritStyle() {
        style.inherit(createAncestorStyle());
        assertNull("id must not be inherited", style.getId());
        assertTrue(style.isUnderline());
        assertTrue(style.isLinethrough());
        assertEquals(TtmlStyle.STYLE_BOLD_ITALIC, style.getStyle());
        assertEquals(FONT_FAMILY, style.getFontFamily());
        assertEquals(Color.WHITE, style.getFontColor());
        assertFalse("do not inherit backgroundColor", style.hasBackgroundColor());
    }

    public void testChainStyle() {
        style.chain(createAncestorStyle());
        assertNull("id must not be inherited", style.getId());
        assertTrue(style.isUnderline());
        assertTrue(style.isLinethrough());
        assertEquals(TtmlStyle.STYLE_BOLD_ITALIC, style.getStyle());
        assertEquals(FONT_FAMILY, style.getFontFamily());
        assertEquals(FOREGROUND_COLOR, style.getFontColor());
        // do inherit backgroundColor when chaining
        assertEquals("do not inherit backgroundColor when chaining",
            BACKGROUND_COLOR, style.getBackgroundColor());
    }

    private TtmlStyle createAncestorStyle() {
        TtmlStyle ancestor = new TtmlStyle();
        ancestor.setId(ID);
        ancestor.setItalic(true);
        ancestor.setBold(true);
        ancestor.setBackgroundColor(BACKGROUND_COLOR);
        ancestor.setFontColor(FOREGROUND_COLOR);
        ancestor.setLinethrough(true);
        ancestor.setUnderline(true);
        ancestor.setFontFamily(FONT_FAMILY);
        return ancestor;
    }

    public void testStyle() {
        assertEquals(TtmlStyle.UNSPECIFIED, style.getStyle());
        style.setItalic(true);
        assertEquals(TtmlStyle.STYLE_ITALIC, style.getStyle());
        style.setBold(true);
        assertEquals(TtmlStyle.STYLE_BOLD_ITALIC, style.getStyle());
        style.setItalic(false);
        assertEquals(TtmlStyle.STYLE_BOLD, style.getStyle());
        style.setBold(false);
        assertEquals(TtmlStyle.STYLE_NORMAL, style.getStyle());
    }

    public void testLinethrough() {
        assertFalse(style.isLinethrough());
        style.setLinethrough(true);
        assertTrue(style.isLinethrough());
        style.setLinethrough(false);
        assertFalse(style.isLinethrough());
    }

    public void testUnderline() {
        assertFalse(style.isUnderline());
        style.setUnderline(true);
        assertTrue(style.isUnderline());
        style.setUnderline(false);
        assertFalse(style.isUnderline());
    }

    public void testFontFamily() {
        assertNull(style.getFontFamily());
        style.setFontFamily(FONT_FAMILY);
        assertEquals(FONT_FAMILY, style.getFontFamily());
        style.setFontFamily(null);
        assertNull(style.getFontFamily());
    }

    public void testColor() {
        assertFalse(style.hasFontColor());
        style.setFontColor(Color.BLACK);
        assertEquals(Color.BLACK, style.getFontColor());
        assertTrue(style.hasFontColor());
    }

    public void testBackgroundColor() {
        assertFalse(style.hasBackgroundColor());
        style.setBackgroundColor(Color.BLACK);
        assertEquals(Color.BLACK, style.getBackgroundColor());
        assertTrue(style.hasBackgroundColor());
    }

    public void testId() {
        assertNull(style.getId());
        style.setId(ID);
        assertEquals(ID, style.getId());
        style.setId(null);
        assertNull(style.getId());
    }
}
