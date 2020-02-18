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
// Specification of AIT can be found in 5.3.4 of TS 102 809 v1.1.1
public class AitDecoder implements MetadataDecoder {
    // This value and descriptor is described in section 5.3.6
    private final static int DESCRIPTOR_TRANSPORT_PROTOCOL = 0x02;
    // This value and descriptor is described in section 5.3.7
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
            int positionOfNextApplication = sectionData.getBytePosition() + applicationDescriptorsLoopLength;
            while(sectionData.getBytePosition() < positionOfNextApplication) {
                int descriptorTag = sectionData.readBits(8);
                int descriptorLen = sectionData.readBits(8);
                int positionOfNextDescriptor = sectionData.getBytePosition() + descriptorLen;

                if(descriptorTag == DESCRIPTOR_TRANSPORT_PROTOCOL) {
                    // This descriptor is defined in section 5.3.6
                    int protocolId = sectionData.readBits(16);
                    // label
                    sectionData.skipBits(8);

                    if(protocolId == TRANSPORT_PROTOCOL_HTTP) {
                        // This selector is defined in section 5.3.6.2
                        while (sectionData.getBytePosition() < positionOfNextDescriptor) {
                            int urlBaseLength = sectionData.readBits(8);
                            String urlBase = sectionData.readString(urlBaseLength, Charset.forName("ASCII"));

                            int extensionCount = sectionData.readBits(8);
                            aitUrlBase = urlBase;
                            for (int urlExtensionIdx = 0; urlExtensionIdx < extensionCount; urlExtensionIdx++) {
                                int urlExtensionLength = sectionData.readBits(8);
                                sectionData.skipBytes(urlExtensionLength);
                            }
                        }
                    }
                } else if(descriptorTag == DESCRIPTOR_SIMPLE_APPLICATION_LOCATION) {
                    // This descriptor is defined in section 5.3.7
                    String url = sectionData.readString(descriptorLen, Charset.forName("ASCII"));
                    aitUrlExtension = url;
                }

                sectionData.setPosition(positionOfNextDescriptor*8);
            }

            sectionData.setPosition(positionOfNextApplication*8);

            if(aitControlCode != -1 && aitUrlBase != null && aitUrlExtension != null) {
                aits.add(new Ait(aitControlCode, aitUrlBase + aitUrlExtension));
            }
        }

        return new Metadata(aits);
    }

}
