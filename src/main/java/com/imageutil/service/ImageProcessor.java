package com.imageutil.service;

import com.imageutil.model.ExactRatioMode;
import com.imageutil.model.OutputFormat;
import com.imageutil.model.ProcessingOptions;
import com.imageutil.model.ProcessingResult;
import com.imageutil.model.ResizeMode;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Service de redimensionnement et de compression d'images basé sur Java ImageIO et Graphics2D.
 */
public class ImageProcessor {

    public static final Set<String> SUPPORTED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "bmp", "gif");

    public static boolean isSupportedImage(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        return SUPPORTED_EXTENSIONS.contains(name.substring(dot + 1));
    }

    /**
     * Lit rapidement les dimensions d'une image sans charger toute la bitmap si le reader le supporte.
     */
    public Dimension readImageDimensions(Path path) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(path.toFile())) {
            if (iis == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                return new Dimension(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * Traite une image selon les options fournies.
     */
    public ProcessingResult process(Path inputFile, ProcessingOptions options) {
        if (inputFile == null || !Files.isRegularFile(inputFile)) {
            return ProcessingResult.failure(inputFile, "Fichier introuvable");
        }

        try {
            long originalSize = Files.size(inputFile);
            BufferedImage source = ImageIO.read(inputFile.toFile());
            if (source == null) {
                return ProcessingResult.failure(inputFile, "Format d'image non reconnu ou fichier corrompu");
            }

            int origW = source.getWidth();
            int origH = source.getHeight();
            Dimension targetDim = calculateTargetDimension(origW, origH, options);

            BufferedImage processed = source;
            if (targetDim.width != origW || targetDim.height != origH) {
                processed = resizeImage(source, targetDim.width, targetDim.height);
            }

            String fileName = inputFile.getFileName().toString();
            String ioFormat = options.outputFormat().resolveImageIoName(fileName);
            String extension = options.outputFormat().resolveExtension(fileName);

            Path outputFile = resolveOutputPath(inputFile, options, extension);
            if (outputFile.getParent() != null) {
                Files.createDirectories(outputFile.getParent());
            }

            BufferedImage writable = normalizeColorSpace(processed, ioFormat);
            writeCompressedImage(writable, ioFormat, options.qualityPercent(), outputFile);

            long newSize = Files.size(outputFile);
            return ProcessingResult.success(
                    inputFile,
                    outputFile,
                    origW,
                    origH,
                    writable.getWidth(),
                    writable.getHeight(),
                    originalSize,
                    newSize
            );
        } catch (Exception e) {
            return ProcessingResult.failure(inputFile, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    /**
     * Calcule les dimensions cibles selon le mode de redimensionnement.
     */
    public Dimension calculateTargetDimension(int origW, int origH, ProcessingOptions options) {
        ResizeMode mode = options.resizeMode();
        switch (mode) {
            case NONE:
                return new Dimension(origW, origH);
            case PERCENTAGE: {
                int pct = options.percentage();
                if (!options.allowUpscale() && pct > 100) {
                    pct = 100;
                }
                int w = Math.max(1, (int) Math.round(origW * (pct / 100.0)));
                int h = Math.max(1, (int) Math.round(origH * (pct / 100.0)));
                return new Dimension(w, h);
            }
            case FIT_MAX: {
                int maxW = options.targetWidth();
                int maxH = options.targetHeight();
                double ratio = Math.min((double) maxW / origW, (double) maxH / origH);
                if (!options.allowUpscale() && ratio >= 1.0) {
                    return new Dimension(origW, origH);
                }
                int w = Math.max(1, (int) Math.round(origW * ratio));
                int h = Math.max(1, (int) Math.round(origH * ratio));
                return new Dimension(w, h);
            }
            case EXACT: {
                ExactRatioMode ratioMode = options.exactRatioMode();
                if (ratioMode == ExactRatioMode.FIXED_WIDTH) {
                    int w = options.targetWidth();
                    if (!options.allowUpscale() && w >= origW) {
                        return new Dimension(origW, origH);
                    }
                    int h = Math.max(1, (int) Math.round(origH * ((double) w / origW)));
                    return new Dimension(Math.max(1, w), h);
                }
                if (ratioMode == ExactRatioMode.FIXED_HEIGHT) {
                    int h = options.targetHeight();
                    if (!options.allowUpscale() && h >= origH) {
                        return new Dimension(origW, origH);
                    }
                    int w = Math.max(1, (int) Math.round(origW * ((double) h / origH)));
                    return new Dimension(w, Math.max(1, h));
                }
                int w = options.targetWidth();
                int h = options.targetHeight();
                if (!options.allowUpscale()) {
                    w = Math.min(w, origW);
                    h = Math.min(h, origH);
                }
                return new Dimension(Math.max(1, w), Math.max(1, h));
            }
            default:
                return new Dimension(origW, origH);
        }
    }

    /**
     * Redimensionne l'image avec réduction progressive par paliers pour un rendu net.
     */
    public BufferedImage resizeImage(BufferedImage source, int targetWidth, int targetHeight) {
        int type = (source.getTransparency() == Transparency.OPAQUE)
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;

        BufferedImage current = source;
        int currentW = source.getWidth();
        int currentH = source.getHeight();

        // Réduction progressive par moitié tant que l'image fait plus du double de la taille cible
        while (currentW / 2 >= targetWidth && currentH / 2 >= targetHeight) {
            int nextW = Math.max(targetWidth, currentW / 2);
            int nextH = Math.max(targetHeight, currentH / 2);
            current = drawScaled(current, nextW, nextH, type, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            currentW = nextW;
            currentH = nextH;
        }

        if (currentW != targetWidth || currentH != targetHeight) {
            current = drawScaled(current, targetWidth, targetHeight, type, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        }

        return current;
    }

    private BufferedImage drawScaled(
            BufferedImage src,
            int width,
            int height,
            int imageType,
            Object interpolationHint
    ) {
        BufferedImage dest = new BufferedImage(width, height, imageType);
        Graphics2D g2 = dest.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationHint);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(src, 0, 0, width, height, null);
        } finally {
            g2.dispose();
        }
        return dest;
    }

    /**
     * Convertit les images transparentes en RGB sur fond blanc pour les formats sans canal alpha (JPEG, BMP).
     */
    private BufferedImage normalizeColorSpace(BufferedImage image, String ioFormat) {
        boolean supportsAlpha = "png".equalsIgnoreCase(ioFormat) || "gif".equalsIgnoreCase(ioFormat);
        if (supportsAlpha) {
            return image;
        }
        if (image.getType() == BufferedImage.TYPE_INT_RGB || image.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = rgb.createGraphics();
        try {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, image.getWidth(), image.getHeight());
            g2.drawImage(image, 0, 0, null);
        } finally {
            g2.dispose();
        }
        return rgb;
    }

    private void writeCompressedImage(
            BufferedImage image,
            String ioFormat,
            int qualityPercent,
            Path outputFile
    ) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(ioFormat);
        if (!writers.hasNext()) {
            throw new IOException("Aucun encodeur disponible pour le format : " + ioFormat);
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile.toFile())) {
            if (ios == null) {
                throw new IOException("Impossible d'ouvrir le fichier en écriture : " + outputFile);
            }
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();

            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                if ("jpeg".equalsIgnoreCase(ioFormat) || "jpg".equalsIgnoreCase(ioFormat)) {
                    param.setCompressionQuality(Math.max(0.01f, Math.min(1.0f, qualityPercent / 100.0f)));
                } else if ("png".equalsIgnoreCase(ioFormat)) {
                    // Pour PNG dans ImageIO, 0.0f correspond à la compression maximale sans perte
                    param.setCompressionQuality(0.0f);
                }
            }

            writer.write(null, new IIOImage(image, null, null), param);
            ios.flush();
        } finally {
            writer.dispose();
        }
    }

    /**
     * Résout le chemin du fichier de sortie en gérant les collisions si overwriteExisting est désactivé.
     */
    public Path resolveOutputPath(Path inputFile, ProcessingOptions options, String extension) {
        Path parentDir = options.useSubfolderCompressed()
                ? inputFile.toAbsolutePath().getParent().resolve("compressed")
                : options.outputDirectory();

        String rawName = inputFile.getFileName().toString();
        int dot = rawName.lastIndexOf('.');
        String baseName = (dot > 0) ? rawName.substring(0, dot) : rawName;
        String suffix = options.fileNameSuffix();

        Path candidate = parentDir.resolve(baseName + suffix + "." + extension);
        if (options.overwriteExisting() || !Files.exists(candidate)) {
            return candidate;
        }

        int counter = 1;
        while (true) {
            Path numbered = parentDir.resolve(baseName + suffix + "_" + counter + "." + extension);
            if (!Files.exists(numbered)) {
                return numbered;
            }
            counter++;
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " o";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.FRANCE, "%.1f Ko", kb);
        }
        double mb = kb / 1024.0;
        return String.format(Locale.FRANCE, "%.2f Mo", mb);
    }
}

