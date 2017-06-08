package com.google.android.exoplayer2.util;


public final class HLSEncryptInfo {

    public String encryptionMethod;
    public String encryptionKeyUri;
    public String encryptionIvString;

    public String encryptionKeyId;
    public String encryptionKeyFormat;

    public String encryptionKeyString;

    public byte[] encryptionIv;
    public byte[] encryptionKey;
    public boolean isEncrypted;

    public HLSEncryptInfo() {

    }

    public HLSEncryptInfo(boolean isEncrypted, String encryptionMethod, String encryptionKeyUri, String encryptionKeyIv) {
        this.isEncrypted = isEncrypted;
        this.encryptionMethod = encryptionMethod;
        this.encryptionKeyUri = encryptionKeyUri;
        this.encryptionIvString = encryptionKeyIv;
    }

    public HLSEncryptInfo(boolean isEncrypted, String encryptionMethod, String encryptionKeyUri, String encryptionKeyIv, String encryptionKeyFormat, String encryptionKeyId) {
        this(isEncrypted, encryptionMethod, encryptionKeyUri, encryptionKeyIv);
        this.encryptionKeyFormat = encryptionKeyFormat;
        this.encryptionKeyId = encryptionKeyId;
    }

}
