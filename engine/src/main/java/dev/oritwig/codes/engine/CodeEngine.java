// SPDX-License-Identifier: Apache-2.0
package dev.oritwig.codes.engine;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.client.result.ResultParser;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/** Thin adapter. ZXing owns detection, decoding, parsing, QR layout and error correction. */
public final class CodeEngine {
  public static final int MAX_TEXT_BYTES = 1200;

  private CodeEngine() {}

  public static BitMatrix encodeQr(String text, int size) throws WriterException {
    if (text == null || text.trim().isEmpty())
      throw new IllegalArgumentException("Enter some text first.");
    if (text.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
      throw new IllegalArgumentException(
          "Use no more than 1,200 UTF-8 bytes. Shorter text makes an easier-to-scan QR code.");
    }
    if (size < 256 || size > 2048)
      throw new IllegalArgumentException("QR image size must be between 256 and 2,048 pixels.");
    Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
    hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
    hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
    hints.put(EncodeHintType.MARGIN, 4);
    return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
  }

  public static Result decode(int[] pixels, int width, int height) throws NotFoundException {
    if (width <= 0
        || height <= 0
        || (long) width * height > 4_194_304L
        || pixels == null
        || pixels.length != width * height) {
      throw new IllegalArgumentException("Image must contain at most 4 megapixels after sampling.");
    }
    RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
    Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
    hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    MultiFormatReader reader = new MultiFormatReader();
    try {
      return reader.decode(new BinaryBitmap(new HybridBinarizer(source)), hints);
    } catch (NotFoundException notFound) {
      return reader.decode(new BinaryBitmap(new HybridBinarizer(source.invert())), hints);
    } finally {
      reader.reset();
    }
  }

  public static String type(Result result) {
    return ResultParser.parseResult(result).getType().toString();
  }
}
