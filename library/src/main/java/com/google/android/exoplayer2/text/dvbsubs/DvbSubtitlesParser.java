package com.google.android.exoplayer2.text.dvbsubs;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.util.Log;

import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableBitArray;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/* Original C code taken from VLC project */
public class DvbSubtitlesParser {

    private static final String TAG = "DVBSubs";
    private final int NUM_BITMAPS = 2;
    /* List of different SEGMENT TYPES */
    /* According to EN 300-743, table 2 */
    final static int DVBSUB_ST_PAGE_COMPOSITION = 0x10;
    final static int DVBSUB_ST_REGION_COMPOSITION = 0x11;
    final static int DVBSUB_ST_CLUT_DEFINITION = 0x12;
    final static int DVBSUB_ST_OBJECT_DATA = 0x13;
    final static int DVBSUB_ST_DISPLAY_DEFINITION = 0x14;
    final static int DVBSUB_ST_ENDOFDISPLAY = 0x80;
    final static int DVBSUB_ST_STUFFING = 0xff;
    /* List of different OBJECT TYPES */
    /* According to EN 300-743, table 6 */
    final static int DVBSUB_OT_BASIC_BITMAP = 0x00;
    final static int DVBSUB_OT_BASIC_CHAR = 0x01;
    final static int DVBSUB_OT_COMPOSITE_STRING = 0x02;
    /* Pixel DATA TYPES */
    /* According to EN 300-743, table 9 */
    final static int DVBSUB_DT_2BP_CODE_STRING = 0x10;
    final static int DVBSUB_DT_4BP_CODE_STRING = 0x11;
    final static int DVBSUB_DT_8BP_CODE_STRING = 0x12;
    final static int DVBSUB_DT_24_TABLE_DATA = 0x20;
    final static int DVBSUB_DT_28_TABLE_DATA = 0x21;
    final static int DVBSUB_DT_48_TABLE_DATA = 0x22;
    final static int DVBSUB_DT_END_LINE = 0xf0;
    /* List of different Page Composition Segment state */
    /* According to EN 300-743, 7.2.1 table 3 */
    final static int DVBSUB_PCS_STATE_ACQUISITION = 0x01;
    final static int DVBSUB_PCS_STATE_CHANGE = 0x02;

    final static int DVBSUB_MAX_OBJECTS_STORED = 16;

    int offset, dataCount;
    int numTSPackets;
    private Paint graphicsPaintObject;
    private Canvas graphicsCanvasObject;
    private Bitmap[] subsBitmap;
    static ParsableBitArray tsStream;
    private DvbSubRegion[] regions;
    private DvbSubDisplay[] displays;
    private DvbSubObjectDef[] objects;
    private DvbSubClut[] cluts;
    private DvbSubPage[] pages;
    private int regionCounter;
    private int displayCounter;
    private int clutCounter;
    private int objectCounter;
    private int pageCounter;
    private int previousCoordX, previousCoordY;
    private boolean newGraphicData;
    int currentBitmap;
    private Map<Integer, Integer> pageMapping; // array index and pageid mapping
    private Map<Integer, Integer> regionMapping; // array index and regionid mapping
    private Map<Integer, Integer> objectMapping;
    private int displayWidth, displayHeight;
    private int displayHorizontal, displayVertical;

    class DvbSubObjectDef {
        public int id;
        public int version;
        public int codingMethod;
        public int nonModifyingColor;
        public int topFieldDataLength;
        public int bottomFieldDataLength;
        public int type;
        public int x;
        public int y;
        public int providerFlag;
        public int foregroundPC;
        public int backgroundPC;
        //public byte[] psz_text; /* for string of characters objects */
    }

    /* The entry in the palette CLUT */
    class DvbSubColor {
        public byte Y;
        public byte Cr;
        public byte Cb;
        public byte T;
    }

    /* The displays dimensions [7.2.1] */
    class DvbSubDisplay {
        public int id;
        public int version;

        public int width;
        public int height;

        public int windowed;
        /* these values are only relevant if windowed */
        public int x;
        public int y;
        public int maxX;
        public int maxY;
    }

    /* The Region is an aera on the image [7.2.3]
     * with a list of the object definitions associated and a CLUT */
    class DvbSubRegion {
        public int pageId;
        public int id;
        public int version;
        public int b_fill_region;
        public int x;
        public int y;
        public int width;
        public int height;
        public int levelComp;
        public int depth;
        public int clut;
        public int numberOfObjectDefs;
        public DvbSubObjectDef[] objectDefs;

        public int findObject(int objectId) {
            if (objectDefs != null) {
                for (int i = 0; i < numberOfObjectDefs; i++) {
                    if (objectDefs[i].id == objectId) {
                        return i;
                    }
                }
            }
            return -1;
        }
    }

    /* The object definition gives the position of the object in a region */
    class DvbSubRegionDef {
        public int id;
        public int x;
        public int y;
    }

    /* The page final static ints the list of regions [7.2.2] */
    class DvbSubPage {
        public int id;
        public int timeout; /* in seconds */
        public int state;
        public int version;
        public int regionDefs;
        public DvbSubRegionDef[] pRegionDefs;
    }

    /* [7.2.4] */
    class DvbSubClut {
        int id;
        int version;
        DvbSubColor[] c2b;
        DvbSubColor[] c4b;
        DvbSubColor[] c8b;
        DvbSubClut next;

        public DvbSubClut() {
            c2b = new DvbSubColor[4];
            for (int i = 0; i < 4; i++) {
                c2b[i] = new DvbSubColor();
            }
            c4b = new DvbSubColor[16];
            for (int i = 0; i < 16; i++) {
                c4b[i] = new DvbSubColor();
            }
            c8b = new DvbSubColor[256];
            for (int i = 0; i < 256; i++) {
                c8b[i] = new DvbSubColor();
            }
            next = null;
        }
    }

    private int addPage() {
        if (pageCounter == 0) {
            pages = new DvbSubPage[1];
            pages[0] = new DvbSubPage();
        } else {
            pages = Arrays.copyOf(pages, pageCounter + 1);
            pages[pageCounter] = new DvbSubPage();
        }
        pageCounter++;
        return pageCounter - 1;
    }

    private int checkAlreadyStoredPages(DvbSubPage received, int pageid) {
        if (pages != null) {
            for (int i = 0; i < pages.length; i++) {
                if (pages[i].id == received.id) {
                    pages[i] = received;
                    return pageMapping.get(received.id);
                }
            }
        }
        int index = addPage();
        pages[index] = received;
        pageMapping.put(pageid, index);
        return index;
    }

    private void checkAlreadyStoredCLUT(DvbSubClut received, int clutId) {
        if (cluts != null) {
            for (int i = 0; i < cluts.length; i++) {
                if (cluts[i].id == received.id) {
                    cluts[i] = received;
                    Log.d(TAG, "overwriting already stored region");
                    return;
                }
            }
        }
        int index = addCLUT();
        cluts[index] = received;
    }

    private int addCLUT() {
        if (clutCounter == 0) {
            cluts = new DvbSubClut[1];
            cluts[0] = new DvbSubClut();
        } else {
            cluts = Arrays.copyOf(cluts, clutCounter + 1);
            cluts[clutCounter] = new DvbSubClut();
        }
        clutCounter++;
        return clutCounter - 1;
    }

    private int updateDisplay(DvbSubDisplay disp) {
        if (displays != null) {
            displays[0] = disp;
            return 0;
        }
        int index = addDisplay();
        displays[index] = disp;
        return 0;
    }

    private int addDisplay() {
        if (displayCounter == 0) {
            displays = new DvbSubDisplay[1];
            displays[0] = new DvbSubDisplay();
            displayCounter = 1;
        } else {
            displays = Arrays.copyOf(displays, displayCounter + 1);
            displayCounter++;
        }
        return displayCounter - 1; /* index of the created display */
    }

    private int addRegion() {
        if (regionCounter == 0) {
            regions = new DvbSubRegion[1];
            regions[0] = new DvbSubRegion();
        } else {
            regions = Arrays.copyOf(regions, regionCounter + 1);
            regions[regionCounter] = new DvbSubRegion();
        }
        regionCounter++;
        return regionCounter - 1; /* index of the created region */
    }

    private int checkAlreadyStoredRegion(DvbSubRegion received, int regionid) {
        if (regions != null) {
            for (int i = 0; i < regions.length; i++) {
                if (regions[i].id == received.id) {
                    regions[i] = received;
                    Log.d(TAG, "overwriting already stored region");
                    return regionMapping.get(received.id);
                }
            }
        }
        int index = addRegion();
        regions[index] = received;
        regionMapping.put(regionid, index);
        return index;
    }

    private int checkAlreadyStoredObject(DvbSubObjectDef received, int objectid) {
        if (objects != null) {
            for (int i = 0; i < objects.length; i++) {
                if (objects[i].id == received.id) {
                    objects[i] = received;
                    Log.d(TAG, "overwriting already created object");
                    return objectMapping.get(received.id);
                }
            }
        }
        int index = addObject();
        objects[index] = received;
        objectMapping.put(objectid, index);
        return index;
    }

    private int addObject() {
        if (objectCounter == 0) {
            objects = new DvbSubObjectDef[1];
            objects[0] = new DvbSubObjectDef();
        } else {
            objects = Arrays.copyOf(objects, objectCounter + 1);
            objects[objectCounter] = new DvbSubObjectDef();
        }
        objectCounter++;
        ;
        return objectCounter - 1; /* index of the created object */
    }

    public void parseSegments() {
        int segmentId, pageId, size;
        segmentId = tsStream.readBits(8);
        switch (segmentId) {
            case DVBSUB_ST_PAGE_COMPOSITION:
                DvbSubPage tempPage = new DvbSubPage();
                int pageid = parsePageSegment(tempPage);
                if (pageid != -1) {
                    checkAlreadyStoredPages(tempPage, pageid);
                }
                break;
            case DVBSUB_ST_CLUT_DEFINITION:
                DvbSubClut tempCLUT = new DvbSubClut();
                int clutId = parseCLUTSegment(tempCLUT);
                if (clutId != -1) {
                    checkAlreadyStoredCLUT(tempCLUT, clutId);
                }
                break;
            case DVBSUB_ST_DISPLAY_DEFINITION:
                DvbSubDisplay tempDisplay = new DvbSubDisplay();
                parseDDSegment(tempDisplay);
                updateDisplay(tempDisplay);
                break;
            case DVBSUB_ST_REGION_COMPOSITION:
                DvbSubRegion tempRegion = new DvbSubRegion();
                int regionid = parseRegionSegment(tempRegion);
                if (regionid != -1) {
                    checkAlreadyStoredRegion(tempRegion, regionid);
                }
                break;
            case DVBSUB_ST_OBJECT_DATA:
                DvbSubObjectDef tempObject = new DvbSubObjectDef();
                int objectId = parseObjectSegment(tempObject);
                if (objectId != -1) {
                    checkAlreadyStoredObject(tempObject, objectId);
                }
                break;
            case DVBSUB_ST_ENDOFDISPLAY:
                pageId = tsStream.readBits(16);
                size = tsStream.readBits(16);
                Log.d(TAG, "pageId " + pageId + "end of display size = " + size);
                tsStream.skipBits(size * 8);
                break;
            case DVBSUB_ST_STUFFING:
                pageId = tsStream.readBits(16);
                size = tsStream.readBits(16);
                Log.d(TAG, "pageId " + pageId + "stuffing size = " + size);
                tsStream.skipBits(size * 8);
                break;
            default:
                break;
        }
    }

    private int parsePageSegment(DvbSubPage page) {
        int processedBytes = 0;
        int pageid = tsStream.readBits(16);
        int segmentLength = tsStream.readBits(16);
        page.id = pageid;
        page.timeout = tsStream.readBits(8);
        processedBytes++;
        page.version = tsStream.readBits(4);
        page.state = tsStream.readBits(2);
        tsStream.skipBits(2);
        processedBytes++;
        int numOfBlocks = (segmentLength - processedBytes) / 6;
        if (numOfBlocks == 0)
            return -1;
        page.pRegionDefs = new DvbSubRegionDef[numOfBlocks];
        int count = 0;
        while (processedBytes < segmentLength) {
            page.pRegionDefs[count] = new DvbSubRegionDef();
            page.pRegionDefs[count].id = tsStream.readBits(8);
            tsStream.skipBits(8);
            page.pRegionDefs[count].x = tsStream.readBits(16);
            page.pRegionDefs[count].y = tsStream.readBits(16);
            count++;
            processedBytes += 6;
        }
        page.regionDefs = count;
        return page.id;
    }

    private int parseCLUTSegment(DvbSubClut clut) {
        int processedBytes = 0;
        int pageid = tsStream.readBits(16);
        int segmentLength = tsStream.readBits(16);
        int id = tsStream.readBits(8);
        int version = tsStream.readBits(4);

        clut.version = version;
        clut.id = id;
        tsStream.skipBits(4);

        processedBytes = 2;
        while (processedBytes < segmentLength) {
            byte y, cb, cr, t;
            int id2, type;

            id2 = tsStream.readBits(8);
            type = tsStream.readBits(3);
            tsStream.skipBits(4);
            if (tsStream.readBit()) {
                y = (byte) (tsStream.readBits(8));
                cr = (byte) (tsStream.readBits(8));
                cb = (byte) (tsStream.readBits(8));
                t = (byte) (tsStream.readBits(8));
                processedBytes += 6;
            } else {
                y = (byte) (tsStream.readBits(6) << 2);
                cr = (byte) (tsStream.readBits(4) << 4);
                cb = (byte) (tsStream.readBits(4) << 4);
                t = (byte) (tsStream.readBits(2) << 6);
                processedBytes += 4;
            }
            if (y == 0) {
                cr = 0;
                cb = 0;
                t = (byte) 0xff;
            }
            if (((type & 0x04) == 4) && id2 < 4) {
                clut.c2b[id2].Y = y;
                clut.c2b[id2].Cr = cr;
                clut.c2b[id2].Cb = cb;
                clut.c2b[id2].T = t;
            }
            if (((type & 0x02) == 2) && id2 < 16) {
                clut.c4b[id2].Y = y;
                clut.c4b[id2].Cr = cr;
                clut.c4b[id2].Cb = cb;
                clut.c4b[id2].T = t;
            }
            if ((type & 0x01) == 1) {
                clut.c8b[id2].Y = y;
                clut.c8b[id2].Cr = cr;
                clut.c8b[id2].Cb = cb;
                clut.c8b[id2].T = t;
            }
        }
        return clut.id;
    }

    private void parseDDSegment(DvbSubDisplay display) {
        int pageid = tsStream.readBits(16);
        int segmentLength = tsStream.readBits(16);
        display.version = tsStream.readBits(4);
        display.windowed = tsStream.readBits(1);
        tsStream.skipBits(3);
        display.width = tsStream.readBits(16);
        displayWidth = display.width;
        display.height = tsStream.readBits(16);
        displayHeight = display.height;
        if (display.windowed == 1) {
            display.x = tsStream.readBits(16);
            displayHorizontal = display.x;
            display.maxX = tsStream.readBits(16);
            display.y = tsStream.readBits(16);
            displayVertical = display.y;
            display.maxY = tsStream.readBits(16);
        }
        updateBitmapResolution();
    }

    private int parseRegionSegment(DvbSubRegion region) {
        region.pageId = tsStream.readBits(16);
        int segmentLength = tsStream.readBits(16);
        region.id = tsStream.readBits(8);
        region.version = tsStream.readBits(4);
        region.b_fill_region = tsStream.readBits(1);
        tsStream.skipBits(3);
        region.width = tsStream.readBits(16);
        region.height = tsStream.readBits(16);
        region.levelComp = tsStream.readBits(3);
        region.depth = tsStream.readBits(3);
        tsStream.skipBits(2);
        region.clut = tsStream.readBits(8);
        tsStream.skipBits(16);
        int count = 0;
        int processedBytes = 10;
        region.objectDefs = new DvbSubObjectDef[1];
        region.objectDefs[0] = new DvbSubObjectDef();
        count = 0;
        while (processedBytes < segmentLength) {
            if (count != 0) {
                region.objectDefs = Arrays.copyOf(region.objectDefs, count + 1);
            }
            region.objectDefs[count] = new DvbSubObjectDef();
            region.objectDefs[count].id = tsStream.readBits(16);
            region.objectDefs[count].type = tsStream.readBits(2);
            region.objectDefs[count].providerFlag = tsStream.readBits(2);
            region.objectDefs[count].x = tsStream.readBits(12);
            tsStream.skipBits(4);
            region.objectDefs[count].y = tsStream.readBits(12);
            processedBytes += 6;
            if (region.objectDefs[count].type == 0x01 || region.objectDefs[count].type == 0x02) {
                region.objectDefs[count].backgroundPC = tsStream.readBits(8);
                region.objectDefs[count].foregroundPC = tsStream.readBits(8);
                processedBytes += 2;
            }
            //Log.d(TAG, "Count regions = " + count + " x  = " + region.objectDefs[count].x
            //+ " y = " + region.objectDefs[count].y);
            count++;
        }
        region.numberOfObjectDefs = count;
        return region.id;
    }

    private DvbSubRegion findRegion(int objectId, int pageid) {
        int i;
        for (i = 0; i < pages.length; i++) {
            if (pages[i].id == pageid) {
                int j;
                for (j = 0; j < pages[i].regionDefs; j++) {
                    if (regions[j].findObject(objectId) != -1) {
                        break;
                    }
                }
                if (j < pages[i].regionDefs) {
                    return regions[j];
                } else {
                    return null;
                }
            }
        }
        return null;
    }

    private int parseObjectSegment(DvbSubObjectDef object) {
        TestVal testVal;

        int pageid = tsStream.readBits(16);
        int segmentLength = tsStream.readBits(16);
        object.id = tsStream.readBits(16);
        object.version = tsStream.readBits(4);
        object.codingMethod = tsStream.readBits(2);
        object.nonModifyingColor = tsStream.readBits(1);
        DvbSubRegion reg = findRegion(object.id, pageid);
        int x = previousCoordX, y = previousCoordY;
        if (reg != null) {
            int objectIdx = reg.findObject(object.id);
            int index = pageMapping.get(pageid);
            if (index != -1) {
                x = pages[index].pRegionDefs[reg.id].x + reg.objectDefs[objectIdx].x;
                y = pages[index].pRegionDefs[reg.id].y + reg.objectDefs[objectIdx].y;
            }
        }
        Point position = new Point(x, y);
        previousCoordX = x;
        previousCoordY = y;
        tsStream.skipBits(1);
        if (object.codingMethod == DVBSUB_OT_BASIC_CHAR || object.codingMethod == DVBSUB_OT_COMPOSITE_STRING) {
       /* not implemented yet */
        } else if (object.codingMethod == DVBSUB_OT_BASIC_BITMAP) {
            object.topFieldDataLength = tsStream.readBits(16);
            object.bottomFieldDataLength = tsStream.readBits(16);
            int processedBytes = 7;
            int totalCount = 0;
            int topLimitBytes = processedBytes + object.topFieldDataLength;
            int test = 0;
            int totalBytes = 0;
            while (processedBytes < topLimitBytes) {
                testVal = pixelDataSubBlock(object.id, position);
                processedBytes += testVal.bits / 8;
                totalBytes += testVal.bits / 8;
                if (testVal.count == 0) {
                    position.x = x;
                    position.y += 2;
                }
            }
            topLimitBytes = processedBytes + object.bottomFieldDataLength;
            totalBytes = 0;
            position.x = x;
            position.y = y;
            position.y++;
            while (processedBytes < topLimitBytes) {
                testVal = pixelDataSubBlock(object.id, position);
                processedBytes += testVal.bits / 8;
                totalBytes += testVal.bits / 8;
                if (testVal.count == 0) {
                    position.x = x;
                    position.y += 2;
                }
            }
//            if (!wordAligned()){
//                tsStream.skipBits(8); /* 8 '0' bits stuffing */
//            }
        }
        return object.id;
    }

    class TestVal {
        int count;
        int bits;
    }

    private TestVal pixelDataSubBlock(int objectId, Point objectOrigin) {
        TestVal testVal = new TestVal();
        int bitsRead = tsStream.getPosition();
        int dataType = tsStream.readBits(8);
        switch (dataType) {
            case DVBSUB_DT_2BP_CODE_STRING:
                testVal.count = dvbSub2BitPixelCodeString(objectOrigin);
                testVal.bits = tsStream.getPosition() - bitsRead;
                break;
            case DVBSUB_DT_4BP_CODE_STRING:
                testVal.count = dvbSub4BitPixelCodeString(objectOrigin);
                testVal.bits = tsStream.getPosition() - bitsRead;
                break;
            case DVBSUB_DT_8BP_CODE_STRING:
                testVal.count = dvbSub8BitPixelCodeString(objectOrigin);
                testVal.bits = tsStream.getPosition() - bitsRead;
                break;
            case DVBSUB_DT_24_TABLE_DATA:
            case DVBSUB_DT_28_TABLE_DATA:
            case DVBSUB_DT_48_TABLE_DATA:
                break;
            case DVBSUB_DT_END_LINE:
                testVal.count = 0;
                testVal.bits = tsStream.getPosition() - bitsRead;
                break;
        }
        return testVal;
    }

    private int dvbSub2BitPixelCodeString(Point objectOrigin) {
        int switchVal;
        boolean stop = false;
        int initialX = objectOrigin.x;
        while (!stop) {
            int count = 0;
            int color = tsStream.readBits(2);
            if (color != 0x00) {
                count = 1;
            } else {
                switchVal = tsStream.readBits(1);
                if (switchVal == 1) {
                    count = 3 + tsStream.readBits(3);
                    color = tsStream.readBits(2);
                } else {
                    switchVal = tsStream.readBits(1);
                    if (switchVal == 0) {
                        switchVal = tsStream.readBits(2);
                        switch (switchVal) {
                            case 0x00:
                                stop = true;
                                break;
                            case 0x01:
                                count = 2;
                                break;
                            case 0x02:
                                count = 12 + tsStream.readBits(4);
                                color = tsStream.readBits(2);
                                break;
                            case 0x03:
                                count = 29 + tsStream.readBits(8);
                                color = tsStream.readBits(2);
                                break;
                            default:
                                break;
                        }
                    } else {
                        count = 1;
                    }
                }
            }
            if (count == 0)
                continue;
            paintInCanvas(objectOrigin, count, color, 2);
            objectOrigin.x += count;
        }
        return objectOrigin.x - initialX;
    }

    private int dvbSub4BitPixelCodeString(Point objectOrigin) {
        int switchVal;
        boolean stop = false;
        int initialX = objectOrigin.x;
        while (!stop) {
            int count = 0;
            int color = tsStream.readBits(4);
            if (color != 0) {
                count = 1;
            } else {
                if (tsStream.readBits(1) == 0x00) {
                    switchVal = tsStream.readBits(3);
                    if (switchVal != 0x00) {
                        count = 2 + switchVal;
                    } else {
                        stop = true;
                    }
                } else {
                    if (tsStream.readBits(1) == 0x00) {
                        count = 4 + tsStream.readBits(2);
                        color = tsStream.readBits(4);
                    } else {
                        switch (tsStream.readBits(2)) {
                            case 0x00:
                                count = 1;
                                break;
                            case 0x01:
                                count = 2;
                                break;
                            case 0x02:
                                count = 9 + tsStream.readBits(4);
                                color = tsStream.readBits(4);
                                break;
                            case 0x03:
                                count = 25 + tsStream.readBits(8);
                                color = tsStream.readBits(4);
                                break;
                        }
                    }
                }
            }
            if (count == 0)
                continue;
            paintInCanvas(objectOrigin, count, color, 4);
            objectOrigin.x += count;
        }
        alignTSStream();
        return objectOrigin.x - initialX;
    }

    private int dvbSub8BitPixelCodeString(Point objectOrigin) {
        boolean stop = false;
        int initialX = objectOrigin.x;
        int switchVal;
        while (!stop) {
            int count = 0;
            int color = tsStream.readBits(8);
            if (color != 0) {
                count = 1;
            } else {
                if (tsStream.readBits(1) == 0x00) {
                    switchVal = tsStream.readBits(7);
                    if (switchVal != 0x00) {
                        count = switchVal;
                    } else {
                        stop = true;
                    }
                } else {
                    count = tsStream.readBits(7);
                    color = tsStream.readBits(8);
                }
            }
            if (count == 0)
                continue;
            paintInCanvas(objectOrigin, count, color, 8);
            objectOrigin.x += count;
        }
        return objectOrigin.x - initialX;
    }


    public class Point {
        int x;
        int y;

        public Point(int m, int n) {
            x = m;
            y = n;
        }
    }

    public class RGBColor {
        int r, g, b, a;
    }

    public RGBColor yuvToRGB(byte y, byte cb, byte cr, byte t) {
        RGBColor color = new RGBColor();
        color.r = clip(((298 * (y) + 409 * (cr + 128) + 128) >> 8) & 0xff);
        color.g = clip(((298 * (y) - 100 * (cb + 128) - 208 * (cr + 128) + 128) >> 8) & 0xff);
        color.b = clip(((298 * (y) + 516 * (cb + 128) + 128) >> 8) & 0xff);
        color.a = 0xff - (t & 0xff);
        return color;
    }

    public int clip(int val) {
        if (val < 0) {
            return 0;
        } else if (val > 255) {
            return 255;
        } else {
            return (val);
        }
    }

    private void paintInCanvas(Point point, int count, int color, int bitDepth) {
        RGBColor rgb = null;
        switch (bitDepth) {
            case 2:
                rgb = yuvToRGB(cluts[0].c2b[color].Y, cluts[0].c2b[color].Cb, cluts[0].c2b[color].Cr, cluts[0].c2b[color].T);
                break;
            case 4:
                rgb = yuvToRGB(cluts[0].c4b[color].Y, cluts[0].c4b[color].Cb, cluts[0].c4b[color].Cr, cluts[0].c4b[color].T);
                break;
            case 8:
                rgb = yuvToRGB(cluts[0].c8b[color].Y, cluts[0].c8b[color].Cb, cluts[0].c8b[color].Cr, cluts[0].c8b[color].T);
                break;
        }
        if (rgb == null)
            return;
        graphicsPaintObject.setARGB(rgb.a, rgb.r, rgb.g, rgb.b);
        graphicsCanvasObject.drawLine(point.x, point.y, point.x + count, point.y, graphicsPaintObject);
        newGraphicData = true;
    }

    private int alignTSStream() {
        int skipBits = 8 - (tsStream.getPosition() % 8);
        if (skipBits != 8) {
            tsStream.skipBits(skipBits);
            return skipBits;
        }
        return 0;
    }

    private boolean wordAligned() {
        int bytePos = (tsStream.getPosition() - 1) / 8;
        if (bytePos % 2 != 0) {
            return false;
        } else {
            return true;
        }
    }

    public DvbSubtitlesParser(Bitmap bitmap) {
        dataCount = 0;
        offset = 0;
        displayCounter = 0;
        clutCounter = 0;
        objectCounter = 0;
        regionCounter = 0;
        pageCounter = 0;
        numTSPackets = 0;
        currentBitmap = 0;
        pageMapping = new HashMap<Integer, Integer>(DVBSUB_MAX_OBJECTS_STORED);
        regionMapping = new HashMap<Integer, Integer>(DVBSUB_MAX_OBJECTS_STORED);
        objectMapping = new HashMap<Integer, Integer>(DVBSUB_MAX_OBJECTS_STORED);
        graphicsPaintObject = new Paint();
//   rendererState = new DvbSubsRendererState();
        subsBitmap = new Bitmap[2];
        displayWidth = 720;
        displayHeight = 576;
        updateBitmapResolution();
        graphicsCanvasObject = new Canvas(subsBitmap[currentBitmap]);
        newGraphicData = false;
    }

    public boolean canParse(String mimeType) {
        return mimeType.equals(MimeTypes.APPLICATION_DVBSUBS);
    }

    public Bitmap dvbSubsDecode(byte[] input, int inputSize) {
        if (input != null) {
            tsStream = new ParsableBitArray(input);
        } else {
            return null;
        }
        if (tsStream.readBits(8) != 0x20) {
            return null;
        }
        if (tsStream.readBits(8) != 0x00) {
            return null;
        }
        int sync = tsStream.readBits(8);
        while (sync == 0x0f || (sync == 0x00 && tsStream.readBits(8) == 0x0f)) {
            parseSegments();
            sync = tsStream.readBits(8);
        }
        if (sync == 0xff) {
            //end of subtitle
            if (newGraphicData) {
                Log.d(TAG, "Reached end of subtitle");
                newGraphicData = false;
                Bitmap bitmap = subsBitmap[currentBitmap];
                currentBitmap = (currentBitmap + 1) % 2;
                graphicsCanvasObject = new Canvas(subsBitmap[currentBitmap]);
                graphicsCanvasObject.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                return bitmap;
            } else {
                Log.d(TAG, "Reached end of subtitle but no newGraphicData");
                return null;
            }
        }
        return null;
    }

    private void updateBitmapResolution() {
        for (int i = 0; i < NUM_BITMAPS; i++) {
            subsBitmap[i] = Bitmap.createBitmap(displayWidth, displayHeight, Bitmap.Config.ARGB_8888);
        }
    }
}
