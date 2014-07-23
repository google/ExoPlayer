package com.google.android.exoplayer.upstream;

import android.net.Uri;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

public final class AESDataSource implements DataSource {
    private Cipher cipher;
    private DataSource underlyingDataSource;
    private String userAgent;
    private byte key[];

    public AESDataSource(String userAgent, DataSource underlyingDataSource){
        this.underlyingDataSource = underlyingDataSource;
        this.userAgent = userAgent;
    }


    @Override
    public long open(DataSpec dataSpec) throws IOException {
        DataSpec underlyingDataSpec = null;

        if (dataSpec.uri.getScheme().equals("aes")) {
            try {
                cipher = Cipher.getInstance("AES/CBC/PCKS7Padding");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            }

            String keyUrl = dataSpec.uri.getQueryParameter("key");
            String dataUrl = dataSpec.uri.getQueryParameter("data");
            Uri keyUri = Uri.parse(keyUrl);

            DataSource keyDataSource;

            if (keyUri.getScheme().equals("file")) {
                keyDataSource = new FileDataSource();
            } else {
                keyDataSource = new HttpDataSource(userAgent, HttpDataSource.REJECT_PAYWALL_TYPES);
            }

            DataSpec keyDataSpec = new DataSpec(keyUri, 0, DataSpec.LENGTH_UNBOUNDED, null);
            keyDataSource.open(keyDataSpec);
            key = new byte[16];
            int bytesRead = 0;
            while (bytesRead < 16) {
                int ret = keyDataSource.read(key, bytesRead, 16 - bytesRead);
                if (ret <= 0) {
                    throw new IOException("cannot read key");
                }
            }
            keyDataSource.close();

            underlyingDataSpec = new DataSpec(Uri.parse(dataUrl), dataSpec.absoluteStreamPosition, dataSpec.length, dataSpec.key);
        } else {
            underlyingDataSpec = dataSpec;
        }

        return underlyingDataSource.open(underlyingDataSpec);
    }

    @Override
    public void close() throws IOException {
        underlyingDataSource.close();
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
//       cipher

        return 0;
    }
}
