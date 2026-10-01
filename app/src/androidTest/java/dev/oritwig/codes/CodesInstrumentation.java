package dev.oritwig.codes;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** On-device native checks, separate from the real system-picker UI acceptance. */
public final class CodesInstrumentation extends Instrumentation {
  private int passed;
  private final StringBuilder log = new StringBuilder();

  @Override
  public void onCreate(Bundle arguments) {
    super.onCreate(arguments);
    start();
  }

  private void check(String label, boolean condition) {
    if (!condition) throw new AssertionError(label);
    passed++;
    log.append("PASS ").append(label).append('\n');
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      Context context = getTargetContext();
      String text = "https://example.org/codes?message=caf%C3%A9";
      Bitmap qr = BitmapBridge.qr(text);
      check("QR output dimensions", qr.getWidth() == 1024 && qr.getHeight() == 1024);
      byte[] bytes = BitmapBridge.png(qr);
      check(
          "PNG signature",
          bytes[0] == (byte) 137 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G');
      Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
      check("Encoded PNG decodes exactly", BitmapBridge.decode(decoded).getText().equals(text));
      decoded.recycle();
      File png = new File(context.getFilesDir(), "roundtrip.png");
      try (FileOutputStream out = new FileOutputStream(png)) {
        out.write(bytes);
      }
      Bitmap imported = BitmapBridge.read(context.getContentResolver(), Uri.fromFile(png));
      check(
          "Android stream import roundtrip", BitmapBridge.decode(imported).getText().equals(text));
      imported.recycle();
      check(
          "UTF-8 pixel roundtrip",
          BitmapBridge.decode(BitmapBridge.qr("你好 · café 🍃")).getText().equals("你好 · café 🍃"));
      try {
        BitmapBridge.readLimited(
            new ByteArrayInputStream(new byte[BitmapBridge.MAX_FILE_BYTES + 1]));
        throw new AssertionError("byte bound absent");
      } catch (IOException expected) {
        check("Oversize byte stream rejected", expected.getMessage().contains("20 MB"));
      }
      File malformed = new File(context.getFilesDir(), "malformed.png");
      try (FileOutputStream out = new FileOutputStream(malformed)) {
        out.write("not an image".getBytes(StandardCharsets.UTF_8));
      }
      try {
        BitmapBridge.read(context.getContentResolver(), Uri.fromFile(malformed));
        throw new AssertionError("malformed accepted");
      } catch (IOException expected) {
        check("Malformed image rejected", expected.getMessage().contains("valid"));
      }
      File oversized = new File(context.getFilesDir(), "oversize.png");
      try (InputStream in = getContext().getAssets().open("oversize.png");
          FileOutputStream out = new FileOutputStream(oversized)) {
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) out.write(b, 0, n);
      }
      try {
        BitmapBridge.read(context.getContentResolver(), Uri.fromFile(oversized));
        throw new AssertionError("dimensions accepted");
      } catch (IOException expected) {
        check(
            "72 MP source rejected before pixel allocation",
            expected.getMessage().contains("64 megapixels"));
      }
      // Use the real ZXing writer for a vertical 1D fixture, then Android rotation adapter.
      BitMatrix m = new MultiFormatWriter().encode("NATIVE-123", BarcodeFormat.CODE_128, 900, 180);
      int[] p = new int[900 * 180];
      for (int y = 0; y < 180; y++)
        for (int x = 0; x < 900; x++) p[y * 900 + x] = m.get(x, y) ? 0xff000000 : 0xffffffff;
      Bitmap horizontal = Bitmap.createBitmap(p, 900, 180, Bitmap.Config.ARGB_8888);
      android.graphics.Matrix rotate = new android.graphics.Matrix();
      rotate.postRotate(90);
      Bitmap vertical = Bitmap.createBitmap(horizontal, 0, 0, 900, 180, rotate, false);
      check(
          "Vertical Code 128 decoded by rotation adapter",
          BitmapBridge.decode(vertical).getText().equals("NATIVE-123"));
      horizontal.recycle();
      vertical.recycle();
      qr.recycle();
      MainActivity activity =
          (MainActivity)
              startActivitySync(
                  new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      waitForIdleSync();
      runOnMainSync(
          () -> {
            check(
                "Read tab first",
                activity.getWindow().getDecorView().findViewWithTag("chooseImage").isShown());
            activity.getWindow().getDecorView().findViewWithTag("createTab").performClick();
            EditText input = activity.getWindow().getDecorView().findViewWithTag("payloadInput");
            input.setText("https://example.org/oritwig-codes");
            activity.getWindow().getDecorView().findViewWithTag("previewQr").performClick();
          });
      waitReady(activity);
      runOnMainSync(
          () -> {
            Button save = activity.getWindow().getDecorView().findViewWithTag("savePng");
            ImageView preview = activity.getWindow().getDecorView().findViewWithTag("qrPreview");
            check("Preview visible after real UI action", preview.isShown());
            check("PNG export enabled after generation", save.isEnabled());
            EditText input = activity.getWindow().getDecorView().findViewWithTag("payloadInput");
            input.setText("Changed payload");
            check("Editing invalidates old PNG export", !save.isEnabled());
            check("Editing hides stale preview", preview.getVisibility() == View.GONE);
            input.setText(" ");
            activity.getWindow().getDecorView().findViewWithTag("previewQr").performClick();
          });
      waitReady(activity);
      runOnMainSync(
          () -> {
            TextView status = activity.getWindow().getDecorView().findViewWithTag("status");
            check(
                "Blank text error visible",
                status.getText().toString().contains("Enter some text"));
            check(
                "Blank text cannot export",
                !activity.getWindow().getDecorView().findViewWithTag("savePng").isEnabled());
            ((EditText) activity.getWindow().getDecorView().findViewWithTag("payloadInput"))
                .setText("Oritwig Codes • all local");
            activity.getWindow().getDecorView().findViewWithTag("previewQr").performClick();
          });
      waitReady(activity);
      check(
          "No internet permission",
          context.checkSelfPermission("android.permission.INTERNET")
              == android.content.pm.PackageManager.PERMISSION_DENIED);
      check(
          "No camera permission",
          context.checkSelfPermission("android.permission.CAMERA")
              == android.content.pm.PackageManager.PERMISSION_DENIED);
      java.util.concurrent.atomic.AtomicReference<MainActivity> recreated =
          new java.util.concurrent.atomic.AtomicReference<>();
      android.app.Application.ActivityLifecycleCallbacks callbacks =
          new android.app.Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity a, Bundle b) {}

            public void onActivityStarted(Activity a) {}

            public void onActivityResumed(Activity a) {
              if (a instanceof MainActivity && a != activity) recreated.set((MainActivity) a);
            }

            public void onActivityPaused(Activity a) {}

            public void onActivityStopped(Activity a) {}

            public void onActivitySaveInstanceState(Activity a, Bundle b) {}

            public void onActivityDestroyed(Activity a) {}
          };
      runOnMainSync(
          () -> {
            activity.getApplication().registerActivityLifecycleCallbacks(callbacks);
            activity.getWindow().getDecorView().findViewWithTag("readTab").performClick();
            activity
                .getPreferences(Context.MODE_PRIVATE)
                .edit()
                .putString("theme", "Dark")
                .commit();
            activity.recreate();
          });
      long recreationDeadline = SystemClock.uptimeMillis() + 30000;
      while (recreated.get() == null && SystemClock.uptimeMillis() < recreationDeadline)
        SystemClock.sleep(50);
      if (recreated.get() == null) throw new AssertionError("Activity recreation timed out");
      MainActivity fresh = recreated.get();
      waitReady(fresh);
      runOnMainSync(
          () -> {
            fresh.getApplication().unregisterActivityLifecycleCallbacks(callbacks);
            check(
                "Reader mode survives recreation",
                fresh.getWindow().getDecorView().findViewWithTag("chooseImage").isShown());
            check(
                "Draft text survives recreation",
                ((EditText) fresh.getWindow().getDecorView().findViewWithTag("payloadInput"))
                    .getText()
                    .toString()
                    .equals("Oritwig Codes • all local"));
            String readerHint =
                ((TextView) fresh.getWindow().getDecorView().findViewWithTag("status"))
                    .getText()
                    .toString();
            check(
                "Background QR restoration keeps reader hint",
                readerHint.equals("All processing stays on this device.")
                    || readerHint.equals("Code found. Review the text before copying or sharing."));
            int lightFlags =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            check(
                "Dark appearance clears light system-bar flags",
                (fresh.getWindow().getDecorView().getSystemUiVisibility() & lightFlags) == 0);
          });
      File artifact = new File(context.getExternalFilesDir(null), "native-roundtrip.png");
      try (FileOutputStream out = new FileOutputStream(artifact)) {
        out.write(bytes);
      }
      result.putString("stream", log.toString());
      result.putInt("passed", passed);
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString("stream", log + "FAIL " + android.util.Log.getStackTraceString(e));
      result.putInt("passed", passed);
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  private void waitReady(MainActivity activity) {
    long deadline = SystemClock.uptimeMillis() + 20000;
    boolean[] enabled = {false};
    do {
      waitForIdleSync();
      runOnMainSync(
          () ->
              enabled[0] =
                  activity.getWindow().getDecorView().findViewWithTag("previewQr").isEnabled());
      if (enabled[0]) return;
      SystemClock.sleep(50);
    } while (SystemClock.uptimeMillis() < deadline);
    throw new AssertionError("UI worker timed out");
  }
}
