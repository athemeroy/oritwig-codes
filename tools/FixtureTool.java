import com.google.zxing.common.BitMatrix;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import dev.oritwig.codes.engine.CodeEngine;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

public final class FixtureTool {
    public static void main(String[] args) throws Exception {
        if(args[0].equals("decode")) {
            BufferedImage b=ImageIO.read(new File(args[1]));
            int[] pixels=b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth());
            String actual=CodeEngine.decode(pixels,b.getWidth(),b.getHeight()).getText();
            System.out.println(actual);
            if(args.length>2 && !actual.equals(args[2])) throw new AssertionError("Unexpected exported payload");
            return;
        }
        write(CodeEngine.encodeQr("https://example.org/oritwig-codes",1024),new File(args[1],"sample-qr.png"));
        write(new MultiFormatWriter().encode("ORITWIG-12345",BarcodeFormat.CODE_128,1024,280),new File(args[1],"sample-barcode.png"));
        BufferedImage blank=new BufferedImage(512,512,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<512;y++)for(int x=0;x<512;x++)blank.setRGB(x,y,0xffffff);
        ImageIO.write(blank,"PNG",new File(args[1],"blank.png"));
    }
    private static void write(BitMatrix m,File path)throws Exception {
        BufferedImage b=new BufferedImage(m.getWidth(),m.getHeight(),BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<m.getHeight();y++)for(int x=0;x<m.getWidth();x++)b.setRGB(x,y,m.get(x,y)?0:0xffffff);
        ImageIO.write(b,"PNG",path);
    }
}
