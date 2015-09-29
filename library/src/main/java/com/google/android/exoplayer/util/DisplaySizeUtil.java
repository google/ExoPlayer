package com.google.android.exoplayer.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Point;
import android.view.Display;
import android.view.WindowManager;

public class DisplaySizeUtil {

    /**
     * Returns a Point containing the Display size as detected using different work-arounds the limitations of ANdroid to detect the display size.
     *
     * @param c Context to get System Services from
     *
     * @return Display size (best available option)
     */
    public static Point getDisplaySize(Context c) {
        WindowManager windowManager = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Point displaySize = new Point();
        /**
         * Works on models like the 'BRAVIA 4K 2015'.
         * This fix is not ideal, but it is the recommended approach according to Sony TV documentation.
         */
        if(Util.SDK_INT < 23 && android.os.Build.MODEL.contains("4K")){
            displaySize.x = 3840;
            displaySize.y = 2160;
            return displaySize;
        }
        if (Util.SDK_INT >= 17) {
            getDisplaySizeV17(display, displaySize);
        } else if (Util.SDK_INT >= 16) {
            getDisplaySizeV16(display, displaySize);
        } else {
            getDisplaySizeV9(display, displaySize);
        }
        return displaySize;
    }

    @TargetApi(17)
    private static void getDisplaySizeV17(Display display, Point outSize) {
        display.getRealSize(outSize);
    }

    @TargetApi(16)
    private static void getDisplaySizeV16(Display display, Point outSize) {
        display.getSize(outSize);
    }

    @SuppressWarnings("deprecation")
    private static void getDisplaySizeV9(Display display, Point outSize) {
        outSize.x = display.getWidth();
        outSize.y = display.getHeight();
    }
}
