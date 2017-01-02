package com.google.android.exoplayer2.text.ssa;

import android.test.InstrumentationTestCase;

import com.google.android.exoplayer2.testutil.TestUtil;

import java.io.IOException;
import java.text.SimpleDateFormat;

/**
 * Created by cablej01 on 26/12/2016.
 */

public class SSATests extends InstrumentationTestCase {
    private static final String TYPICAL_FILE = "ssa/typical";

    public void testTimeCodeConvert() throws IOException {
        assertEquals("0:00:04.230", SSADecoder.formatTimeCode(SSADecoder.parseTimecode("0:00:04.23")));
    }

    public void testDecodeTypical() throws IOException {
        SSADecoder decoder = new SSADecoder();
        byte[] bytes = TestUtil.getByteArray(getInstrumentation(), TYPICAL_FILE);
        SSASubtitle subtitle = decoder.decodeFile(bytes, bytes.length);
        int n = subtitle.getEventTimeCount();
        assertEquals(924, n); // includes end events
        assertTypicalCue1(subtitle, 0);
        assertTypicalCue2(subtitle, 2);
        assertTypicalCue3(subtitle, 4);
        assertTypicalCue4(subtitle, 6);
    }

    /*
    Dialogue: 0,0:00:04.23,0:00:06.90,Watamote-internal/narrator,Serinuma,0000,0000,0000,,The prince should be with the princess.
    Dialogue: 0,0:00:09.61,0:00:13.20,Watamote-internal/narrator,Serinuma,0000,0000,0000,,Who was the one who decided that?
    Dialogue: 0,0:00:33.01,0:00:41.77,Watamote-Title,,0000,0000,0000,,{\fad(3562,1)}Kiss Him, Not Me
    Dialogue: 0,0:01:48.87,0:01:54.38,Watamote-Ep_Title,,0000,0000,0000,,Can She Do It? A Real Life Oto
    */

    private static void assertTypicalCue1(SSASubtitle subtitle, int eventIndex) {
        assertEquals("0:00:04.230", SSADecoder.formatTimeCode(subtitle.getEventTime(eventIndex)));
        assertEquals("The prince should be with the princess.",
                subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString());
        assertEquals("0:00:06.900", SSADecoder.formatTimeCode(subtitle.getEventTime(eventIndex+1)));
    }

    private static void assertTypicalCue2(SSASubtitle subtitle, int eventIndex) {
        assertEquals("0:00:09.610", SSADecoder.formatTimeCode(subtitle.getEventTime(eventIndex)));
        assertEquals("Who was the one who decided that?",
                subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString());
        assertEquals("0:00:13.200", SSADecoder.formatTimeCode(subtitle.getEventTime(eventIndex+1)));
    }

    private static void assertTypicalCue3(SSASubtitle subtitle, int eventIndex) {
        assertEquals("0:00:33.010", SSADecoder.formatTimeCode(subtitle.getEventTime(eventIndex)));
        assertEquals("Kiss Him, Not Me",
                subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString());
        assertEquals("0:00:41.770", SSADecoder.formatTimeCode(subtitle.getEventTime(eventIndex+1)));
    }

    private static void assertTypicalCue4(SSASubtitle subtitle, int eventIndex) {
        String s1 = SSADecoder.formatTimeCode(subtitle.getEventTime(eventIndex));
        String s2 = SSADecoder.formatTimeCode(subtitle.getEventTime(eventIndex+1));
        String s3 =
                subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString();
        assertEquals("0:01:48.870", SSADecoder.formatTimeCode(subtitle.getEventTime(eventIndex)));
        assertEquals("Can She Do It? A Real Life Otome Game",
                subtitle.getCues(subtitle.getEventTime(eventIndex)).get(0).text.toString());
        assertEquals("0:01:54.380", SSADecoder.formatTimeCode(subtitle.getEventTime(eventIndex+1)));
    }

}
