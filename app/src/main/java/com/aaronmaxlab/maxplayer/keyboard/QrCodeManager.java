package com.aaronmaxlab.maxplayer.keyboard;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.HashMap;
import java.util.Map;

public class QrCodeManager {

    public static Bitmap generateQrCode(String content, int size)
            throws WriterException {

        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);

        BitMatrix matrix = new MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
        );

        Bitmap bitmap = Bitmap.createBitmap(
                size,
                size,
                Bitmap.Config.RGB_565
        );

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                bitmap.setPixel(
                        x,
                        y,
                        matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF
                );
            }
        }

        return bitmap;
    }
}