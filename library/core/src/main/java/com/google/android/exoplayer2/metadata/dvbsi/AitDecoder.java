package com.google.android.exoplayer2.metadata.dvbsi;

import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataDecoder;
import com.google.android.exoplayer2.metadata.MetadataInputBuffer;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class AitDecoder implements MetadataDecoder {
    // Specification of AIT can be found in 5.3.4 of TS 102 809 v1.1.1
    // https://www.etsi.org/deliver/etsi_ts/102800_102899/102809/01.01.01_60/ts_102809v010101p.pdf
    private final static int DESCRIPTOR_TRANSPORT_PROTOCOL = 0x02;

    private final static int DESCRIPTOR_SIMPLE_APPLICATION_LOCATION = 0x15;

    private final static int TRANSPORT_PROTOCOL_HTTP = 3;

    private TimestampAdjuster timestampAdjuster;

    private final ParsableByteArray sectionData;

    public AitDecoder() {
        sectionData = new ParsableByteArray();
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

        int tableId = sectionData.peekUnsignedByte();

        //Only this table is allowed in AIT streams
        if (tableId == 0x74) {
            return parseAit(sectionData);
        }

        return new Metadata();
    }

    private Metadata parseAit(ParsableByteArray sectionData) {
        int tmp;

        int tableId = sectionData.readUnsignedByte();

        tmp = sectionData.readUnsignedShort();
        int endOfSection = sectionData.getPosition() + (tmp & 4095) - 4 /* Ignore leading CRC */;

        tmp = sectionData.readUnsignedShort();
        int applicationType = tmp & 0x7fff;

        tmp = sectionData.readUnsignedByte();
        int versionNumber = (tmp & 0x3e) >> 1;
        boolean current = (tmp & 1) == 1;

        int section_number = sectionData.readUnsignedByte();
        int last_section_number = sectionData.readUnsignedByte();

        tmp = sectionData.readUnsignedShort();
        int commonDescriptorsLength = tmp & 4095;

        //Since we currently only keep url and control code, which are unique per application,
        //there is no useful information in common descriptor.
        sectionData.skipBytes(commonDescriptorsLength);

        tmp = sectionData.readUnsignedShort();
        int appLoopLength = tmp & 4095;

        ArrayList<Ait> aits = new ArrayList<>();
        while(sectionData.getPosition() < endOfSection) {
            // Values that will be stored in Ait()
            String aitUrlBase = null;
            String aitUrlExtension = null;
            int aitControlCode = -1;

            long application_identifier = sectionData.readUnsignedInt24() << 24L;
            application_identifier |= sectionData.readUnsignedInt24();
            int controlCode = sectionData.readUnsignedByte();

            aitControlCode = controlCode;

            tmp = sectionData.readUnsignedShort();
            int sectionLength = tmp & 4095;
            int positionOfNextSection = sectionData.getPosition() + sectionLength;
            while(sectionData.getPosition() < positionOfNextSection) {
                int type = sectionData.readUnsignedByte();
                int l = sectionData.readUnsignedByte();
                int positionOfNextSection2 = sectionData.getPosition() + l;

                if(type == DESCRIPTOR_TRANSPORT_PROTOCOL) {
                    int protocolId = sectionData.readUnsignedShort();
                    int label = sectionData.readUnsignedByte();

                    if(protocolId == TRANSPORT_PROTOCOL_HTTP) {
                        while (sectionData.getPosition() < positionOfNextSection2) {
                            int urlBaseLength = sectionData.readUnsignedByte();
                            String urlBase = sectionData.readString(urlBaseLength);
                            int extensionCount = sectionData.readUnsignedByte();
                            aitUrlBase = urlBase;
                            for (int i = 0; i < extensionCount; i++) {
                                int len = sectionData.readUnsignedByte();
                                sectionData.skipBytes(len);
                            }
                        }
                    }
                } else if(type == DESCRIPTOR_SIMPLE_APPLICATION_LOCATION) {
                    String url = sectionData.readString(l);
                    aitUrlExtension = url;
                }

                sectionData.setPosition(positionOfNextSection2);
            }

            sectionData.setPosition(positionOfNextSection);

            if(aitControlCode != -1 && aitUrlBase != null && aitUrlExtension != null) {
                aits.add(new Ait(aitControlCode, aitUrlBase + aitUrlExtension));
            }
        }

        return new Metadata(aits);
    }

}
