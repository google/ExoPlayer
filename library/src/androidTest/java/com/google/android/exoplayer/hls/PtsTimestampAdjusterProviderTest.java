package com.google.android.exoplayer.hls;

import com.google.android.exoplayer.extractor.ts.PtsTimestampAdjuster;

import junit.framework.TestCase;

public class PtsTimestampAdjusterProviderTest extends TestCase{

    public void testGetAdjuster(){

        PtsTimestampAdjusterProvider provider = new PtsTimestampAdjusterProvider();

        PtsTimestampAdjuster adj1 = provider.getAdjuster(true, 1, 123);
        PtsTimestampAdjuster adj2 = provider.getAdjuster(true, 2, 456);
        PtsTimestampAdjuster adjShouldUseAdj1 = provider.getAdjuster(true, 1, 123);
        PtsTimestampAdjuster adj3 = provider.getAdjuster(true, 3, 789);
        PtsTimestampAdjuster adjShouldCreateNew = provider.getAdjuster(true, 3, 802);

        assertSame(adj1, adjShouldUseAdj1);
        assertNotSame(adj1, adj2);
        assertNotSame(adj3, adjShouldCreateNew);

    }
}
