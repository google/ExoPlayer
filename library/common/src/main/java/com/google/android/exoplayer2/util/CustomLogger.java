package com.google.android.exoplayer2.util;

/**
 * Interface to be implemented by a custom logger if logging required is other than simple {@link android.util.Log}
 * <p>
 * {@link Log.setCustomLogger(CustomLogger)} should be used to provide the implementation
 */
public interface CustomLogger {
    /**
     * Analogous to {@link android.util.Log#d(String, String)}
     *
     * @see android.util.Log#d(String, String)
     */
    void d(String tag, String message);

    /**
     * Analogous to {@link android.util.Log#i(String, String)}
     *
     * @see android.util.Log#i(String, String)
     */
    void i(String tag, String message);

    /**
     * Analogous to {@link android.util.Log#w(String, String)}
     *
     * @see android.util.Log#w(String, String)
     */
    void w(String tag, String message);

    /**
     * Analogous to {@link android.util.Log#e(String, String)}
     *
     * @see android.util.Log#e(String, String)
     */
    void e(String tag, String message);
}
