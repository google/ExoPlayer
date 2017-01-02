package com.google.android.exoplayer2.text.ssa;

import com.google.android.exoplayer2.text.Cue;

/**
 * Created by cablej01 on 02/01/2017.
 */

public class SSACue extends Cue {
    private Style style = null;
    private int layer;
    private String effect;
    private String richText = null;

    public SSACue(String text) {
        this(text, null, 0, null);
    }

    public SSACue(String text, Style style, int layer, String effect) {
        super(text.replaceAll("\\{[^{]*\\}", ""));
        this.richText = text;
        this.layer = layer;
        this.effect = effect;
        this.style = style;
        // TODO map SSA fields to superclass fields
    }

    public Style getStyle() {
        return style;
    }

    public int getLayer() {
        return layer;
    }

    public String getEffect() {
        return effect;
    }

    public String getRichText() {
        return richText;
    }
}
