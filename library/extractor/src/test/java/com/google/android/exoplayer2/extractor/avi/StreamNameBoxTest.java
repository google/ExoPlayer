package com.google.android.exoplayer2.extractor.avi;

import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.Assert;
import org.junit.Test;

public class StreamNameBoxTest {
  @Test
  public void createStreamName_givenList() throws IOException {
    final String name = "Test";
    final ListBuilder listBuilder = new ListBuilder(ListBox.TYPE_STRL);
    listBuilder.addBox(DataHelper.getStreamNameBox(name));
    final ByteBuffer listBuffer = listBuilder.build();
    final FakeExtractorInput fakeExtractorInput = new FakeExtractorInput.Builder().setData(listBuffer.array()).build();
    fakeExtractorInput.skipFully(8);
    ListBox listBox = ListBox.newInstance(listBuffer.capacity() - 8, new BoxFactory(), fakeExtractorInput);
    Assert.assertEquals(1, listBox.getChildren().size());
    final StreamNameBox streamNameBox = (StreamNameBox) listBox.getChildren().get(0);
    //Test + nullT = 5 bytes, so verify that the input is properly aligned
    Assert.assertEquals(0, fakeExtractorInput.getPosition() & 1);
    Assert.assertEquals(name, streamNameBox.getName());
  }
}
