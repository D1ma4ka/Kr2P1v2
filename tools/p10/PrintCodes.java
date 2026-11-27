import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;

// Produces a local sheet for the physical-camera acceptance run using the app's bundled ZXing library.
class PrintCodes {
    private static final String RETAIL_CODE = "4006381333931";

    private static void drawCode(Graphics2D graphics, BitMatrix bits, int left, int top) {
        for (int row = 0; row < bits.getHeight(); row++) {
            for (int column = 0; column < bits.getWidth(); column++) {
                if (bits.get(column, row)) graphics.fillRect(left + column, top + row, 1, 1);
            }
        }
    }

    private static void writePng(BufferedImage image, String path) throws Exception {
        if (!ImageIO.write(image, "png", Path.of(path).toFile())) {
            throw new IllegalStateException("PNG encoder unavailable");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("Usage: PrintCodes.java reward-file sheet.png [camera-frame.png]");
        }
        String reward = Files.readString(Path.of(args[0])).strip();
        if (!reward.matches("krapiva:reward:[A-Za-z0-9_-]{16,180}")) {
            throw new IllegalArgumentException("Expected a partner reward code from the local test fixture");
        }
        var writer = new MultiFormatWriter();
        BufferedImage image = new BufferedImage(1280, 1500, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 42));
            graphics.drawString("KraPet — local partner QR", 88, 108);
            graphics.drawString("KraPiva — EAN-13", 88, 876);
            graphics.drawString(RETAIL_CODE, 88, 1280);
            drawCode(graphics, writer.encode(reward, BarcodeFormat.QR_CODE, 540, 540), 76, 150);
            drawCode(graphics, writer.encode(RETAIL_CODE, BarcodeFormat.EAN_13, 1000, 280,
                Map.of(EncodeHintType.MARGIN, 20)), 76, 910);
        } finally {
            graphics.dispose();
        }
        writePng(image, args[1]);
        if (args.length == 3) {
            BufferedImage frame = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
            Graphics2D camera = frame.createGraphics();
            try {
                camera.setColor(Color.WHITE);
                camera.fillRect(0, 0, frame.getWidth(), frame.getHeight());
                camera.setColor(Color.BLACK);
                drawCode(camera, writer.encode(reward, BarcodeFormat.QR_CODE, 360, 360), 140, 60);
            } finally {
                camera.dispose();
            }
            writePng(frame, args[2]);
        }
    }
}
