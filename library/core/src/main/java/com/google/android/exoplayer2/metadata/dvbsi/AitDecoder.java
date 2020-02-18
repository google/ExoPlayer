package com.google.android.exoplayer2.metadata.dvbsi;

import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataDecoder;
import com.google.android.exoplayer2.metadata.MetadataInputBuffer;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

// Unless mentioned explicitly, every references here are to
// https://www.etsi.org/deliver/etsi_ts/102800_102899/102809/01.01.01_60/ts_102809v010101p.pdf
public class AitDecoder implements MetadataDecoder {
    // Specification of AIT can be found in 5.3.4 of TS 102 809 v1.1.1

    private final static int DESCRIPTOR_TRANSPORT_PROTOCOL = 0x02;

    private final static int DESCRIPTOR_SIMPLE_APPLICATION_LOCATION = 0x15;

    private final static int TRANSPORT_PROTOCOL_HTTP = 3;

    private TimestampAdjuster timestampAdjuster;

    private final ParsableBitArray sectionData;

    public AitDecoder() {
        sectionData = new ParsableBitArray();
    }

    @Override
    public Metadata decode(MetadataInputBuffer inputBuffer) {
        if (timestampAdjuster == null
                || inputBuffer.subsampleOffsetUs != timestampAdjuster.getTimestampOffsetUs()) {
            timestampAdjuster = new TimestampAdjuster(inputBuffer.timeUs);
            timestampAdjuster.adjustSampleTimestamp(inputBuffer.timeUs - inputBuffer.subsampleOffsetUs);
        }

        ByteBuffer buffer = inputBuffer.data;
        byte[] data = buffer.array();
        int size = buffer.limit();
        sectionData.reset(data, size);

        int tableId = sectionData.data[0];
        //Only this table is allowed in AIT streams
        if (tableId == 0x74) {
            return parseAit(sectionData);
        }

        return new Metadata();
    }

    private Metadata parseAit(ParsableBitArray sectionData) {
        //tableId
        sectionData.skipBits(8);

        //section_syntax_indication | reserved_future_use | reserved
        sectionData.skipBits(4);
        int sectionLength = sectionData.readBits(12);
        int endOfSection = sectionData.getBytePosition() + sectionLength - 4 /* Ignore leading CRC */;

        // test_application_flag | application_type
        sectionData.skipBits(16);

        // reserved | version_number | current_next_indicator
        sectionData.skipBits(8);

        // section_number
        sectionData.skipBits(8);
        // last_section_number
        sectionData.skipBits(8);

        // reserved_future_use
        sectionData.skipBits(4);
        int commonDescriptorsLength = sectionData.readBits(12);

        //Since we currently only keep url and control code, which are unique per application,
        //there is no useful information in common descriptor.
        sectionData.skipBytes(commonDescriptorsLength);

        // reserved_future_use | application_loop_length
        sectionData.skipBits(16);

        ArrayList<Ait> aits = new ArrayList<>();
        while(sectionData.getBytePosition() < endOfSection) {
            // Values that will be stored in Ait()
            String aitUrlBase = null;
            String aitUrlExtension = null;
            int aitControlCode = -1;

            // application_identifier
            sectionData.skipBits(48);
            int controlCode = sectionData.readBits(8);

            aitControlCode = controlCode;

            // reserved_future_use
            sectionData.skipBits(4);

            int applicationDescriptorsLoopLength = sectionData.readBits(12);
            int positionOfNextSection = sectionData.getBytePosition() + applicationDescriptorsLoopLength;
            while(sectionData.getBytePosition() < positionOfNextSection) {
                int type = sectionData.readBits(8);
                int l = sectionData.readBits(8);
                int positionOfNextSection2 = sectionData.getBytePosition() + l;

                if(type == DESCRIPTOR_TRANSPORT_PROTOCOL) {
                    // See section 5.3.6
                    int protocolId = sectionData.readBits(16);
                    // label
                    sectionData.skipBits(8);

                    if(protocolId == TRANSPORT_PROTOCOL_HTTP) {
                        while (sectionData.getBytePosition() < positionOfNextSection2) {
                            int urlBaseLength = sectionData.readBits(8);
                            String urlBase = sectionData.readString(urlBaseLength, Charset.forName("ASCII"));

                            int extensionCount = sectionData.readBits(8);
                            aitUrlBase = urlBase;
                            for (int i = 0; i < extensionCount; i++) {
                                int len = sectionData.readBits(8);
                                sectionData.skipBytes(len);
                            }
                        }
                    }
                } else if(type == DESCRIPTOR_SIMPLE_APPLICATION_LOCATION) {
                    String url = sectionData.readString(l, Charset.forName("ASCII"));
                    aitUrlExtension = url;
                }

                sectionData.setPosition(positionOfNextSection2*8);
            }

            sectionData.setPosition(positionOfNextSection*8);

            if(aitControlCode != -1 && aitUrlBase != null && aitUrlExtension != null) {
                aits.add(new Ait(aitControlCode, aitUrlBase + aitUrlExtension));
            }
        }

        return new Metadata(aits);
    }

}
