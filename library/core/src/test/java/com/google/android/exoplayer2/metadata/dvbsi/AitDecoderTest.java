package com.google.android.exoplayer2.metadata.dvbsi;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataInputBuffer;
import java.nio.ByteBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class AitDecoderTest {
  // Test samples have been generated with tsduck:
  // $ tstabcomp -c table.xml
  // $ od -v -An -tx1 table.bin  |sed -E 's/([0-9a-f]{2})/\(byte\)0x\1,/g'
  private AitDecoder decoder;
  private MetadataInputBuffer inputBuffer;

  @Before
  public void setUp() {
    decoder = new AitDecoder();
    inputBuffer = new MetadataInputBuffer();
  }

  @Test
  public void testSimple() {
    /*
<?xml version="1.0" encoding="UTF-8"?>
<tsduck>
  <AIT version="30" current="true" test_application_flag="false" application_type="0x0010">
    <application control_code="0x01">
      <application_identifier organization_id="0x00000120" application_id="0x0071"/>
      <transport_protocol_descriptor transport_protocol_label="0x00">
        <http>
          <url base="http://v/"/>
        </http>
      </transport_protocol_descriptor>
      <simple_application_location_descriptor initial_path="a"/>
    </application>
  </AIT>
</tsduck>
     */
    byte[] data = new byte[]{
        (byte)0x74, (byte)0xf0, (byte)0x29, (byte)0x00, (byte)0x10, (byte)0xfd, (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0x00, (byte)0xf0, (byte)0x1c, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x20,
        (byte)0x00, (byte)0x71, (byte)0x01, (byte)0xf0, (byte)0x13, (byte)0x02, (byte)0x0e, (byte)0x00, (byte)0x03, (byte)0x00, (byte)0x09, (byte)0x68, (byte)0x74, (byte)0x74, (byte)0x70, (byte)0x3a,
        (byte)0x2f, (byte)0x2f, (byte)0x76, (byte)0x2f, (byte)0x00, (byte)0x15, (byte)0x01, (byte)0x61, (byte)0x84, (byte)0x54, (byte)0x57, (byte)0xd9
    };

    Metadata metadata = feedInputBuffer(data, 0, 0L);
    assertThat(metadata.length()).isEqualTo(1);
    assertThat(((Ait) metadata.get(0)).controlCode).isEqualTo(Ait.CONTROL_CODE_AUTOSTART);
    assertThat(((Ait) metadata.get(0)).url).isEqualTo("http://v/a");
  }

  @Test
  public void testArte() {
    /*
<?xml version="1.0" encoding="UTF-8"?>
<tsduck>
  <AIT version="30" current="true" test_application_flag="false" application_type="0x0010">
    <application control_code="0x01">
      <application_identifier organization_id="0x00000120" application_id="0x0071"/>
      <transport_protocol_descriptor transport_protocol_label="0x00">
        <http>
          <url base="http://static-cdn.arte.tv/redbutton/"/>
        </http>
      </transport_protocol_descriptor>
      <transport_protocol_descriptor transport_protocol_label="0x01">
        <object_carousel component_tag="0xF1"/>
      </transport_protocol_descriptor>
      <application_name_descriptor>
        <language code="fr" application_name="Launcher"/>
      </application_name_descriptor>
      <application_usage_descriptor usage_type="0x00"/>
      <application_descriptor service_bound="true" visibility="3" application_priority="2">
        <profile application_profile="0x0000" version="1.1.1"/>
        <transport_protocol label="0x00"/>
        <transport_protocol label="0x01"/>
      </application_descriptor>
      <simple_application_location_descriptor initial_path="index_fr.html"/>
      <simple_application_boundary_descriptor>
        <prefix boundary_extension="http://logi104.xiti.com"/>
      </simple_application_boundary_descriptor>
      <simple_application_boundary_descriptor>
        <prefix boundary_extension="http://www.arte.tv"/>
      </simple_application_boundary_descriptor>
    </application>
    <application control_code="0x02">
      <application_identifier organization_id="0x00000120" application_id="0x0072"/>
      <transport_protocol_descriptor transport_protocol_label="0x02">
        <http>
          <url base="http://www.arte.tv/hbbtvv2/"/>
        </http>
      </transport_protocol_descriptor>
      <application_name_descriptor>
        <language code="fr" application_name="arte7"/>
      </application_name_descriptor>
      <application_descriptor service_bound="true" visibility="3" application_priority="1">
        <profile application_profile="0x0000" version="1.1.1"/>
        <transport_protocol label="0x02"/>
      </application_descriptor>
      <simple_application_location_descriptor initial_path="index.html?lang=fr_FR&amp;page=PLUS7"/>
      <simple_application_boundary_descriptor>
        <prefix boundary_extension="http://logc136.xiti.com"/>
      </simple_application_boundary_descriptor>
      <simple_application_boundary_descriptor>
        <prefix boundary_extension="http://st.arte.tv/"/>
      </simple_application_boundary_descriptor>
      <simple_application_boundary_descriptor>
        <prefix boundary_extension="http://arteptweb.gl-systemhaus.de"/>
      </simple_application_boundary_descriptor>
      <simple_application_boundary_descriptor>
        <prefix boundary_extension="http://static-cdn.arte.tv"/>
      </simple_application_boundary_descriptor>
      <simple_application_boundary_descriptor>
        <prefix boundary_extension="http://download.www.arte.tv"/>
      </simple_application_boundary_descriptor>
      <simple_application_boundary_descriptor>
        <prefix boundary_extension="http://mesure.streaming.estat.com"/>
      </simple_application_boundary_descriptor>
      <simple_application_boundary_descriptor>
        <prefix boundary_extension="http://geoloc.arte.tv"/>
      </simple_application_boundary_descriptor>
    </application>
  </AIT>
</tsduck>
     */
    byte[] data = new byte[]{
        (byte)0x74, (byte)0xf1, (byte)0xd8, (byte)0x00, (byte)0x10, (byte)0xfd, (byte)0x00, (byte)0x00, (byte)0xf0, (byte)0x00, (byte)0xf1, (byte)0xcb, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x20,
        (byte)0x00, (byte)0x71, (byte)0x01, (byte)0xf0, (byte)0x8f, (byte)0x02, (byte)0x29, (byte)0x00, (byte)0x03, (byte)0x00, (byte)0x24, (byte)0x68, (byte)0x74, (byte)0x74, (byte)0x70, (byte)0x3a,
        (byte)0x2f, (byte)0x2f, (byte)0x73, (byte)0x74, (byte)0x61, (byte)0x74, (byte)0x69, (byte)0x63, (byte)0x2d, (byte)0x63, (byte)0x64, (byte)0x6e, (byte)0x2e, (byte)0x61, (byte)0x72, (byte)0x74,
        (byte)0x65, (byte)0x2e, (byte)0x74, (byte)0x76, (byte)0x2f, (byte)0x72, (byte)0x65, (byte)0x64, (byte)0x62, (byte)0x75, (byte)0x74, (byte)0x74, (byte)0x6f, (byte)0x6e, (byte)0x2f, (byte)0x00,
        (byte)0x02, (byte)0x05, (byte)0x00, (byte)0x01, (byte)0x01, (byte)0x7f, (byte)0xf1, (byte)0x01, (byte)0x0c, (byte)0x66, (byte)0x72, (byte)0x65, (byte)0x08, (byte)0x4c, (byte)0x61, (byte)0x75,
        (byte)0x6e, (byte)0x63, (byte)0x68, (byte)0x65, (byte)0x72, (byte)0x16, (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x0a, (byte)0x05, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x01, (byte)0x01,
        (byte)0xff, (byte)0x02, (byte)0x00, (byte)0x01, (byte)0x15, (byte)0x0d, (byte)0x69, (byte)0x6e, (byte)0x64, (byte)0x65, (byte)0x78, (byte)0x5f, (byte)0x66, (byte)0x72, (byte)0x2e, (byte)0x68,
        (byte)0x74, (byte)0x6d, (byte)0x6c, (byte)0x17, (byte)0x19, (byte)0x01, (byte)0x17, (byte)0x68, (byte)0x74, (byte)0x74, (byte)0x70, (byte)0x3a, (byte)0x2f, (byte)0x2f, (byte)0x6c, (byte)0x6f,
        (byte)0x67, (byte)0x69, (byte)0x31, (byte)0x30, (byte)0x34, (byte)0x2e, (byte)0x78, (byte)0x69, (byte)0x74, (byte)0x69, (byte)0x2e, (byte)0x63, (byte)0x6f, (byte)0x6d, (byte)0x17, (byte)0x14,
        (byte)0x01, (byte)0x12, (byte)0x68, (byte)0x74, (byte)0x74, (byte)0x70, (byte)0x3a, (byte)0x2f, (byte)0x2f, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x2e, (byte)0x61, (byte)0x72, (byte)0x74,
        (byte)0x65, (byte)0x2e, (byte)0x74, (byte)0x76, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x20, (byte)0x00, (byte)0x72, (byte)0x02, (byte)0xf1, (byte)0x2a, (byte)0x02, (byte)0x20, (byte)0x00,
        (byte)0x03, (byte)0x02, (byte)0x1b, (byte)0x68, (byte)0x74, (byte)0x74, (byte)0x70, (byte)0x3a, (byte)0x2f, (byte)0x2f, (byte)0x77, (byte)0x77, (byte)0x77, (byte)0x2e, (byte)0x61, (byte)0x72,
        (byte)0x74, (byte)0x65, (byte)0x2e, (byte)0x74, (byte)0x76, (byte)0x2f, (byte)0x68, (byte)0x62, (byte)0x62, (byte)0x74, (byte)0x76, (byte)0x76, (byte)0x32, (byte)0x2f, (byte)0x00, (byte)0x01,
        (byte)0x09, (byte)0x66, (byte)0x72, (byte)0x65, (byte)0x05, (byte)0x61, (byte)0x72, (byte)0x74, (byte)0x65, (byte)0x37, (byte)0x00, (byte)0x09, (byte)0x05, (byte)0x00, (byte)0x00, (byte)0x01,
        (byte)0x01, (byte)0x01, (byte)0xff, (byte)0x01, (byte)0x02, (byte)0x15, (byte)0x20, (byte)0x69, (byte)0x6e, (byte)0x64, (byte)0x65, (byte)0x78, (byte)0x2e, (byte)0x68, (byte)0x74, (byte)0x6d,
        (byte)0x6c, (byte)0x3f, (byte)0x6c, (byte)0x61, (byte)0x6e, (byte)0x67, (byte)0x3d, (byte)0x66, (byte)0x72, (byte)0x5f, (byte)0x46, (byte)0x52, (byte)0x26, (byte)0x70, (byte)0x61, (byte)0x67,
        (byte)0x65, (byte)0x3d, (byte)0x50, (byte)0x4c, (byte)0x55, (byte)0x53, (byte)0x37, (byte)0x17, (byte)0x19, (byte)0x01, (byte)0x17, (byte)0x68, (byte)0x74, (byte)0x74, (byte)0x70, (byte)0x3a,
        (byte)0x2f, (byte)0x2f, (byte)0x6c, (byte)0x6f, (byte)0x67, (byte)0x63, (byte)0x31, (byte)0x33, (byte)0x36, (byte)0x2e, (byte)0x78, (byte)0x69, (byte)0x74, (byte)0x69, (byte)0x2e, (byte)0x63,
        (byte)0x6f, (byte)0x6d, (byte)0x17, (byte)0x14, (byte)0x01, (byte)0x12, (byte)0x68, (byte)0x74, (byte)0x74, (byte)0x70, (byte)0x3a, (byte)0x2f, (byte)0x2f, (byte)0x73, (byte)0x74, (byte)0x2e,
        (byte)0x61, (byte)0x72, (byte)0x74, (byte)0x65, (byte)0x2e, (byte)0x74, (byte)0x76, (byte)0x2f, (byte)0x17, (byte)0x23, (byte)0x01, (byte)0x21, (byte)0x68, (byte)0x74, (byte)0x74, (byte)0x70,
        (byte)0x3a, (byte)0x2f, (byte)0x2f, (byte)0x61, (byte)0x72, (byte)0x74, (byte)0x65, (byte)0x70, (byte)0x74, (byte)0x77, (byte)0x65, (byte)0x62, (byte)0x2e, (byte)0x67, (byte)0x6c, (byte)0x2d,
        (byte)0x73, (byte)0x79, (byte)0x73, (byte)0x74, (byte)0x65, (byte)0x6d, (byte)0x68, (byte)0x61, (byte)0x75, (byte)0x73, (byte)0x2e, (byte)0x64, (byte)0x65, (byte)0x17, (byte)0x1b, (byte)0x01,
        (byte)0x19, (byte)0x68, (byte)0x74, (byte)0x74, (byte)0x70, (byte)0x3a, (byte)0x2f, (byte)0x2f, (byte)0x73, (byte)0x74, (byte)0x61, (byte)0x74, (byte)0x69, (byte)0x63, (byte)0x2d, (byte)0x63,
        (byte)0x64, (byte)0x6e, (byte)0x2e, (byte)0x61, (byte)0x72, (byte)0x74, (byte)0x65, (byte)0x2e, (byte)0x74, (byte)0x76, (byte)0x17, (byte)0x1d, (byte)0x01, (byte)0x1b, (byte)0x68, (byte)0x74,
        (byte)0x74, (byte)0x70, (byte)0x3a, (byte)0x2f, (byte)0x2f, (byte)0x64, (byte)0x6f, (byte)0x77, (byte)0x6e, (byte)0x6c, (byte)0x6f, (byte)0x61, (byte)0x64, (byte)0x2e, (byte)0x77, (byte)0x77,
        (byte)0x77, (byte)0x2e, (byte)0x61, (byte)0x72, (byte)0x74, (byte)0x65, (byte)0x2e, (byte)0x74, (byte)0x76, (byte)0x17, (byte)0x23, (byte)0x01, (byte)0x21, (byte)0x68, (byte)0x74, (byte)0x74,
        (byte)0x70, (byte)0x3a, (byte)0x2f, (byte)0x2f, (byte)0x6d, (byte)0x65, (byte)0x73, (byte)0x75, (byte)0x72, (byte)0x65, (byte)0x2e, (byte)0x73, (byte)0x74, (byte)0x72, (byte)0x65, (byte)0x61,
        (byte)0x6d, (byte)0x69, (byte)0x6e, (byte)0x67, (byte)0x2e, (byte)0x65, (byte)0x73, (byte)0x74, (byte)0x61, (byte)0x74, (byte)0x2e, (byte)0x63, (byte)0x6f, (byte)0x6d, (byte)0x17, (byte)0x17,
        (byte)0x01, (byte)0x15, (byte)0x68, (byte)0x74, (byte)0x74, (byte)0x70, (byte)0x3a, (byte)0x2f, (byte)0x2f, (byte)0x67, (byte)0x65, (byte)0x6f, (byte)0x6c, (byte)0x6f, (byte)0x63, (byte)0x2e,
        (byte)0x61, (byte)0x72, (byte)0x74, (byte)0x65, (byte)0x2e, (byte)0x74, (byte)0x76, (byte)0x52, (byte)0x24, (byte)0xa0, (byte)0xcc
    };

    Metadata metadata = feedInputBuffer(data, 0, 0L);
    assertThat(metadata.length()).isEqualTo(2);
    assertThat(((Ait) metadata.get(0)).controlCode).isEqualTo(Ait.CONTROL_CODE_AUTOSTART);
    assertThat(((Ait) metadata.get(0)).url).isEqualTo("http://static-cdn.arte.tv/redbutton/index_fr.html");
    assertThat(((Ait) metadata.get(1)).controlCode).isEqualTo(Ait.CONTROL_CODE_PRESENT);
    assertThat(((Ait) metadata.get(1)).url).isEqualTo("http://www.arte.tv/hbbtvv2/index.html?lang=fr_FR&page=PLUS7");
  }

  private Metadata feedInputBuffer(byte[] data, long timeUs, long subsampleOffset) {
    inputBuffer.clear();
    inputBuffer.data = ByteBuffer.allocate(data.length).put(data);
    inputBuffer.timeUs = timeUs;
    inputBuffer.subsampleOffsetUs = subsampleOffset;
    return decoder.decode(inputBuffer);
  }
}