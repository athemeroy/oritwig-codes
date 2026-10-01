package dev.oritwig.codes.engine;

import static org.junit.Assert.*;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import java.util.Arrays;
import org.junit.Test;

public class CodeEngineTest {
  private static int[] pixels(BitMatrix m, boolean inverted) {
    int[] p = new int[m.getWidth() * m.getHeight()];
    for (int y = 0; y < m.getHeight(); y++)
      for (int x = 0; x < m.getWidth(); x++)
        p[y * m.getWidth() + x] = m.get(x, y) != inverted ? 0xff000000 : 0xffffffff;
    return p;
  }

  private Result roundtrip(String s) throws Exception {
    BitMatrix m = CodeEngine.encodeQr(s, 768);
    return CodeEngine.decode(pixels(m, false), m.getWidth(), m.getHeight());
  }

  @Test
  public void plainTextRoundtrip() throws Exception {
    assertEquals("Meet at 14:30", roundtrip("Meet at 14:30").getText());
  }

  @Test
  public void unicodeRoundtrip() throws Exception {
    assertEquals("你好 • café 🍃", roundtrip("你好 • café 🍃").getText());
  }

  @Test
  public void preserveWhitespace() throws Exception {
    assertEquals(" x\n y ", roundtrip(" x\n y ").getText());
  }

  @Test
  public void urlIsParsedOnly() throws Exception {
    assertEquals("URI", CodeEngine.type(roundtrip("https://example.org/path?a=b")));
  }

  @Test
  public void wifiIsParsedOnly() throws Exception {
    assertEquals("WIFI", CodeEngine.type(roundtrip("WIFI:T:WPA;S:Demo;P:sample;;")));
  }

  @Test
  public void maxTextRoundtrip() throws Exception {
    String s = "a".repeat(1200);
    assertEquals(s, roundtrip(s).getText());
  }

  @Test(expected = IllegalArgumentException.class)
  public void tooManyBytes() throws Exception {
    CodeEngine.encodeQr("好".repeat(401), 768);
  }

  @Test(expected = IllegalArgumentException.class)
  public void blankRejected() throws Exception {
    CodeEngine.encodeQr(" \n ", 768);
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidSizeRejected() throws Exception {
    CodeEngine.encodeQr("hello", 10000);
  }

  @Test(expected = IllegalArgumentException.class)
  public void oversizedPixelsRejected() throws Exception {
    CodeEngine.decode(new int[4], 4096, 4096);
  }

  @Test(expected = NotFoundException.class)
  public void noCode() throws Exception {
    int[] p = new int[256 * 256];
    Arrays.fill(p, 0xffffffff);
    CodeEngine.decode(p, 256, 256);
  }

  @Test
  public void invertedQr() throws Exception {
    BitMatrix m = CodeEngine.encodeQr("Inverted", 512);
    assertEquals("Inverted", CodeEngine.decode(pixels(m, true), 512, 512).getText());
  }

  @Test
  public void rotatedQr() throws Exception {
    BitMatrix m = CodeEngine.encodeQr("Rotated", 512);
    m.rotate90();
    assertEquals("Rotated", CodeEngine.decode(pixels(m, false), 512, 512).getText());
  }

  @Test
  public void code128() throws Exception {
    BitMatrix m = new MultiFormatWriter().encode("SAMPLE-12345", BarcodeFormat.CODE_128, 1000, 240);
    Result r = CodeEngine.decode(pixels(m, false), 1000, 240);
    assertEquals(BarcodeFormat.CODE_128, r.getBarcodeFormat());
    assertEquals("SAMPLE-12345", r.getText());
  }

  @Test
  public void ean13() throws Exception {
    BitMatrix m = new MultiFormatWriter().encode("5901234123457", BarcodeFormat.EAN_13, 1000, 240);
    assertEquals("5901234123457", CodeEngine.decode(pixels(m, false), 1000, 240).getText());
  }

  @Test
  public void correctionLevelM() throws Exception {
    assertEquals(
        "M",
        roundtrip("Correction").getResultMetadata().get(ResultMetadataType.ERROR_CORRECTION_LEVEL));
  }
}
