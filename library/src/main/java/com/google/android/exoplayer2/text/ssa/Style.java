package com.google.android.exoplayer2.text.ssa;

import java.util.Map;

/**
 * Created by cablej01 on 27/12/2016.
 */

public class Style {
    private String name;
    private String fontName;
    private int fontSize;
    private int primaryColour, secondaryColour, outlineColour, backColour;
    private boolean bold, italic, underline, strikeOut;
    private int scaleX, scaleY, spacing, angle;
    private int borderStyle;
    private int outline, shadow, alignment, marginL, marginR, marginV;
    private int alphaLevel=0;
    private int encoding;

    public Style() {

    }

    public Style(Map<String,String> init) {
        name = init.get("name");
        fontName = init.get("fontname");
        fontSize = Integer.parseInt(init.get("fontsize"));
        primaryColour = parseColour(init.get("primarycolour"));
        secondaryColour = parseColour(init.get("secondarycolour"));
        outlineColour = parseColour(init.get("outlinecolour"));
        backColour = parseColour(init.get("backcolour"));
        bold = init.get("bold").equals("0")?false:true;
        italic = init.get("italic").equals("0")?false:true;
        underline = init.get("underline").equals("0")?false:true;
        strikeOut = init.get("strikeout").equals("0")?false:true;
        scaleX = Integer.parseInt(init.get("scalex"));
        scaleY = Integer.parseInt(init.get("scaley"));
        spacing = Integer.parseInt(init.get("spacing"));
        angle = Integer.parseInt(init.get("angle"));
        borderStyle = Integer.parseInt(init.get("borderstyle"));
        outline = Integer.parseInt(init.get("outline"));
        shadow = Integer.parseInt(init.get("shadow"));
        alignment = Integer.parseInt(init.get("alignment"));
        marginL = Integer.parseInt(init.get("marginl"));
        marginR = Integer.parseInt(init.get("marginr"));
        marginV = Integer.parseInt(init.get("marginv"));
        if(init.containsKey("alphalevel"))
            alphaLevel= Integer.parseInt(init.get("alphalevel"));
        encoding = Integer.parseInt(init.get("encoding"));
    }
    
    public Style(Style aStyle) {
        name = aStyle.name;
        fontName = aStyle.fontName;
        fontSize = aStyle.fontSize;
        primaryColour = aStyle.primaryColour;
        secondaryColour = aStyle.secondaryColour;
        outlineColour = aStyle.outlineColour;
        backColour = aStyle.backColour;
        bold = aStyle.bold;
        italic = aStyle.italic;
        underline = aStyle.underline;
        strikeOut = aStyle.strikeOut;
        scaleX = aStyle.scaleX;
        scaleY = aStyle.scaleY;
        spacing = aStyle.spacing;
        angle = aStyle.angle;
        borderStyle = aStyle.borderStyle;
        outline = aStyle.outline;
        shadow = aStyle.shadow;
        alignment = aStyle.alignment;
        marginL = aStyle.marginL;
        marginR = aStyle.marginR;
        marginV = aStyle.marginV;
        alphaLevel= aStyle.alphaLevel;
        encoding = aStyle.encoding;
    }

    public static int parseColour(String val) {
        return Integer.parseInt(val.substring(2), 16);
    }

    public static String formatColour(int val) {
        return String.format("&H%06X", val);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFontName() {
        return fontName;
    }

    public void setFontName(String fontName) {
        this.fontName = fontName;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    public int getPrimaryColour() {
        return primaryColour;
    }

    public void setPrimaryColour(int primaryColour) {
        this.primaryColour = primaryColour;
    }

    public int getSecondaryColour() {
        return secondaryColour;
    }

    public void setSecondaryColour(int secondaryColour) {
        this.secondaryColour = secondaryColour;
    }

    public int getOutlineColour() {
        return outlineColour;
    }

    public void setOutlineColour(int outlineColour) {
        this.outlineColour = outlineColour;
    }

    public int getBackColour() {
        return backColour;
    }

    public void setBackColour(int backColour) {
        this.backColour = backColour;
    }

    public boolean isBold() {
        return bold;
    }

    public void setBold(boolean bold) {
        this.bold = bold;
    }

    public boolean isItalic() {
        return italic;
    }

    public void setItalic(boolean italic) {
        this.italic = italic;
    }

    public boolean isUnderline() {
        return underline;
    }

    public void setUnderline(boolean underline) {
        this.underline = underline;
    }

    public boolean isStrikeOut() {
        return strikeOut;
    }

    public void setStrikeOut(boolean strikeOut) {
        this.strikeOut = strikeOut;
    }

    public int getScaleX() {
        return scaleX;
    }

    public void setScaleX(int scaleX) {
        this.scaleX = scaleX;
    }

    public int getScaleY() {
        return scaleY;
    }

    public void setScaleY(int scaleY) {
        this.scaleY = scaleY;
    }

    public int getSpacing() {
        return spacing;
    }

    public void setSpacing(int spacing) {
        this.spacing = spacing;
    }

    public int getAngle() {
        return angle;
    }

    public void setAngle(int angle) {
        this.angle = angle;
    }

    public int getBorderStyle() {
        return borderStyle;
    }

    public void setBorderStyle(int borderStyle) {
        this.borderStyle = borderStyle;
    }

    public int getOutline() {
        return outline;
    }

    public void setOutline(int outline) {
        this.outline = outline;
    }

    public int getShadow() {
        return shadow;
    }

    public void setShadow(int shadow) {
        this.shadow = shadow;
    }

    public int getAlignment() {
        return alignment;
    }

    public void setAlignment(int alignment) {
        this.alignment = alignment;
    }

    public int getMarginL() {
        return marginL;
    }

    public void setMarginL(int marginL) {
        this.marginL = marginL;
    }

    public int getMarginR() {
        return marginR;
    }

    public void setMarginR(int marginR) {
        this.marginR = marginR;
    }

    public int getMarginV() {
        return marginV;
    }

    public void setMarginV(int marginV) {
        this.marginV = marginV;
    }

    public int getAlphaLevel() {
        return alphaLevel;
    }

    public void setAlphaLevel(int alphaLevel) {
        this.alphaLevel = alphaLevel;
    }

    public int getEncoding() {
        return encoding;
    }

    public void setEncoding(int encoding) {
        this.encoding = encoding;
    }
}
