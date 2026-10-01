// SPDX-License-Identifier: Apache-2.0
package dev.oritwig.codes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import dev.oritwig.codes.engine.CodeEngine;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Local UI and Android platform routing; barcode operations are delegated to ZXing. */
public final class MainActivity extends Activity {
  private static final int IMPORT_IMAGE = 10, EXPORT_PNG = 11;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private Future<?> work;
  private int generation;
  private boolean destroyed, busy, dark, createMode;
  private String resultText, resultFormat, resultType, generatedText, pendingExportText;
  private Bitmap preview;
  private LinearLayout root, readPane, createPane, resultCard;
  private ScrollView scroll;
  private TextView status, resultBody, resultMeta, byteCount, qrCaption;
  private EditText input;
  private ImageView qrImage;
  private Button readTab,
      createTab,
      importButton,
      generateButton,
      exportButton,
      cancelButton,
      clearButton,
      themeButton;
  private int ink, muted, paper, card, accent;

  @Override
  public void onCreate(Bundle state) {
    String theme = getPreferences(MODE_PRIVATE).getString("theme", "System");
    dark =
        "Dark".equals(theme)
            || ("System".equals(theme)
                && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES);
    setTheme(dark ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
    super.onCreate(state);
    ink = Color.parseColor(dark ? "#EFF9F4" : "#173E34");
    muted = Color.parseColor(dark ? "#B2C7BE" : "#577268");
    paper = Color.parseColor(dark ? "#11231D" : "#F3F5EC");
    card = Color.parseColor(dark ? "#1D332A" : "#FFFFFF");
    accent = Color.parseColor(dark ? "#97E2C1" : "#176D5D");
    buildUi();
    if (state != null) {
      input.setText(state.getString("draft", ""));
      resultText = state.getString("resultText");
      resultFormat = state.getString("resultFormat");
      resultType = state.getString("resultType");
      pendingExportText = state.getString("pendingExportText");
      if (resultText != null) showResult();
      showMode(state.getBoolean("createMode"));
      String restoredQr = state.getString("generatedText");
      if (restoredQr != null && restoredQr.equals(input.getText().toString())) generate(restoredQr);
    } else showMode(false);
  }

  private void buildUi() {
    getWindow().setStatusBarColor(paper);
    getWindow().setNavigationBarColor(paper);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            dark
                ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(paper);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets.consumeSystemWindowInsets();
        });
    setContentView(root);
    root.requestApplyInsets();
    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    LinearLayout page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setPadding(dp(24), dp(24), dp(24), dp(24));
    scroll.addView(page);
    TextView eyebrow = text("ORITWIG  /  LOCAL TOOLS", 12, muted);
    eyebrow.setLetterSpacing(.13f);
    page.addView(eyebrow);
    TextView title = text("Codes", 38, ink);
    title.setTypeface(null, Typeface.BOLD);
    if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
    page.addView(title);
    TextView subtitle =
        text("Read an image. Make a QR.\nKeep the choice in your hands.", 16, muted);
    subtitle.setPadding(0, dp(8), 0, dp(24));
    page.addView(subtitle);
    LinearLayout tabs = new LinearLayout(this);
    page.addView(tabs);
    readTab = button("Read image", v -> showMode(false));
    readTab.setTag("readTab");
    tabs.addView(readTab, new LinearLayout.LayoutParams(0, dp(52), 1));
    createTab = button("Create QR", v -> showMode(true));
    createTab.setTag("createTab");
    LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, dp(52), 1);
    tabParams.setMarginStart(dp(8));
    tabs.addView(createTab, tabParams);
    readPane = column();
    page.addView(readPane);
    heading(readPane, "A code, without the guesswork");
    readPane.addView(
        text(
            "Choose an image containing one QR code or barcode. We’ll show what it contains before"
                + " you decide what to do.",
            16,
            muted));
    importButton = button("Choose image", v -> chooseImage());
    importButton.setTag("chooseImage");
    spaceAbove(readPane, importButton, 16);
    TextView limits =
        text(
            "PNG, JPEG, WebP and Android-supported images\n"
                + "Up to 20 MB · 64 MP source · sampled to 2,048 px",
            12,
            muted);
    spaceAbove(readPane, limits, 10);
    resultCard = column();
    resultCard.setPadding(dp(16), dp(16), dp(16), dp(16));
    resultCard.setBackground(shape(card, 16));
    resultCard.setVisibility(View.GONE);
    spaceAbove(readPane, resultCard, 20);
    resultMeta = text("", 12, accent);
    resultMeta.setTypeface(null, Typeface.BOLD);
    resultMeta.setTag("resultMeta");
    resultCard.addView(resultMeta);
    resultBody = text("", 18, ink);
    resultBody.setTextIsSelectable(true);
    resultBody.setTag("resultBody");
    resultBody.setPadding(0, dp(12), 0, dp(12));
    resultCard.addView(resultBody);
    resultCard.addView(
        text(
            "Untrusted content. Links, Wi-Fi settings and other actions never open automatically.",
            13,
            muted));
    LinearLayout resultActions = new LinearLayout(this);
    resultCard.addView(resultActions);
    Button copy = button("Copy text", v -> copyResult());
    copy.setTag("copyText");
    resultActions.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
    Button share = button("Share text", v -> shareResult());
    share.setTag("shareText");
    resultActions.addView(share, new LinearLayout.LayoutParams(0, -2, 1));
    Button clearResult =
        button(
            "Clear result",
            v -> {
              resultText = null;
              resultFormat = null;
              resultType = null;
              resultCard.setVisibility(View.GONE);
              setStatus("Result cleared.");
            });
    resultCard.addView(clearResult);
    createPane = column();
    page.addView(createPane);
    heading(createPane, "A little square. Your own words.");
    createPane.addView(
        text("Text, a web address or another payload. Nothing is sent to a server.", 16, muted));
    input = new EditText(this);
    input.setTag("payloadInput");
    input.setId(View.generateViewId());
    input.setHint("What should your QR code say?");
    input.setContentDescription("QR code text");
    input.setMinLines(3);
    input.setMaxLines(6);
    input.setTextColor(ink);
    input.setHintTextColor(muted);
    input.setGravity(Gravity.TOP | Gravity.START);
    input.setTextSize(18);
    input.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(1200)});
    spaceAbove(createPane, input, 12);
    byteCount = text("0 / 1,200 UTF-8 bytes", 12, muted);
    createPane.addView(byteCount);
    generateButton = button("Preview QR", v -> generate(input.getText().toString()));
    generateButton.setTag("previewQr");
    spaceAbove(createPane, generateButton, 12);
    qrImage = new ImageView(this);
    qrImage.setTag("qrPreview");
    qrImage.setContentDescription(
        "Generated QR code preview. The original text is in the text box above.");
    qrImage.setAdjustViewBounds(true);
    qrImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
    qrImage.setVisibility(View.GONE);
    LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(-1, dp(280));
    qrParams.topMargin = dp(16);
    createPane.addView(qrImage, qrParams);
    qrCaption = text("1,024 × 1,024 PNG · ZXing · correction level M", 12, muted);
    qrCaption.setGravity(Gravity.CENTER);
    qrCaption.setVisibility(View.GONE);
    createPane.addView(qrCaption);
    exportButton = button("Save PNG…", v -> exportQr());
    exportButton.setTag("savePng");
    exportButton.setEnabled(false);
    spaceAbove(createPane, exportButton, 12);
    clearButton =
        button(
            "Clear text",
            v -> {
              cancelWork(false);
              input.setText("");
              setStatus("Text cleared.");
            });
    createPane.addView(clearButton);
    input.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            int bytes = s.toString().getBytes(StandardCharsets.UTF_8).length;
            byteCount.setText(getString(R.string.byte_count, bytes));
            byteCount.setTextColor(
                bytes > CodeEngine.MAX_TEXT_BYTES
                    ? Color.parseColor(dark ? "#FFB5A5" : "#A02C1B")
                    : muted);
            invalidatePreview();
            if (busy) cancelWork(false);
          }

          public void afterTextChanged(Editable e) {}
        });
    status = text("All processing stays on this device.", 14, muted);
    status.setTag("status");
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    spaceAbove(page, status, 20);
    cancelButton = button("Cancel processing", v -> cancelWork(true));
    cancelButton.setVisibility(View.GONE);
    page.addView(cancelButton);
    TextView privacy = text("NO ACCOUNT  ·  NO ADS  ·  NO SCAN HISTORY", 10, muted);
    privacy.setLetterSpacing(.07f);
    spaceAbove(page, privacy, 28);
    LinearLayout footer = new LinearLayout(this);
    page.addView(footer);
    themeButton = button("Appearance", v -> chooseTheme());
    footer.addView(themeButton, new LinearLayout.LayoutParams(0, -2, 1));
    Button about = button("About & licenses", v -> about());
    about.setTag("about");
    footer.addView(about, new LinearLayout.LayoutParams(0, -2, 1));
  }

  private void showMode(boolean create) {
    createMode = create;
    createPane.setVisibility(create ? View.VISIBLE : View.GONE);
    readPane.setVisibility(create ? View.GONE : View.VISIBLE);
    readTab.setSelected(!create);
    createTab.setSelected(create);
    readTab.setBackgroundTintList(
        android.content.res.ColorStateList.valueOf(create ? card : accent));
    createTab.setBackgroundTintList(
        android.content.res.ColorStateList.valueOf(create ? accent : card));
    readTab.setTextColor(create ? accent : (dark ? paper : Color.WHITE));
    createTab.setTextColor(create ? (dark ? paper : Color.WHITE) : accent);
  }

  private void chooseImage() {
    Intent intent =
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("image/*")
            .addCategory(Intent.CATEGORY_OPENABLE);
    try {
      startActivityForResult(intent, IMPORT_IMAGE);
    } catch (ActivityNotFoundException e) {
      setStatus("No document picker is available on this device.");
    }
  }

  private void exportQr() {
    if (generatedText == null || preview == null) return;
    pendingExportText = generatedText;
    Intent intent =
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType("image/png")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, "oritwig-code.png");
    try {
      startActivityForResult(intent, EXPORT_PNG);
    } catch (ActivityNotFoundException e) {
      pendingExportText = null;
      setStatus("No save-location picker is available on this device.");
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != IMPORT_IMAGE && request != EXPORT_PNG) return;
    if (result != RESULT_OK || data == null || data.getData() == null) {
      if (request == EXPORT_PNG) pendingExportText = null;
      setStatus(
          request == IMPORT_IMAGE
              ? "Image selection canceled. Your previous result is unchanged."
              : "Save canceled. Your QR preview is still here.");
      return;
    }
    Uri uri = data.getData();
    if (request == IMPORT_IMAGE) readImage(uri);
    else {
      String text = pendingExportText;
      pendingExportText = null;
      if (text == null) setStatus("The export was interrupted. Preview your text and try again.");
      else savePng(uri, text);
    }
  }

  private void readImage(Uri uri) {
    resultText = null;
    resultFormat = null;
    resultType = null;
    resultCard.setVisibility(View.GONE);
    final int token = beginWork("Reading image on this device…");
    work =
        worker.submit(
            () -> {
              Bitmap bitmap = null;
              try {
                bitmap = BitmapBridge.read(getContentResolver(), uri);
                if (Thread.currentThread().isInterrupted()) return;
                Result result = BitmapBridge.decode(bitmap);
                String text = result.getText(),
                    format = result.getBarcodeFormat().toString(),
                    type = CodeEngine.type(result);
                runOnUiThread(
                    () -> {
                      if (!isCurrent(token)) return;
                      endWork();
                      resultText = text;
                      resultFormat = format;
                      resultType = type;
                      showResult();
                      setStatus("Code found. Review the text before copying or sharing.");
                    });
              } catch (NotFoundException e) {
                fail(
                    token,
                    "No readable code found. Try a sharp, well-lit crop containing one complete"
                        + " code.");
              } catch (IOException | IllegalArgumentException | SecurityException e) {
                fail(token, safeMessage(e));
              } catch (OutOfMemoryError e) {
                fail(token, "There isn’t enough memory for this image. Try a smaller image.");
              } finally {
                if (bitmap != null) bitmap.recycle();
              }
            });
  }

  private void generate(String text) {
    hideKeyboard();
    invalidatePreview();
    final int token = beginWork("Making your QR code…");
    work =
        worker.submit(
            () -> {
              try {
                Bitmap bitmap = BitmapBridge.qr(text);
                runOnUiThread(
                    () -> {
                      if (!isCurrent(token)) {
                        bitmap.recycle();
                        return;
                      }
                      endWork();
                      preview = bitmap;
                      generatedText = text;
                      qrImage.setImageBitmap(bitmap);
                      qrImage.setVisibility(View.VISIBLE);
                      qrCaption.setVisibility(View.VISIBLE);
                      exportButton.setEnabled(true);
                      setStatus(
                          createMode
                              ? "QR ready. Save it as a PNG wherever you choose."
                              : resultText != null
                                  ? "Code found. Review the text before copying or sharing."
                                  : "All processing stays on this device.");
                    });
              } catch (Exception e) {
                fail(token, safeMessage(e));
              } catch (OutOfMemoryError e) {
                fail(
                    token,
                    "There isn’t enough memory to create a QR. Close another app and try again.");
              }
            });
  }

  private void savePng(Uri uri, String text) {
    final int token = beginWork("Saving PNG…");
    work =
        worker.submit(
            () -> {
              Bitmap bitmap = null;
              try {
                bitmap = BitmapBridge.qr(text);
                byte[] bytes = BitmapBridge.png(bitmap);
                if (Thread.currentThread().isInterrupted()) return;
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                  if (output == null) throw new IOException("The destination could not be opened.");
                  output.write(bytes);
                  output.flush();
                }
                runOnUiThread(
                    () -> {
                      if (!isCurrent(token)) return;
                      endWork();
                      setStatus(
                          "Saved a 1,024 × 1,024 PNG. You can read it back with Choose image.");
                    });
              } catch (Exception e) {
                fail(
                    token,
                    "Could not save the PNG. "
                        + safeMessage(e)
                        + " A partial file may remain at the selected location.");
              } catch (OutOfMemoryError e) {
                fail(
                    token,
                    "Not enough memory to save. A partial file may remain at the selected"
                        + " location.");
              } finally {
                if (bitmap != null) bitmap.recycle();
              }
            });
    // Once writing starts, cancel cannot promise to undo an external provider write.
    cancelButton.setVisibility(View.GONE);
  }

  private int beginWork(String message) {
    cancelWork(false);
    busy = true;
    setStatus(message);
    setBusy(true);
    return generation;
  }

  private void endWork() {
    busy = false;
    work = null;
    setBusy(false);
  }

  private void setBusy(boolean value) {
    importButton.setEnabled(!value);
    generateButton.setEnabled(!value);
    input.setEnabled(!value);
    clearButton.setEnabled(!value);
    themeButton.setEnabled(!value);
    exportButton.setEnabled(!value && generatedText != null);
    cancelButton.setVisibility(value ? View.VISIBLE : View.GONE);
  }

  private void cancelWork(boolean inform) {
    generation++;
    if (work != null) work.cancel(true);
    endWork();
    if (inform) setStatus("Processing canceled. Nothing new was saved.");
  }

  private boolean isCurrent(int token) {
    return !destroyed && token == generation;
  }

  private void fail(int token, String message) {
    runOnUiThread(
        () -> {
          if (isCurrent(token)) {
            endWork();
            setStatus(message);
          }
        });
  }

  private String safeMessage(Throwable e) {
    String s = e.getMessage();
    return s == null || s.isEmpty() ? "Please try a different file or destination." : s;
  }

  private void invalidatePreview() {
    generatedText = null;
    exportButton.setEnabled(false);
    qrImage.setImageDrawable(null);
    qrImage.setVisibility(View.GONE);
    qrCaption.setVisibility(View.GONE);
    if (preview != null) {
      preview.recycle();
      preview = null;
    }
  }

  private void showResult() {
    resultBody.setText(resultText);
    resultMeta.setText(
        getString(
            R.string.result_meta,
            resultFormat.replace('_', ' '),
            resultType,
            resultText.codePointCount(0, resultText.length())));
    resultCard.setVisibility(View.VISIBLE);
  }

  private void copyResult() {
    if (resultText == null) return;
    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    ClipData data = ClipData.newPlainText("Scanned code", resultText);
    PersistableBundle extras = new PersistableBundle();
    extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
    data.getDescription().setExtras(extras);
    clipboard.setPrimaryClip(data);
    setStatus("Copied. Your keyboard or clipboard manager may retain this text.");
  }

  private void shareResult() {
    if (resultText == null) return;
    try {
      startActivity(
          Intent.createChooser(
              new Intent(Intent.ACTION_SEND)
                  .setType("text/plain")
                  .putExtra(Intent.EXTRA_TEXT, resultText),
              "Share scanned text"));
    } catch (ActivityNotFoundException e) {
      setStatus("No app is available to share text.");
    }
  }

  private void chooseTheme() {
    String[] choices = {"System", "Light", "Dark"};
    String current = getPreferences(MODE_PRIVATE).getString("theme", "System");
    int selected = java.util.Arrays.asList(choices).indexOf(current);
    new AlertDialog.Builder(this)
        .setTitle("Appearance")
        .setSingleChoiceItems(
            choices,
            selected,
            (dialog, which) -> {
              getPreferences(MODE_PRIVATE).edit().putString("theme", choices[which]).apply();
              dialog.dismiss();
              recreate();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void about() {
    new AlertDialog.Builder(this)
        .setTitle("Oritwig Codes · prototype")
        .setMessage(
            "A local image reader and QR maker. No account, internet permission, ads or scan"
                + " database. Only appearance is saved as a preference.\n\n"
                + "The barcode engine is ZXing core 3.5.4, Apache 2.0, unchanged upstream source."
                + " Telegram uses this same third-party library; ZXing is not Telegram-authored."
                + " This independent app has no Telegram account or API dependency.\n\n"
                + "This version reads one code per image and creates text QR codes. Live camera"
                + " scanning and history are not included. Original app shell and Android"
                + " file/pixel adapters surround the upstream engine.\n\n"
                + "Source pin: f651b0a0375676e47144f73397dddff8868b0e4c")
        .setPositiveButton("Licenses", (d, w) -> licenses())
        .setNegativeButton("Close", null)
        .show();
  }

  private void licenses() {
    StringBuilder text = new StringBuilder("ZXing project\nhttps://github.com/zxing/zxing\n\n");
    for (String file : new String[] {"NOTICE", "AUTHORS", "LICENSE"}) {
      try (InputStream stream = getAssets().open("licenses/" + file)) {
        text.append(new String(readAll(stream), StandardCharsets.UTF_8)).append("\n\n");
      } catch (IOException e) {
        text.append("Unable to load ").append(file);
      }
    }
    ScrollView box = new ScrollView(this);
    TextView content = text(text.toString(), 14, ink);
    content.setPadding(dp(20), dp(16), dp(20), dp(16));
    content.setTextIsSelectable(true);
    box.addView(content);
    new AlertDialog.Builder(this)
        .setTitle("Open-source notices")
        .setView(box)
        .setPositiveButton("Close", null)
        .show();
  }

  private byte[] readAll(InputStream stream) throws IOException {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int n;
    while ((n = stream.read(buffer)) != -1) out.write(buffer, 0, n);
    return out.toByteArray();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putString("draft", input.getText().toString());
    state.putBoolean("createMode", createMode);
    state.putString("resultText", resultText);
    state.putString("resultFormat", resultFormat);
    state.putString("resultType", resultType);
    state.putString("generatedText", generatedText);
    state.putString("pendingExportText", pendingExportText);
  }

  @Override
  protected void onDestroy() {
    destroyed = true;
    generation++;
    if (work != null) work.cancel(true);
    worker.shutdownNow();
    if (preview != null) {
      qrImage.setImageDrawable(null);
      preview.recycle();
      preview = null;
    }
    super.onDestroy();
  }

  private void hideKeyboard() {
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(input.getWindowToken(), 0);
    input.clearFocus();
  }

  private void setStatus(String message) {
    status.setText(message);
  }

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private LinearLayout column() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  private TextView text(String value, int size, int color) {
    TextView v = new TextView(this);
    v.setText(value);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setLineSpacing(dp(2), 1);
    return v;
  }

  private Button button(String title, View.OnClickListener action) {
    Button v = new Button(this);
    v.setText(title);
    v.setAllCaps(false);
    v.setTextColor(accent);
    v.setMinHeight(dp(48));
    v.setOnClickListener(action);
    return v;
  }

  private void heading(LinearLayout parent, String title) {
    TextView v = text(title, 22, ink);
    v.setTypeface(null, Typeface.BOLD);
    if (android.os.Build.VERSION.SDK_INT >= 28) v.setAccessibilityHeading(true);
    spaceAbove(parent, v, 24);
    v.setPadding(0, 0, 0, dp(12));
  }

  private void spaceAbove(LinearLayout parent, View child, int margin) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(margin);
    parent.addView(child, p);
  }

  private GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }
}
