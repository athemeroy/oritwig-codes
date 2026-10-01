// SPDX-License-Identifier: Apache-2.0
package dev.oritwig.codes;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import dev.oritwig.codes.engine.CodeEngine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Android pixel/stream boundary only; no barcode algorithms. */
final class BitmapBridge {
  static final int MAX_FILE_BYTES = 20 * 1024 * 1024;
  static final long MAX_SOURCE_PIXELS = 64_000_000;

  private BitmapBridge() {}

  static Bitmap read(ContentResolver resolver, Uri uri) throws IOException {
    byte[] bytes;
    try (InputStream stream = resolver.openInputStream(uri)) {
      if (stream == null) throw new IOException("This image could not be opened.");
      bytes = readLimited(stream);
    }
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
      throw new IOException("Choose a valid PNG, JPEG, WebP or other supported image.");
    if ((long) bounds.outWidth * bounds.outHeight > MAX_SOURCE_PIXELS)
      throw new IOException("This image is too large. Choose an image under 64 megapixels.");
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = 1;
    while ((bounds.outWidth + options.inSampleSize - 1) / options.inSampleSize > 2048
        || (bounds.outHeight + options.inSampleSize - 1) / options.inSampleSize > 2048)
      options.inSampleSize *= 2;
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    if (bitmap == null) throw new IOException("This image could not be decoded.");
    return bitmap;
  }

  static byte[] readLimited(InputStream stream) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int count;
    while ((count = stream.read(buffer)) != -1) {
      if (Thread.currentThread().isInterrupted()) throw new IOException("Canceled.");
      if (output.size() > MAX_FILE_BYTES - count)
        throw new IOException("This file is too large. Choose an image under 20 MB.");
      output.write(buffer, 0, count);
    }
    return output.toByteArray();
  }

  static Result decode(Bitmap bitmap) throws NotFoundException {
    try {
      return decodePixels(bitmap);
    } catch (NotFoundException miss) {
      // Platform rotation supports vertical 1D barcodes; ZXing still performs detection.
      Matrix matrix = new Matrix();
      matrix.postRotate(90);
      Bitmap rotated =
          Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
      try {
        return decodePixels(rotated);
      } finally {
        if (rotated != bitmap) rotated.recycle();
      }
    }
  }

  static Result decodePixels(Bitmap bitmap) throws NotFoundException {
    int width = bitmap.getWidth(), height = bitmap.getHeight();
    int[] pixels = new int[width * height];
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
    return CodeEngine.decode(pixels, width, height);
  }

  static Bitmap qr(String text) throws WriterException {
    BitMatrix matrix = CodeEngine.encodeQr(text, 1024);
    int width = matrix.getWidth(), height = matrix.getHeight();
    int[] pixels = new int[width * height];
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++)
        pixels[y * width + x] = matrix.get(x, y) ? 0xff000000 : 0xffffffff;
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
  }

  static byte[] png(Bitmap bitmap) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes))
      throw new IOException("PNG encoding failed.");
    return bytes.toByteArray();
  }
}
