/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.text.dvbsubs;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.DashPathEffect;
import android.graphics.PorterDuffXfermode;
import android.graphics.Region;
import android.support.annotation.IntDef;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.exoplayer2.core.BuildConfig;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.util.ParsableBitArray;

import java.util.ArrayList;
import java.util.List;


public class DvbSubtitlesParser {

    private static final String TAG = "DVBSubs";

    @IntDef(flag = true, value = {FLAG_PES_STRIPPED_DVBSUB})
    public @interface Flags {
    }
    public static final int FLAG_PES_STRIPPED_DVBSUB = 1;

    @Flags private final int flags;

    /* List of different SEGMENT TYPES */
    /* According to EN 300-743, table 2 */
    private final static int DVBSUB_ST_PAGE_COMPOSITION   = 0x10;
    private final static int DVBSUB_ST_REGION_COMPOSITION = 0x11;
    private final static int DVBSUB_ST_CLUT_DEFINITION    = 0x12;
    private final static int DVBSUB_ST_OBJECT_DATA        = 0x13;
    private final static int DVBSUB_ST_DISPLAY_DEFINITION = 0x14;
    private final static int DVBSUB_ST_ENDOFDISPLAY       = 0x80;
    private final static int DVBSUB_ST_STUFFING           = 0xff;

    /* List of different Page Composition Segment state */
    /* According to EN 300-743, 7.2.1 table 3 */
    private final static int DVBSUB_PCS_STATE_NORMAL      = 0b00; // Update. Only changed elements.
    private final static int DVBSUB_PCS_STATE_ACQUISITION = 0b01; // Refresh. All subtitle elements.
    private final static int DVBSUB_PCS_STATE_CHANGE      = 0b10; // New. All subtitle elements.

    /* List of different Region Composition Segments CLUT level oc compatibility */
    /* According to EN 300-743, 7.2.1 table 4 */
    private final static int DVBSUB_RCS_CLUT_2            = 0x01;
    private final static int DVBSUB_RCS_CLUT_4            = 0x02;
    private final static int DVBSUB_RCS_CLUT_8            = 0x03;

    /* List of different Region Composition Segments bit depths */
    /* According to EN 300-743, 7.2.1 table 5 */
    private final static int DVBSUB_RCS_BITDEPTH_2        = 0x01;
    private final static int DVBSUB_RCS_BITDEPTH_4        = 0x02;
    private final static int DVBSUB_RCS_BITDEPTH_8        = 0x03;

    /* List of different object types in the Region Composition Segment */
    /* According to EN 300-743, table 6 */
    private final static int DVBSUB_OT_BASIC_BITMAP       = 0x00;
    private final static int DVBSUB_OT_BASIC_CHAR         = 0x01;
    private final static int DVBSUB_OT_COMPOSITE_STRING   = 0x02;

    /* List of different object coding methods in the Object Data Segment */
    /* According to EN 300-743, table 8 */
    private static final int DVBSUB_ODS_PIXEL_CODED       = 0x00;
    private static final int DVBSUB_ODS_CHAR_CODED        = 0x01;

    /* Pixel DATA TYPES */
    /* According to EN 300-743, table 9 */
    private final static int DVBSUB_DT_2BP_CODE_STRING    = 0x10;
    private final static int DVBSUB_DT_4BP_CODE_STRING    = 0x11;
    private final static int DVBSUB_DT_8BP_CODE_STRING    = 0x12;
    private final static int DVBSUB_DT_24_TABLE_DATA      = 0x20;
    private final static int DVBSUB_DT_28_TABLE_DATA      = 0x21;
    private final static int DVBSUB_DT_48_TABLE_DATA      = 0x22;
    private final static int DVBSUB_DT_END_LINE           = 0xf0;

    /* Clut mapping tables */
    /* According to EN 300-743, 10.4 10.5 10.6 */
    private byte[] defaultMap24 = {(byte) 0x00, (byte) 0x07, (byte) 0x08, (byte) 0x0f };
    private byte[] defaultMap28 = {(byte) 0x00, (byte) 0x77, (byte) 0x88, (byte) 0xff };
    private byte[] defaultMap48 = {(byte) 0x00, (byte) 0x11, (byte) 0x22, (byte) 0x33,
            (byte) 0x44, (byte) 0x55, (byte) 0x66, (byte) 0x77,
            (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb,
            (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff};

    private ClutDefinition defaultClut = new ClutDefinition();

    /* FLAGS */
    private final static int DISPLAY_WINDOW_FLAG               = 0x01;

    private final static int REGION_FILL_FLAG                  = 0x01;

    private final static int OBJECT_NON_MODIFYING_COLOUR_FLAG  = 0x01;

    /* Constants */
    private static final int UNDEF_PAGE = -1;

    /* instance variables */
    private Paint defaultPaint = new Paint();
    private Paint fillRegionPaint = new Paint();
    private Paint debugRegionPaint = new Paint();
    private Paint debugObjectPaint = new Paint();
    private Canvas canvas = new Canvas();
    private Bitmap bitmap;

    private static ParsableBitArray tsStream;
    private SubtitleService subtitleService;

    private class SubtitleService {
        int subtitlePage;
        int ancillaryPage;
        boolean newSubtitle = false;

        // subtitle page
        DisplayDefinition displayDefinition;
        PageComposition pageComposition;
        SparseArray<RegionComposition> regions = new SparseArray<>();
        SparseArray<ClutDefinition> cluts = new SparseArray<>();
        SparseArray<ObjectData> objects = new SparseArray<>();

        // ancillary page
        SparseArray<ClutDefinition> ancillaryCluts = new SparseArray<>();
        SparseArray<ObjectData> ancillaryObjects = new SparseArray<>();
    }

    /* The displays dimensions [7.2.1] */
    private class DisplayDefinition {
        int pageId;
        int versionNumber;

        int displayWidth = 719;
        int displayHeight = 575;

        int flags;
        int displayWindowHorizontalPositionMinimum = 0;
        int displayWindowHorizontalPositionMaximum = 719;
        int displayWindowVerticalPositionMinimum = 0;
        int displayWindowVerticalPositionMaximum = 575;

        void updateBitmapResolution() {
            bitmap = Bitmap.createBitmap(this.displayWidth + 1, this.displayHeight + 1,
                    Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bitmap);
        }
    }

    /* The page final static ints the list of regions [7.2.2] */
    private class PageComposition {
        int pageId;
        int pageTimeOut; /* in seconds */
        int pageVersionNumber;
        int pageState;
        SparseArray<PageRegion> pageRegions = new SparseArray<>();
    }

    private class PageRegion {
        int regionId;
        int regionHorizontalAddress;
        int regionVerticalAddress;
    }

    /* The Region is an area on the image [7.2.3]
    * with a list of the object definitions associated and a CLUT */
    private class RegionComposition {
        int pageId;
        int regionId;
        int regionVersionNumber;
        int flags;
        int regionWidth;
        int regionHeight;
        int regionLevelOfCompatibility;
        int regionDepth;
        int clutId;
        int region8bitPixelCode;
        int region4bitPixelCode;
        int region2bitPixelCode;
        SparseArray<RegionObject> regionObjects = new SparseArray<>();
        Cue cue;
    }

    /* The entry in the palette CLUT */
    private class ClutEntry {
        int clutEntryId;
        byte flags;
        byte Y;
        byte Cr;
        byte Cb;
        byte T;

        byte A;
        byte R;
        byte G;
        byte B;
        int ARGB;

        void clutYCbCrT (int Y, int Cb, int Cr, int T) {

            this.Y = (byte) Y;
            this.Cb = (byte) Cb;
            this.Cr = (byte) Cr;
            this.T = (byte) T;

            int R = (int) (Y + 1.40200 * (Cr - 128));
            int G = (int) (Y - 0.34414 * (Cb - 128) - 0.71414 * (Cr - 128));
            int B = (int) (Y + 1.77200 * (Cb - 128));

            if (R > 255)     this.R = (byte) 255;
            else if (R < 0)  this.R = 0;
            else             this.R = (byte) R;

            if (G > 255)     this.G = (byte) 255;
            else if (G < 0)  this.G = 0;
            else             this.G = (byte) G;

            if (B > 255)     this.B = (byte) 255;
            else if (B < 0)  this.B = 0;
            else             this.B = (byte) B;

            this.A = (byte) (0xFF - (this.T & 0xFF));
            this.ARGB =
                    ((this.A & 0xFF) << 24) |
                            ((this.R & 0xFF) << 16) |
                            ((this.G & 0xFF) << 8) |
                            (this.B & 0xFF);

        }

        void clutRGBA (int R, int G, int B, int A) {

            this.A = (byte) A;
            this.R = (byte) R;
            this.G = (byte) G;
            this.B = (byte) B;

            this.ARGB =
                    ((A & 0xFF) << 24) |
                            ((R & 0xFF) << 16) |
                            ((G & 0xFF) << 8) |
                            (B & 0xFF);

            int y =        (int) ( 0.299000 * R +  0.587000  * G +  0.114000 * B);
            int Cb = 128 + (int) (-0.168736 * R + -0.331264  * G +  0.500000 * B);
            int Cr = 128 + (int) ( 0.500000 * R + -0.418688  * G + -0.081312 * B);

            if (y > 255)     this.Y  = (byte) 255;
            else if (y < 0)  this.Y  = 0;
            else             this.Y  = (byte) y;

            if (Cb > 255)    this.Cb = (byte) 255;
            else if (Cb < 0) this.Cb = 0;
            else             this.Cb = (byte) Cb;

            if (Cr > 255)    this.Cr = (byte) 255;
            else if (Cr < 0) this.Cr = 0;
            else             this.Cr = (byte) Cr;

            this.T = (byte) (0xFF - (this.A & 0xFF));
        }
    }

    /* Colours to be applied in a CLUT family [7.2.4] */
    private class ClutDefinition {
        int pageId;
        int clutId;
        int clutVersionNumber;
        ClutEntry[] clutEntries2bit;
        ClutEntry[] clutEntries4bit;
        ClutEntry[] clutEntries8bit;

        ClutEntry[] generateDefault2bitClut() {
            ClutEntry[] entries = new ClutEntry[4];

            entries[0] = new ClutEntry();
            entries[0].clutRGBA(0x00, 0x00, 0x00, 0x00);
            entries[1] = new ClutEntry();
            entries[1].clutRGBA(0xFF, 0xFF, 0xFF, 0xFF);
            entries[2] = new ClutEntry();
            entries[2].clutRGBA(0x00, 0x00, 0x00, 0xFF);
            entries[3] = new ClutEntry();
            entries[3].clutRGBA(0x7F, 0x7F, 0x7F, 0xFF);

            return entries;
        }

        ClutEntry[] generateDefault4bitClut() {
            ClutEntry[] entries = new ClutEntry[16];

            entries[0] = new ClutEntry();
            entries[0].clutRGBA(0x00, 0x00, 0x00, 0x00);

            int i = 15;
            while (i > 0) {
                entries[i] = new ClutEntry();
                if (i < 8) {
                    entries[i].clutRGBA(
                            ((i & 0x01) != 0 ? 0xFF : 0x00),
                            ((i & 0x02) != 0 ? 0xFF : 0x00),
                            ((i & 0x04) != 0 ? 0xFF : 0x00),
                            0xFF);
                } else {
                    entries[i].clutRGBA(
                            ((i & 0x01) != 0 ? 0x7F : 0x00),
                            ((i & 0x02) != 0 ? 0x7F : 0x00),
                            ((i & 0x04) != 0 ? 0x7F : 0x00),
                            0xFF);
                }

                i--;
            }

            return entries;
        }

        ClutEntry[] generateDefault8bitClut() {
            ClutEntry[] entries = new ClutEntry[256];

            entries[0] = new ClutEntry();
            entries[0].clutRGBA(0x00, 0x00, 0x00, 0x00);

            int i = 255;
            while (i > 0) {
                entries[i] = new ClutEntry();
                if (i < 8) {
                    entries[i].clutRGBA(
                            ((i & 0x01) != 0 ? 0xFF : 0x00),
                            ((i & 0x02) != 0 ? 0xFF : 0x00),
                            ((i & 0x04) != 0 ? 0xFF : 0x00),
                            0x3F);
                } else {
                    switch (i & 0x88) {
                        case 0x00:
                            entries[i].clutRGBA(
                                    (((i & 0x01) != 0 ? 0x55 : 0x00) + ((i & 0x10) != 0 ? 0xAA : 0x00)),
                                    (((i & 0x02) != 0 ? 0x55 : 0x00) + ((i & 0x20) != 0 ? 0xAA : 0x00)),
                                    (((i & 0x04) != 0 ? 0x55 : 0x00) + ((i & 0x40) != 0 ? 0xAA : 0x00)),
                                    0xFF);
                            break;
                        case 0x08:
                            entries[i].clutRGBA(
                                    (((i & 0x01) != 0 ? 0x55 : 0x00) + ((i & 0x10) != 0 ? 0xAA : 0x00)),
                                    (((i & 0x02) != 0 ? 0x55 : 0x00) + ((i & 0x20) != 0 ? 0xAA : 0x00)),
                                    (((i & 0x04) != 0 ? 0x55 : 0x00) + ((i & 0x40) != 0 ? 0xAA : 0x00)),
                                    0x7F);
                            break;
                        case 0x80:
                            entries[i].clutRGBA(
                                    (127 + ((i & 0x01) != 0 ? 0x2B : 0x00) + ((i & 0x10) != 0 ? 0x55 : 0x00)),
                                    (127 + ((i & 0x02) != 0 ? 0x2B : 0x00) + ((i & 0x20) != 0 ? 0x55 : 0x00)),
                                    (127 + ((i & 0x04) != 0 ? 0x2B : 0x00) + ((i & 0x40) != 0 ? 0x55 : 0x00)),
                                    0xFF);
                            break;
                        case 0x88:
                            entries[i].clutRGBA(
                                    (((i & 0x01) != 0 ? 0x2B : 0x00) + ((i & 0x10) != 0 ? 0x55 : 0x00)),
                                    (((i & 0x02) != 0 ? 0x2B : 0x00) + ((i & 0x20) != 0 ? 0x55 : 0x00)),
                                    (((i & 0x04) != 0 ? 0x2B : 0x00) + ((i & 0x40) != 0 ? 0x55 : 0x00)),
                                    0xFF);
                            break;
                    }

                }

                i--;
            }

            return entries;
        }

        ClutDefinition () {
            clutEntries2bit = generateDefault2bitClut();
            clutEntries4bit = generateDefault4bitClut();
            clutEntries8bit = generateDefault8bitClut();
        }

    }

    /* The object data segment contains the data of an object [7.2.5]
    */
    private class ObjectData {
        int pageId;
        int objectId;
        int objectVersionNumber;
        int objectCodingMethod;
        byte flags;
        int topFieldDataLength;
        byte[] topFieldData;
        int bottomFieldDataLength;
        byte[] bottomFieldData;
        int numberOfCodes;
    }

    private class RegionObject {
        int objectId;
        int objectType;
        int objectProvider;
        int objectHorizontalPosition;
        int objectVerticalPosition;
        int foregroundPixelCode;
        int backgroundPixelCode;
    }

    DvbSubtitlesParser() {
        this(1);
    }

    DvbSubtitlesParser(int subtitlePge) {
        this(subtitlePge, UNDEF_PAGE);
    }

    DvbSubtitlesParser(int subtitlePage, int ancillaryPage) {
        this(subtitlePage, ancillaryPage, 0);
    }

    DvbSubtitlesParser(int subtitlePage, int ancillaryPage, @Flags int flags) {
        this.subtitleService = new SubtitleService();
        this.flags = flags;

        this.defaultPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        this.defaultPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        this.defaultPaint.setPathEffect(null);

        this.fillRegionPaint.setStyle(Paint.Style.FILL);
        this.fillRegionPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
        this.fillRegionPaint.setPathEffect(null);

        this.debugRegionPaint.setColor(0xff00ff00);
        this.debugRegionPaint.setStyle(Paint.Style.STROKE);
        this.debugRegionPaint.setPathEffect(new DashPathEffect(new float[] {2,2}, 0));

        this.debugObjectPaint.setColor(0xffff0000);
        this.debugObjectPaint.setStyle(Paint.Style.STROKE);
        this.debugObjectPaint.setPathEffect(new DashPathEffect(new float[]{10, 20}, 0));

        this.subtitleService.subtitlePage = subtitlePage;
        this.subtitleService.ancillaryPage = ancillaryPage;

        this.subtitleService.displayDefinition = new DisplayDefinition();
        this.subtitleService.displayDefinition.updateBitmapResolution();
    }

    private void parseSubtitlingSegment() {

        /* Parse subtitling segment. ETSI EN 300 743 7.2

                          SYNTAX                  SIZE                SEMANTICS
        ---------------------------------------   ----   ------------------------------------------
        Subtitling_segment() {
           sync_byte                                8    An 8-bit field that shall be coded with the value '0000 1111'
           segment_type                             8    Indicates the type of data contained in the segment data field
           page_id                                 16    Identifies the subtitle service of the data contained in this subtitling_segment
           segment_length                          16    Number of bytes contained in the segment_data_field
           segment_data_field()                          This is the payload of the segment

         */

        int pageId, segmentId, segmentLength;
        segmentId = tsStream.readBits(8);
        switch (segmentId) {
            case DVBSUB_ST_DISPLAY_DEFINITION:
                if (BuildConfig.DEBUG) Log.d(TAG, "    Parse Display Definition segment.");
                DisplayDefinition tempDisplay = parseDisplayDefinitionSegment();
                if (tempDisplay != null && tempDisplay.pageId == subtitleService.subtitlePage) {
                    if (tempDisplay.displayWidth != subtitleService.displayDefinition.displayWidth ||
                            tempDisplay.displayHeight != subtitleService.displayDefinition.displayHeight ||
                            tempDisplay.displayWindowHorizontalPositionMaximum != subtitleService.displayDefinition.displayWindowHorizontalPositionMaximum ||
                            tempDisplay.displayWindowHorizontalPositionMinimum != subtitleService.displayDefinition.displayWindowHorizontalPositionMinimum ||
                            tempDisplay.displayWindowVerticalPositionMaximum != subtitleService.displayDefinition.displayWindowVerticalPositionMaximum ||
                            tempDisplay.displayWindowVerticalPositionMinimum != subtitleService.displayDefinition.displayWindowVerticalPositionMinimum ||
                            tempDisplay.flags != subtitleService.displayDefinition.flags) {
                        subtitleService.displayDefinition = tempDisplay;
                        subtitleService.displayDefinition.updateBitmapResolution();
                    } else {
                        subtitleService.displayDefinition.versionNumber = tempDisplay.versionNumber;
                    }

                    if (BuildConfig.DEBUG) Log.d(TAG + "/DDS", "    [versionNumber] = " + tempDisplay.versionNumber +
                            " [width/height] = " + (tempDisplay.displayWidth + 1) + "/" + (tempDisplay.displayHeight + 1) +
                            " Window[minX/minY/maxX/maxY] = " + tempDisplay.displayWindowHorizontalPositionMinimum +
                            "/" + tempDisplay.displayWindowVerticalPositionMinimum +
                            "/" + tempDisplay.displayWindowHorizontalPositionMaximum +
                            "/" + tempDisplay.displayWindowVerticalPositionMaximum
                    );
                }
                break;
            case DVBSUB_ST_PAGE_COMPOSITION:
                if (BuildConfig.DEBUG) Log.d(TAG, "    Parse Page Composition segment.");
                PageComposition tempPage = parsePageCompositionSegment();
                if (tempPage != null && tempPage.pageId == subtitleService.subtitlePage) {
                    if (tempPage.pageState == DVBSUB_PCS_STATE_NORMAL && subtitleService.pageComposition == null)
                        break;
                    subtitleService.pageComposition = tempPage;
                }
                break;
            case DVBSUB_ST_REGION_COMPOSITION:
                if (BuildConfig.DEBUG) Log.d(TAG, "    Parse Region Composition segment.");
                RegionComposition tempRegionComposition = parseRegionCompositionSegment();
                if (tempRegionComposition != null && tempRegionComposition.pageId == subtitleService.subtitlePage) {
                    subtitleService.regions.put(tempRegionComposition.regionId, tempRegionComposition);
                }
                break;
            case DVBSUB_ST_CLUT_DEFINITION:
                if (BuildConfig.DEBUG) Log.d(TAG, "    Parse Clut Definition segment.");
                ClutDefinition tempClutDefinition = parseClutDefinitionSegment();
                if (tempClutDefinition != null ) {
                    if (tempClutDefinition.pageId == subtitleService.subtitlePage) {
                        subtitleService.cluts.put(tempClutDefinition.clutId, tempClutDefinition);
                    } else if (tempClutDefinition.pageId == subtitleService.ancillaryPage) {
                        subtitleService.ancillaryCluts.put(tempClutDefinition.clutId, tempClutDefinition);
                    }
                }
                break;
            case DVBSUB_ST_OBJECT_DATA:
                if (BuildConfig.DEBUG) Log.d(TAG, "    Parse Object Data segment.");
                ObjectData tempObjectData = parseObjectDataSegment();
                if (tempObjectData != null) {
                    if (tempObjectData.pageId == subtitleService.subtitlePage) {
                        subtitleService.objects.put(tempObjectData.objectId, tempObjectData);
                    } else if (tempObjectData.pageId == subtitleService.ancillaryPage) {
                        subtitleService.ancillaryObjects.put(tempObjectData.objectId, tempObjectData);
                    }
                }
                break;
            case DVBSUB_ST_ENDOFDISPLAY:
                pageId = tsStream.readBits(16);
                segmentLength = tsStream.readBits(16);
                if (BuildConfig.DEBUG) Log.d(TAG, "pageId " + pageId + "end of display size = " + segmentLength);
                tsStream.skipBits(segmentLength * 8);
                break;
            case DVBSUB_ST_STUFFING:
                pageId = tsStream.readBits(16);
                segmentLength = tsStream.readBits(16);
                if (BuildConfig.DEBUG) Log.d(TAG, "pageId " + pageId + "stuffing size = " + segmentLength);
                tsStream.skipBits(segmentLength * 8);
                break;
            default:
                break;
        }
    }

    private DisplayDefinition parseDisplayDefinitionSegment() {

        /* Parse display definition segment. ETSI EN 300 743 7.2.1

                          SYNTAX                  SIZE                SEMANTICS
        ---------------------------------------   ----   ------------------------------------------
        display_definition_segment(){
           sync_byte                                8    An 8-bit field that shall be coded with the value '0000 1111'
           segment_type                             8    Indicates the type of data contained in the segment data field
           page_id                                 16    Identifies the subtitle service of the data contained in this subtitling_segment
           segment_length                          16    Number of bytes contained in the segment_data_field
           dds_version_number                       4    Incremented when any of the contents of this segment change
           display_window_flag                      1    if "1" display the subtitle in the defined window
           reserved                                 3
           display_width                           16    Specifies the maximum horizontal width of the display in pixels minus 1
           display_height                          16    Specifies the maximum vertical height of the display in lines minus 1
           if (display_window_flag == 1) {               With origin in the top-left of the screen:
              display_window_horizontal_position_minimum
                                                   16    Specifies the left-hand most pixel of this DVB subtitle display set
              display_window_horizontal_position_maximum
                                                   16    Specifies the right-hand most pixel of this DVB subtitle display set
              display_window_vertical_position_minimum
                                                   16    Specifies the upper most line of this DVB subtitle display set
              display_window_vertical_position_maximum
                                                   16    Specifies the bottom line of this DVB subtitle display set
           }
        }
        */

        DisplayDefinition display = new DisplayDefinition();

        display.pageId = tsStream.readBits(16);
        tsStream.skipBits(16);
        display.versionNumber = tsStream.readBits(4);
        if (tsStream.readBits(1) == 1) {
            display.flags |= DISPLAY_WINDOW_FLAG;
        }
        tsStream.skipBits(3);
        display.displayWidth = tsStream.readBits(16);
        display.displayHeight = tsStream.readBits(16);
        if ((display.flags & DISPLAY_WINDOW_FLAG) != 0) {
            display.displayWindowHorizontalPositionMinimum = tsStream.readBits(16);
            display.displayWindowHorizontalPositionMaximum = tsStream.readBits(16);
            display.displayWindowVerticalPositionMinimum = tsStream.readBits(16);
            display.displayWindowVerticalPositionMaximum = tsStream.readBits(16);
        } else {
            display.displayWindowHorizontalPositionMinimum = 0;
            display.displayWindowHorizontalPositionMaximum = display.displayWidth;
            display.displayWindowVerticalPositionMinimum = 0;
            display.displayWindowVerticalPositionMaximum = display.displayHeight;
        }

        return display;
    }

    private PageComposition parsePageCompositionSegment() {

        /* Parse page composition segment. ETSI EN 300 743 7.2.2

                          SYNTAX                  SIZE                SEMANTICS
        ---------------------------------------   ----   ------------------------------------------
        page_composition_segment() {
            sync_byte                               8
            segment_type                            8
            page_id                                16
            segment_length                         16
            page_time_out                           8    The period after the page instace should be erased
            page_version_number                     4    Incremented when any of the contents of this segment change
            page_state                              2    The status of the subtitling page instance
            reserved                                2
            while (processed_length < segment_length) {  Page region list
                region_id                           8    Uniquely identifies a region within a page
                reserved                            8
                region_horizontal_address          16    Horizontal address of the top left pixel of this region
                region_vertical_address            16    Vertical address of the top line of this region
            }
        }
        */

        PageComposition page = new PageComposition();

        page.pageId = tsStream.readBits(16);
        int remainingSegmentLength = tsStream.readBits(16);
        page.pageTimeOut = tsStream.readBits(8);
        page.pageVersionNumber = tsStream.readBits(4);
        page.pageState = tsStream.readBits(2);
        tsStream.skipBits(2);

        if (page.pageState == DVBSUB_PCS_STATE_NORMAL &&
                subtitleService.pageComposition != null &&
                subtitleService.pageComposition.pageId == page.pageId &&
                (subtitleService.pageComposition.pageVersionNumber + 1) % 16 == page.pageVersionNumber) {
            //page.pageRegions = subtitleService.pageComposition.pageRegions;

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "        Updated Page Composition. pageId: " + page.pageId +
                        " version: " + page.pageVersionNumber +
                        " timeout: " + page.pageTimeOut
                );
            }

        } else if (subtitleService.subtitlePage == page.pageId) {
            if (BuildConfig.DEBUG) {
                if (page.pageState == DVBSUB_PCS_STATE_NORMAL) {
                    Log.d(TAG, "        FAILED Page Composition update. pageId: " + page.pageId +
                            " Version(Old/New): " + (subtitleService.pageComposition != null ? subtitleService.pageComposition.pageVersionNumber : "NaN") + "/" + page.pageVersionNumber);
                }
            }

            subtitleService.newSubtitle = false;
            subtitleService.pageComposition = null;
            subtitleService.regions = new SparseArray<>();
            subtitleService.cluts = new SparseArray<>();
            subtitleService.objects = new SparseArray<>();

            if (BuildConfig.DEBUG) {
                if (page.pageState != DVBSUB_PCS_STATE_NORMAL) {
                    Log.d(TAG, "        New Page Composition. pageId: " + page.pageId +
                            " version: " + page.pageVersionNumber +
                            " timeout: " + page.pageTimeOut
                    );
                }
            }
        }

        remainingSegmentLength -= 2;
        while (remainingSegmentLength > 0) {
            PageRegion region = new PageRegion();

            region.regionId = tsStream.readBits(8);
            tsStream.skipBits(8);
            region.regionHorizontalAddress = tsStream.readBits(16);
            region.regionVerticalAddress = tsStream.readBits(16);

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "            " +
                        (page.pageRegions.get(region.regionId) == null ? "New" : "Upd.") +
                        " Page Region. regionId: " + region.regionId +
                        " (x/y): (" + region.regionHorizontalAddress + "/" + region.regionVerticalAddress + ")");
            }

            page.pageRegions.put(region.regionId, region);

            remainingSegmentLength -= 6;
        }

        return page;
    }

    private RegionComposition parseRegionCompositionSegment() {

        /* Parse region composition segment. ETSI EN 300 743 7.2.3

                          SYNTAX                  SIZE                SEMANTICS
        ---------------------------------------   ----   ------------------------------------------
        region_composition_segment() {
            sync_byte                               8
            segment_type                            8
            page_id                                16
            segment_length                         16
            region_id                               8     Uniquely identifies the region
            region_version_number                   4     Indicates the version of this region
            region_fill_flag                        1     If set the region is to be filled region_n-bit_pixel_code clut index
            reserved                                3
            region_width                           16     Specifies the horizontal length of this region
            region_height                          16     Specifies the vertical length of the region
            region_level_of_compatibility           3     Code that indicates the minimum bithdepth of CLUT
            region_depth                            3     Identifies the intended pixel depth for this region
            reserved                                2
            CLUT_id                                 8     Identifies the family of CLUTs that applies to this region
            region_8-bit_pixel_code                 8     Specifies the entry of the applied 8-bit CLUT as background colour
            region_4-bit_pixel-code                 4     Specifies the entry of the applied 4-bit CLUT as background colour
            region_2-bit_pixel-code                 2     Specifies the entry of the applied 2-bit CLUT as background colour
            reserved                                2
            while (processed_length < segment_length) {   list of region objects
                object_id                          16     Identifies an object that is shown in the region
                object_type                         2     Identifies the type of object
                object_provider_flag                2     How this object is provided
                object_horizontal_position         12     Specifies the horizontal position of the top left pixel of this object
                reserved                            4
                object_vertical_position           12     Specifies the vertical position of the top left pixel of this object
                if (object_type ==0x01 or object_type == 0x02){ UNSUPPORTED
                    foreground_pixel_code           8
                    background_pixel_code           8
                }
            }
        }
        */

        RegionComposition region = new RegionComposition();

        region.pageId = tsStream.readBits(16);
        int remainingSegmentLength = tsStream.readBits(16);
        region.regionId = tsStream.readBits(8);
        region.regionVersionNumber = tsStream.readBits(4);
        if (tsStream.readBits(1) == 1) {
            region.flags |= REGION_FILL_FLAG;
        }
        tsStream.skipBits(3);
        region.regionWidth = tsStream.readBits(16);
        region.regionHeight = tsStream.readBits(16);
        region.regionLevelOfCompatibility = tsStream.readBits(3);
        region.regionDepth = tsStream.readBits(3);
        tsStream.skipBits(2);
        region.clutId = tsStream.readBits(8);
        tsStream.skipBits(16);

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "        New Region Composition. regionId: " + region.regionId +
                    " (w/h): (" + region.regionWidth + "/" + region.regionHeight + ")");
        }

        int arrayIndex = 0; // index by an incremental counter to allow repeating objects in one region

        if (subtitleService.pageComposition != null && subtitleService.pageComposition.pageId == region.pageId &&
                subtitleService.pageComposition.pageState == DVBSUB_PCS_STATE_NORMAL) {
            RegionComposition tempRegion = subtitleService.regions.get(region.regionId);
            if (tempRegion != null) {
                region.regionObjects = tempRegion.regionObjects;
                arrayIndex = region.regionObjects.size();
            }
        }

        remainingSegmentLength -= 10;
        RegionObject object;
        while (remainingSegmentLength > 0) {
            object = new RegionObject();

            object.objectId = tsStream.readBits(16);
            object.objectType = tsStream.readBits(2);
            object.objectProvider = tsStream.readBits(2);
            object.objectHorizontalPosition = tsStream.readBits(12);
            tsStream.skipBits(4);
            object.objectVerticalPosition = tsStream.readBits(12);
            remainingSegmentLength -= 6;

            if (object.objectType == 0x01 || object.objectType == 0x02) { // Only seems to affect to char subtitles
                object.foregroundPixelCode = tsStream.readBits(8);
                object.backgroundPixelCode = tsStream.readBits(8);
                remainingSegmentLength -= 2;
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "            New Region Object[" + arrayIndex + "]." +
                        " objectId: " + object.objectId +
                        " (x/y): (" + object.objectHorizontalPosition + "/" + object.objectVerticalPosition + ")");
            }

            region.regionObjects.put(arrayIndex++, object);
        }


        return region;
    }

    private ClutDefinition parseClutDefinitionSegment() {

        /* Parse CLUT definition segment. ETSI EN 300 743 7.2.4

                          SYNTAX                  SIZE                SEMANTICS
        ---------------------------------------   ----   ------------------------------------------
        CLUT_definition_segment() {
            sync_byte                               8
            segment_type                            8
            page_id                                16
            segment_length                         16
            CLUT-id                                 8    Uniquely identifies within a page the CLUT family
            CLUT_version_number                     4    Indicates the version of this segment data
            reserved                                4
            while (processed_length < segment_length) {  Clut entries list
                CLUT_entry_id                       8    Specifies the entry number of the CLUT
                2-bit/entry_CLUT_flag               1    Indicates this entry is to be loaded into the 2-bit/entry CLUT
                4-bit/entry_CLUT_flag               1    Indicates this entry is to be loaded into the 4-bit/entry CLUT
                8-bit/entry_CLUT_flag               1    Indicates this entry is to be loaded into the 8-bit/entry CLUT
                reserved                            4
                full_range_flag                     1    Indicates that the Y_value, Cr_value, Cb_value and T_value
                                                         fields have the full 8-bit resolution
                if full_range_flag =='1' {
                    Y-value                         8    The Y value for this CLUT entry.
                    Cr-value                        8    The Cr value for this CLUT entry.
                    Cb-value                        8    The Cb value for this CLUT entry.
                    T-value                         8    The Transparency value for this CLUT entry. 0 = no transparency
                } else {
                    Y-value                         6    The Y value for this CLUT entry.
                    Cr-value                        4    The Cr value for this CLUT entry.
                    Cb-value                        4    The Cb value for this CLUT entry.
                    T-value                         2    The Transparency value for this CLUT entry. 0 = no transparency
                }
            }
        }
        */

        ClutDefinition clut = new ClutDefinition();
        clut.pageId = tsStream.readBits(16);
        int remainingSegmentLength = tsStream.readBits(16);
        clut.clutId = tsStream.readBits(8);
        clut.clutVersionNumber = tsStream.readBits(4);
        tsStream.skipBits(4);

        remainingSegmentLength -= 2;
        ClutEntry entry;
        int Y, Cb, Cr, T;
        int entryId, entryFlags;
        while (remainingSegmentLength > 0) {
            entryId = tsStream.readBits(8);
            entryFlags = tsStream.readBits(8);

            if ((entryFlags & 0x80) != 0) {
                entry = clut.clutEntries2bit[entryId];
            } else if ((entryFlags & 0x40) != 0) {
                entry = clut.clutEntries4bit[entryId];
            } else {
                entry = clut.clutEntries8bit[entryId];
            }

            entry.flags = (byte) (entryFlags & 0xE1);
            if ((entry.flags & 0x01) != 0) {
                Y =  tsStream.readBits(8);
                Cr = tsStream.readBits(8);
                Cb = tsStream.readBits(8);
                T =  tsStream.readBits(8);
                remainingSegmentLength -= 6;
            } else {
                Y =  tsStream.readBits(6) << 2;
                Cr = tsStream.readBits(4) << 4;
                Cb = tsStream.readBits(4) << 4;
                T =  tsStream.readBits(2) << 6;
                remainingSegmentLength -= 4;
            }

            if (Y == 0x00) {
                Cr = 0x00;
                Cb = 0x00;
                T =  0xFF;
            }

            entry.clutYCbCrT(Y, Cb, Cr, T);
        }
        return clut;
    }

    private ObjectData parseObjectDataSegment() {

        /* Parse object data segment. ETSI EN 300 743 7.2.5

                          SYNTAX                  SIZE                SEMANTICS
        ---------------------------------------   ----   ------------------------------------------
        object_data_segment() {
            sync_byte                               8
            segment_type                            8
            page_id                                16
            segment_length                         16
            object_id                              16    Uniquely identifies within the page the object
            object_version_number                   4    Indicates the version of this segment data
            object_coding_method                    2    Specifies the method used to code the object
            non_modifying_colour_flag               1    Indicates that the CLUT entry value '1' is a non modifying colour
            reserved                                1
            if (object_coding_method == '00'){
                top_field_data_block_length        16    Specifies the number of bytes contained in his pixel-data_sub-blocks
                bottom_field_data_block_length     16    Specifies the number of bytes contained in his pixel-data_sub-blocks
                while(processed_length<top_field_data_block_length)
                    pixel-data_sub-block()
                while (processed_length<bottom_field_data_block_length)
                    pixel-data_sub-block()
                if (!wordaligned())
                    8_stuff_bits                    8
            }
            if (object_coding_method == '01') {          UNSUPPORTED
                number of codes                     8
                for (i == 1, i <= number of codes, i ++)
                character_code                     16
            }
        }
        */

        ObjectData object = new ObjectData();
        object.pageId = tsStream.readBits(16);
        tsStream.skipBits(16); // remainingSegmentLength
        object.objectId = tsStream.readBits(16);
        object.objectVersionNumber = tsStream.readBits(4);
        object.objectCodingMethod = tsStream.readBits(2);
        if (tsStream.readBits(1) == 1) {
            object.flags |= OBJECT_NON_MODIFYING_COLOUR_FLAG;
        }
        tsStream.skipBits(1);

        if (object.objectCodingMethod == DVBSUB_ODS_CHAR_CODED) {
            /* not implemented yet */
            object.numberOfCodes = tsStream.readBits(8);
            tsStream.skipBits(object.numberOfCodes * 16);
        } else if (object.objectCodingMethod == DVBSUB_ODS_PIXEL_CODED) {
            object.topFieldDataLength = tsStream.readBits(16);
            object.bottomFieldDataLength = tsStream.readBits(16);

            object.topFieldData = new byte[object.topFieldDataLength];
            System.arraycopy(tsStream.data, tsStream.getPosition() / 8, object.topFieldData, 0, object.topFieldDataLength);
            tsStream.skipBits(object.topFieldDataLength * 8);

            object.bottomFieldData = new byte[object.bottomFieldDataLength];
            if (object.bottomFieldDataLength == 0) {
                object.bottomFieldData = object.topFieldData;
            } else {
                System.arraycopy(tsStream.data, tsStream.getPosition() / 8, object.bottomFieldData, 0, object.bottomFieldDataLength);
                tsStream.skipBits(object.bottomFieldDataLength * 8);
            }
        }

        return object;
    }

    private Bitmap parsePixelDataSubBlocks (ObjectData object, ClutDefinition clut, int regionDepth,
                                            int horizontalAddress, int verticalAddress) {

        /* Parse pixel-data sub-block. ETSI EN 300 743 7.2.5.1

                          SYNTAX                  SIZE
        ---------------------------------------   ----
        pixel-data_sub-block() {
            data_type                               8
            if data_type =='0x10' {
                repeat {
                    2-bit/pixel_code_string()
                } until (end of 2-bit/pixel_code_string)
                while (!bytealigned())
                    2_stuff_bits                    2
                if data_type =='0x11' {
                    repeat {
                        4-bit/pixel_code_string()
                    } until (end of 4-bit/pixel_code_string)
                    if (!bytealigned())
                        4_stuff_bits                4
                }
            }
            if data_type =='0x12' {
                repeat {
                    8-bit/pixel_code_string()
                } until (end of 8-bit/pixel_code_string)
            }
            if data_type =='0x20'
            2_to_4-bit_map-table                   16
            if data_type =='0x21'
            2_to_8-bit_map-table                   32
            if data_type =='0x22'
            4_to_8-bit_map-table                  128
        }
        */

        int line, column;
        int i;
        byte[] clutMapTable, clutMapTable24, clutMapTable28, clutMapTable48;


        ClutEntry[] clutEntries;
        if (regionDepth == DVBSUB_RCS_BITDEPTH_8) {
            clutEntries = clut.clutEntries8bit;
        } else if (regionDepth == DVBSUB_RCS_BITDEPTH_4) {
            clutEntries = clut.clutEntries4bit;
        } else {
            clutEntries = clut.clutEntries2bit;
        }

        int lineHeight;

        ParsableBitArray[] pixelData = new ParsableBitArray[2];
        pixelData[0]= new ParsableBitArray(object.topFieldData);
        if ( object.bottomFieldDataLength == 0) {
            lineHeight = 2;
        } else {
            lineHeight = 1;
            pixelData[1] = new ParsableBitArray(object.bottomFieldData);

        }

        ParsableBitArray data;
        int field = 0;
        while (field < 2) {
            data = pixelData[field];
            column = horizontalAddress;
            line = verticalAddress + field;
            clutMapTable24 = null;
            clutMapTable28 = null;
            clutMapTable48 = null;

            while (data.bitsLeft() > 0) {
                switch (data.readBits(8)) {
                    case DVBSUB_DT_2BP_CODE_STRING:
                        if (regionDepth == DVBSUB_RCS_BITDEPTH_8) {
                            clutMapTable = clutMapTable28 == null ? defaultMap28 : clutMapTable28;
                        } else if (regionDepth == DVBSUB_RCS_BITDEPTH_4) {
                            clutMapTable = clutMapTable24 == null ? defaultMap24 : clutMapTable24;
                        } else {
                            clutMapTable = null;
                        }
                        column += dvbSub2BitPixelCodeString(data, lineHeight, clutEntries, clutMapTable,
                                column, line, (object.flags & OBJECT_NON_MODIFYING_COLOUR_FLAG) == 0);
                        if ((i = data.getPosition() % 8) != 0) {
                            data.skipBits(7 - i + 1);
                        }
                        break;
                    case DVBSUB_DT_4BP_CODE_STRING:
                        if (regionDepth == DVBSUB_RCS_BITDEPTH_8)
                            clutMapTable = clutMapTable48 == null ? defaultMap48 : clutMapTable48;
                        else
                            clutMapTable = null;
                        column += dvbSub4BitPixelCodeString(data, lineHeight, clutEntries, clutMapTable,
                                column, line, (object.flags & OBJECT_NON_MODIFYING_COLOUR_FLAG) == 0);
                        if ((i = data.getPosition() % 8) != 0) {
                            data.skipBits(7 - i + 1);
                        }
                        break;
                    case DVBSUB_DT_8BP_CODE_STRING:
                        column += dvbSub8BitPixelCodeString(data, lineHeight, clutEntries, null,
                                column, line, (object.flags & OBJECT_NON_MODIFYING_COLOUR_FLAG) == 0);
                        break;
                    case DVBSUB_DT_24_TABLE_DATA:
                        clutMapTable24 = new byte[4];
                        for (i = 0; i < 4; i++) {
                            clutMapTable24[i] = (byte) data.readBits(4);
                        }
                        break;
                    case DVBSUB_DT_28_TABLE_DATA:
                        clutMapTable28 = new byte[4];
                        for (i = 0; i < 4; i++) {
                            clutMapTable28[i] = (byte) data.readBits(8);
                        }
                        break;
                    case DVBSUB_DT_48_TABLE_DATA:
                        clutMapTable48 = new byte[16];
                        for (i = 0; i < 4; i++) {
                            clutMapTable48[i] = (byte) data.readBits(8);
                        }
                        break;
                    case DVBSUB_DT_END_LINE:
                        column = horizontalAddress;
                        line += 2;
                        break;
                    default:
                        break;
                }
            }
            field += lineHeight;
        }

        return null;
    }

    private int dvbSub2BitPixelCodeString(ParsableBitArray data, int lineHeigth,
                                          ClutEntry[] clutEntries, byte[] clutMapTable,
                                          int column, int line, boolean paint) {

        /* Parse 2-bit/pixel code string. ETSI EN 300 743 7.2.5.2

                          SYNTAX                  SIZE
        ---------------------------------------   ----
        2-bit/pixel_code_string() {
            if (nextbits() != '00') {
                2-bit_pixel-code                    2
            } else {
                2-bit_zero                          2
                switch_1 1 bslbf
                if (switch_1 == '1') {
                    run_length_3-10                 3
                    2-bit_pixel-code                2
                } else {
                    switch_2                        1
                    if (switch_2 == '0') {
                        switch_3                    2
                        if (switch_3 == '10') {
                            run_length_12-27        4
                            2-bit_pixel-code        2
                        }
                        if (switch_3 == '11') {
                            run_length_29-284       8
                            2-bit_pixel-code        2
                        }
                    }
                }
            }
        }
        */

        int savedColumn = column, peek, runLength, clutIdx = 0x00, colour;
        boolean endOfPixelCodeString = false;

        while (!endOfPixelCodeString) {
            runLength = 0;
            peek = data.readBits(2);
            if (peek != 0x00) {
                runLength = 1;
                clutIdx = peek;
            } else {
                peek = data.readBits(1);
                if (peek == 0x01) {
                    runLength = 3 + data.readBits(3);
                    clutIdx = data.readBits(2);
                } else {
                    peek = data.readBits(1);
                    if (peek == 0x00) {
                        peek = data.readBits(2);
                        switch (peek) {
                            case 0x00:
                                endOfPixelCodeString = true;
                                break;
                            case 0x01:
                                runLength = 2;
                                clutIdx = 0x00;
                                break;
                            case 0x02:
                                runLength = 12 + data.readBits(4);
                                clutIdx = data.readBits(2);
                                break;
                            case 0x03:
                                runLength = 29 + data.readBits(8);
                                clutIdx = data.readBits(2);
                                break;
                        }
                    }
                }
            }

            if (runLength != 0 && paint) {
                colour = clutMapTable != null ? clutEntries[clutMapTable[clutIdx]].ARGB
                        : clutEntries[clutIdx].ARGB;
                defaultPaint.setColor(colour);
                canvas.drawRect(
                        column, line, column + runLength, line + lineHeigth, defaultPaint);
            }

            column += runLength;
        }

        return column - savedColumn;
    }

    private int dvbSub4BitPixelCodeString(ParsableBitArray data, int lineHeigth,
                                          ClutEntry[] clutEntries, byte[] clutMapTable,
                                          int column, int line, boolean paint) {

        /* Parse 4-bit/pixel code string. ETSI EN 300 743 7.2.5.2

                          SYNTAX                  SIZE
        ---------------------------------------   ----
        4-bit/pixel_code_string() {
            if (nextbits() != '0000') {
                4-bit_pixel-code                    4
            } else {
                4-bit_zero                          4
                switch_1                            1
                if (switch_1 == '0') {
                    if (nextbits() != '000')
                        run_length_3-9              3
                    else
                        end_of_string_signal        3
                } else {
                    switch_2                        1
                    if (switch_2 == '0') {
                        run_length_4-7              2
                        4-bit_pixel-code            4
                    } else {
                        switch_3                    2
                        if (switch_3 == '10') {
                            run_length_9-24         4
                            4-bit_pixel-code        4
                        }
                        if (switch_3 == '11') {
                            run_length_25-280       8
                            4-bit_pixel-code        4
                        }
                    }
                }
            }
        }
        */

        int savedColumn = column, peek, runLength, clutIdx = 0x00, colour;
        boolean endOfPixelCodeString = false;

        while (!endOfPixelCodeString) {
            runLength = 0;
            peek = data.readBits(4);
            if (peek != 0x00) {
                runLength = 1;
                clutIdx = peek;
            } else {
                peek = data.readBits(1);
                if (peek == 0x00) {
                    peek = data.readBits(3);
                    if (peek != 0x00) {
                        runLength = 2 + peek;
                        clutIdx = 0x00;
                    } else {
                        endOfPixelCodeString = true;
                    }
                } else {
                    peek = data.readBits(1);
                    if (peek == 0x00) {
                        runLength = 4 + data.readBits(2);
                        clutIdx = data.readBits(4);
                    } else {
                        peek = data.readBits(2);
                        switch (peek) {
                            case 0x00:
                                runLength = 1;
                                clutIdx = 0x00;
                                break;
                            case 0x01:
                                runLength = 2;
                                clutIdx = 0x00;
                                break;
                            case 0x02:
                                runLength = 9 + data.readBits(4);
                                clutIdx = data.readBits(4);
                                break;
                            case 0x03:
                                runLength = 25 + data.readBits(8);
                                clutIdx = data.readBits(4);
                                break;
                        }
                    }
                }
            }

            if (runLength != 0 && paint) {
                colour = clutMapTable != null ? clutEntries[clutMapTable[clutIdx]].ARGB
                        : clutEntries[clutIdx].ARGB;
                defaultPaint.setColor(colour);
                canvas.drawRect(
                        column, line, column + runLength, line + lineHeigth, defaultPaint);
            }

            column += runLength;
        }

        return column - savedColumn;
    }

    private int dvbSub8BitPixelCodeString(ParsableBitArray data, int lineHeigth,
                                          ClutEntry[] clutEntries, byte[] clutMapTable,
                                          int column, int line, boolean paint) {

        /* Parse 8-bit/pixel code string. ETSI EN 300 743 7.2.5.2

                          SYNTAX                  SIZE
        ---------------------------------------   ----

        8-bit/pixel_code_string() {
            if (nextbits() != '0000 0000') {
                8-bit_pixel-code                    8
            } else {
                8-bit_zero                          8
                switch_1                            1
                if switch_1 == '0' {
                    if nextbits() != '000 0000'
                        run_length_1-127            7
                    else
                        end_of_string_signal        7
                } else {
                    run_length_3-127                7
                    8-bit_pixel-code                8
                }
            }
        }
        */

        int savedColumn = column, peek, runLength, clutIdx = 0x00, colour;
        boolean endOfPixelCodeString = false;

        while (!endOfPixelCodeString) {
            runLength = 0;
            peek = data.readBits(8);
            if (peek != 0x00) {
                runLength = 1;
                clutIdx = peek;
            } else {
                peek = data.readBits(1);
                if (peek == 0x00) {
                    peek = data.readBits(7);
                    if (peek != 0x00) {
                        runLength = peek;
                        clutIdx = 0x00;
                    } else {
                        endOfPixelCodeString = true;
                    }
                } else {
                    runLength = data.readBits(7);
                    clutIdx = data.readBits(8);
                }
            }

            if (runLength != 0 && paint) {
                colour = clutMapTable != null ? clutEntries[clutMapTable[clutIdx]].ARGB
                        : clutEntries[clutIdx].ARGB;
                defaultPaint.setColor(colour);
                canvas.drawRect(
                        column, line, column + runLength, line + lineHeigth, defaultPaint);
            }

            column += runLength;
        }

        return column - savedColumn;
    }

    List<Cue> dvbSubsDecode(byte[] input, int inputSize) {

        /* process PES PACKET. ETSI EN 300 743 7.1

                          SYNTAX                  SIZE                SEMANTICS
        ---------------------------------------   ----   ------------------------------------------
        PES_data_field() {
           data_identifier                          8    For DVB subtitle streams it shall be 0x20
           subtitle_stream_id                       8    For DVB subtitling stream it shall be 0x00
              while nextbits() == '0000 1111' {
                 Subtitling_segment()
              }
              end_of_PES_data_field_marker          8    An 8-bit field with fixed contents '1111 1111'

         */

        if (input != null) {
            tsStream = new ParsableBitArray(input, inputSize);
        } else {
            return null;
        }
        if (!isSet(FLAG_PES_STRIPPED_DVBSUB)) {
            if (tsStream.readBits(8) != 0x20) {   // data_identifier
                return null;
            }
            if (tsStream.readBits(8) != 0x00) {   // subtitle_stream_id
                return null;
            }
        }

        if (BuildConfig.DEBUG) Log.d(TAG,"New PES subtitle packet.");

        int sync = tsStream.readBits(8);
        // test for segment Sync Byte and account for possible additional wordalign byte in Object data segment
        while (sync == 0x0f || (sync == 0x00 && (sync = tsStream.readBits(8)) == 0x0f)) {
            parseSubtitlingSegment();
            if (isSet(FLAG_PES_STRIPPED_DVBSUB) && tsStream.bitsLeft() == 0) {
                break;
            }
            sync = tsStream.readBits(8);

        }

        if (sync == 0xff || (isSet(FLAG_PES_STRIPPED_DVBSUB) && tsStream.bitsLeft() == 0)) { // end_of_PES_data_field_marker
            // paint the current Subtitle definition
            if (subtitleService.pageComposition != null) {
                List<Cue> cueList = new ArrayList<>();

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Rendering subtitle. w: " + subtitleService.displayDefinition.displayWidth +
                            " h: " + subtitleService.displayDefinition.displayHeight);

                    if ((subtitleService.displayDefinition.flags & DISPLAY_WINDOW_FLAG) != 0) {
                        Log.d(TAG, "    Window dimensions (x/y/w/h): (" +
                                subtitleService.displayDefinition.displayWindowHorizontalPositionMinimum + "/" +
                                subtitleService.displayDefinition.displayWindowVerticalPositionMinimum + "/" +
                                (subtitleService.displayDefinition.displayWindowHorizontalPositionMaximum -
                                        subtitleService.displayDefinition.displayWindowHorizontalPositionMinimum) + "/" +
                                (subtitleService.displayDefinition.displayWindowVerticalPositionMaximum -
                                        subtitleService.displayDefinition.displayWindowVerticalPositionMinimum) + ")" );
                    }
                }

                int a,b;
                PageRegion pageRegion;
                RegionComposition regionComposition;
                int baseHorizontalAddress, baseVerticalAddress;
                ObjectData object;
                ClutDefinition clut;
                int regionKey;
                // process page regions
                for (a = 0; a < subtitleService.pageComposition.pageRegions.size(); a++) {
                    regionKey = subtitleService.pageComposition.pageRegions.keyAt(a);
                    pageRegion = subtitleService.pageComposition.pageRegions.get(regionKey);
                    regionComposition = subtitleService.regions.get(regionKey);

                    baseHorizontalAddress = pageRegion.regionHorizontalAddress;
                    baseVerticalAddress = pageRegion.regionVerticalAddress;

                    if ((subtitleService.displayDefinition.flags & DISPLAY_WINDOW_FLAG) != 0) {
                        baseHorizontalAddress +=
                                subtitleService.displayDefinition.displayWindowHorizontalPositionMinimum;
                        baseVerticalAddress +=
                                subtitleService.displayDefinition.displayWindowVerticalPositionMinimum;
                    }

                    // clip object drawing to the current region and display definition window
                    canvas.clipRect(
                            baseHorizontalAddress, baseVerticalAddress,
                            Math.min(baseHorizontalAddress + regionComposition.regionWidth,
                                    subtitleService.displayDefinition.displayWindowHorizontalPositionMaximum),
                            Math.min(baseVerticalAddress + regionComposition.regionHeight,
                                    subtitleService.displayDefinition.displayWindowVerticalPositionMaximum),
                            Region.Op.REPLACE);

                    if ((clut = subtitleService.cluts.get(regionComposition.clutId)) == null) {
                        if ((clut = subtitleService.ancillaryCluts.get(regionComposition.clutId)) == null) {
                            clut = defaultClut;
                        }
                    }

                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "    Region: " + regionKey + " (x/y/w/h): (" +
                                baseHorizontalAddress + "/" + baseVerticalAddress + "/" +
                                (baseHorizontalAddress + regionComposition.regionWidth - 1) + "/" +
                                (baseVerticalAddress + regionComposition.regionHeight - 1) + ")"
                        );

                        canvas.drawRect(
                                baseHorizontalAddress, baseVerticalAddress,
                                baseHorizontalAddress + regionComposition.regionWidth - 1,
                                baseVerticalAddress + regionComposition.regionHeight - 1,
                                debugRegionPaint);
                    }

                    RegionObject regionObject;
                    int objectKey;
                    // process regions compositions
                    for ( b = 0; b < regionComposition.regionObjects.size(); b++) {
                        objectKey = regionComposition.regionObjects.keyAt(b);
                        regionObject = regionComposition.regionObjects.get(objectKey);

                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "        Object[" + objectKey + "]. objectId: " + regionObject.objectId + " (x/y): (" +
                                    (baseHorizontalAddress + regionObject.objectHorizontalPosition) + "/" +
                                    (baseVerticalAddress + regionObject.objectVerticalPosition) + ")"
                            );

                            canvas.drawRect(
                                    baseHorizontalAddress + regionObject.objectHorizontalPosition,
                                    baseVerticalAddress + regionObject.objectVerticalPosition,
                                    baseHorizontalAddress + regionObject.objectHorizontalPosition + regionComposition.regionWidth - 1,
                                    baseVerticalAddress + regionObject.objectVerticalPosition + regionComposition.regionHeight - 1,
                                    debugObjectPaint);
                        }

                        if ((object = subtitleService.objects.get(regionObject.objectId)) == null) {
                            if ((object = subtitleService.ancillaryObjects.get(regionObject.objectId)) == null) {
                                continue;
                            }
                        }

                        parsePixelDataSubBlocks(object, clut, regionComposition.regionDepth,
                                baseHorizontalAddress + regionObject.objectHorizontalPosition,
                                baseVerticalAddress + regionObject.objectVerticalPosition);

                    }

                    // fill the region if needed
                    if ((regionComposition.flags & REGION_FILL_FLAG )!= 0) {
                        int colour;
                        if (regionComposition.regionDepth == DVBSUB_RCS_BITDEPTH_8) {
                            colour = clut.clutEntries8bit[regionComposition.region8bitPixelCode].ARGB;
                        } else if (regionComposition.regionDepth == DVBSUB_RCS_BITDEPTH_4) {
                            colour = clut.clutEntries4bit[regionComposition.region4bitPixelCode].ARGB;
                        } else {
                            colour = clut.clutEntries2bit[regionComposition.region2bitPixelCode].ARGB;
                        }

                        fillRegionPaint.setColor(colour);

                        canvas.drawRect(
                                baseHorizontalAddress, baseVerticalAddress,
                                baseHorizontalAddress + regionComposition.regionWidth ,
                                baseVerticalAddress + regionComposition.regionHeight,
                                fillRegionPaint);

                    }

                    Bitmap cueBitmap = Bitmap.createBitmap(bitmap,
                            baseHorizontalAddress, baseVerticalAddress,
                            regionComposition.regionWidth, regionComposition.regionHeight);
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                    regionComposition.cue = new Cue(cueBitmap,
                            (float) baseHorizontalAddress / subtitleService.displayDefinition.displayWidth, Cue.ANCHOR_TYPE_START,
                            (float) baseVerticalAddress / subtitleService.displayDefinition.displayHeight, Cue.ANCHOR_TYPE_START,
                            (float) regionComposition.regionWidth / subtitleService.displayDefinition.displayWidth,
                            (float) subtitleService.displayDefinition.displayWidth / subtitleService.displayDefinition.displayHeight);
                    cueList.add(regionComposition.cue);
                }

                return cueList;            }
        } else {
            Log.d(TAG,"Unexpected...");
        }
        return null;
    }

    private boolean isSet(@Flags int flag) {
        return (flags & flag) != 0;
    }

}