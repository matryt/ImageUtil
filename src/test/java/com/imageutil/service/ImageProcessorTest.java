package com.imageutil.service;

import com.imageutil.model.ExactRatioMode;
import com.imageutil.model.OutputFormat;
import com.imageutil.model.ProcessingOptions;
import com.imageutil.model.ProcessingResult;
import com.imageutil.model.ResizeMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    @TempDir
    Path tempDir;

    private Path createSampleImage(String filename, int width, int height, boolean withAlpha) throws IOException {
        int type = withAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage img = new BufferedImage(width, height, type);
        Graphics2D g = img.createGraphics();
        try {
            if (!withAlpha) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, width, height);
            }
            for (int y = 0; y < height; y += 20) {
                g.setColor(new Color((y * 7) % 255, 100, 180));
                g.fillRect(0, y, width, 10);
            }
        } finally {
            g.dispose();
        }
        Path file = tempDir.resolve(filename);
        String ext = filename.substring(filename.lastIndexOf('.') + 1);
        ImageIO.write(img, ext.equals("jpg") ? "jpeg" : ext, file.toFile());
        return file;
    }

    @Test
    void testPercentageResizeAndSubfolderOutput() throws IOException {
        Path input = createSampleImage("photo.png", 800, 600, false);
        ProcessingOptions opts = new ProcessingOptions(
                ResizeMode.PERCENTAGE, 50, 100, 100, false,
                OutputFormat.JPEG, 75, null, true, "_min", false
        );

        ProcessingResult result = processor.process(input, opts);

        assertTrue(result.success());
        assertEquals(800, result.originalWidth());
        assertEquals(600, result.originalHeight());
        assertEquals(400, result.newWidth());
        assertEquals(300, result.newHeight());
        assertEquals(tempDir.resolve("compressed/photo_min.jpg"), result.outputFile());
        assertTrue(Files.exists(result.outputFile()));

        Dimension readDim = processor.readImageDimensions(result.outputFile());
        assertNotNull(readDim);
        assertEquals(400, readDim.width);
        assertEquals(300, readDim.height);
    }

    @Test
    void testFitMaxPreservesAspectRatioAndPreventsUpscale() throws IOException {
        Path input = createSampleImage("wide.jpg", 1000, 500, false);
        ProcessingOptions downscaleOpts = new ProcessingOptions(
                ResizeMode.FIT_MAX, 100, 400, 400, false,
                OutputFormat.ORIGINAL, 80, tempDir.resolve("out"), false, "", true
        );

        ProcessingResult down = processor.process(input, downscaleOpts);
        assertTrue(down.success());
        assertEquals(400, down.newWidth());
        assertEquals(200, down.newHeight());

        ProcessingOptions noUpscaleOpts = new ProcessingOptions(
                ResizeMode.FIT_MAX, 100, 3000, 3000, false,
                OutputFormat.ORIGINAL, 80, tempDir.resolve("out2"), false, "", true
        );
        ProcessingResult same = processor.process(input, noUpscaleOpts);
        assertTrue(same.success());
        assertEquals(1000, same.newWidth());
        assertEquals(500, same.newHeight());
    }

    @Test
    void testTransparentPngToJpegConversionAndQualityImpact() throws IOException {
        Path input = createSampleImage("alpha.png", 600, 600, true);
        Path outDir = tempDir.resolve("jpg_out");

        ProcessingOptions highQ = new ProcessingOptions(
                ResizeMode.NONE, 100, 600, 600, false,
                OutputFormat.JPEG, 95, outDir, false, "_hq", true
        );
        ProcessingOptions lowQ = new ProcessingOptions(
                ResizeMode.NONE, 100, 600, 600, false,
                OutputFormat.JPEG, 20, outDir, false, "_lq", true
        );

        ProcessingResult rHigh = processor.process(input, highQ);
        ProcessingResult rLow = processor.process(input, lowQ);

        assertTrue(rHigh.success());
        assertTrue(rLow.success());
        assertTrue(rLow.newSizeBytes() < rHigh.newSizeBytes());
    }

    @Test
    void testPngCompressionOutput() throws IOException {
        Path input = createSampleImage("sample.png", 400, 300, true);
        ProcessingOptions pngOpts = new ProcessingOptions(
                ResizeMode.EXACT, 100, 200, 150, false,
                OutputFormat.PNG, 80, tempDir.resolve("png_out"), false, "", false
        );

        ProcessingResult res1 = processor.process(input, pngOpts);
        ProcessingResult res2 = processor.process(input, pngOpts);

        assertTrue(res1.success());
        assertTrue(res2.success());
        assertEquals(200, res1.newWidth());
        assertEquals(150, res1.newHeight());
        assertEquals("sample.png", res1.outputFile().getFileName().toString());
        assertEquals("sample_1.png", res2.outputFile().getFileName().toString());
    }

    @Test
    void testExactFixedWidthAndHeightAdaptRatio() throws IOException {
        Path landscape = createSampleImage("landscape.png", 1600, 900, false);
        Path portrait = createSampleImage("portrait.png", 900, 1600, false);
        Path outDir = tempDir.resolve("ratio_out");

        ProcessingOptions fixedWidth = new ProcessingOptions(
                ResizeMode.EXACT, 100, 800, 1080, ExactRatioMode.FIXED_WIDTH, false,
                OutputFormat.ORIGINAL, 80, outDir, false, "_w", true
        );
        ProcessingResult rL = processor.process(landscape, fixedWidth);
        ProcessingResult rP = processor.process(portrait, fixedWidth);

        assertTrue(rL.success());
        assertEquals(800, rL.newWidth());
        assertEquals(450, rL.newHeight());

        assertTrue(rP.success());
        assertEquals(800, rP.newWidth());
        assertEquals(1422, rP.newHeight());

        ProcessingOptions fixedHeight = new ProcessingOptions(
                ResizeMode.EXACT, 100, 1920, 450, ExactRatioMode.FIXED_HEIGHT, false,
                OutputFormat.ORIGINAL, 80, outDir, false, "_h", true
        );
        ProcessingResult rLh = processor.process(landscape, fixedHeight);
        ProcessingResult rPh = processor.process(portrait, fixedHeight);

        assertTrue(rLh.success());
        assertEquals(800, rLh.newWidth());
        assertEquals(450, rLh.newHeight());

        assertTrue(rPh.success());
        assertEquals(253, rPh.newWidth());
        assertEquals(450, rPh.newHeight());
    }

    @Test
    void testIsSupportedImage() throws IOException {
        Path txt = Files.writeString(tempDir.resolve("notes.txt"), "hello");
        Path jpg = createSampleImage("pic.jpg", 10, 10, false);

        assertFalse(ImageProcessor.isSupportedImage(txt));
        assertTrue(ImageProcessor.isSupportedImage(jpg));
        assertFalse(ImageProcessor.isSupportedImage(tempDir));
    }
}


