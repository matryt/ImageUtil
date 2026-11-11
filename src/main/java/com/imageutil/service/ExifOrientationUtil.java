package com.imageutil.service;

import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utilitaire de lecture et d'application de l'orientation EXIF (images JPEG).
 */
public final class ExifOrientationUtil {

    private static final int EXIF_ORIENTATION_TAG = 0x0112;

    private ExifOrientationUtil() {
    }

    /**
     * Lit le tag EXIF Orientation (1 à 8) d'un fichier image. Retourne 1 si absent ou non JPEG.
     */
    public static int readOrientation(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return 1;
        }
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            if (in.read() != 0xFF || in.read() != 0xD8) {
                return 1; // Pas un JPEG
            }
            while (true) {
                int markerPrefix = in.read();
                while (markerPrefix == 0xFF) {
                    markerPrefix = in.read();
                }
                if (markerPrefix == -1) {
                    return 1;
                }
                // Si on atteint SOS (0xDA) ou EOI (0xD9), les métadonnées sont terminées
                if (markerPrefix == 0xDA || markerPrefix == 0xD9) {
                    return 1;
                }
                // Marqueurs sans longueur
                if (markerPrefix == 0x00 || markerPrefix == 0x01
                        || (markerPrefix >= 0xD0 && markerPrefix <= 0xD8)) {
                    continue;
                }

                int lenHi = in.read();
                int lenLo = in.read();
                if (lenHi < 0 || lenLo < 0) {
                    return 1;
                }
                int segmentLength = (lenHi << 8) | lenLo;
                if (segmentLength < 2) {
                    return 1;
                }
                int payloadLength = segmentLength - 2;

                if (markerPrefix == 0xE1 && payloadLength >= 14) {
                    byte[] payload = in.readNBytes(payloadLength);
                    if (payload.length == payloadLength && isExifHeader(payload)) {
                        int orientation = parseTiffOrientation(payload, 6);
                        if (orientation >= 1 && orientation <= 8) {
                            return orientation;
                        }
                    }
                } else {
                    skipFully(in, payloadLength);
                }
            }
        } catch (IOException ignored) {
            return 1;
        }
    }

    public static boolean swapsDimensions(int orientation) {
        return orientation >= 5 && orientation <= 8;
    }

    /**
     * Applique la transformation correspondant au tag EXIF Orientation pour obtenir des pixels droits.
     */
    public static BufferedImage applyOrientation(BufferedImage image, int orientation) {
        if (image == null || orientation <= 1 || orientation > 8) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        boolean swap = swapsDimensions(orientation);
        int destW = swap ? h : w;
        int destH = swap ? w : h;

        AffineTransform tx = new AffineTransform();
        switch (orientation) {
            case 2 -> { // Miroir horizontal
                tx.scale(-1, 1);
                tx.translate(-w, 0);
            }
            case 3 -> { // Rotation 180°
                tx.translate(w, h);
                tx.rotate(Math.PI);
            }
            case 4 -> { // Miroir vertical
                tx.scale(1, -1);
                tx.translate(0, -h);
            }
            case 5 -> { // Transposition (miroir + 90° horaire)
                tx.rotate(-Math.PI / 2);
                tx.scale(-1, 1);
            }
            case 6 -> { // Rotation 90° horaire
                tx.translate(h, 0);
                tx.rotate(Math.PI / 2);
            }
            case 7 -> { // Transverse (miroir + 90° antihoraire)
                tx.translate(h, w);
                tx.rotate(Math.PI / 2);
                tx.scale(-1, 1);
            }
            case 8 -> { // Rotation 90° antihoraire (270° horaire)
                tx.translate(0, w);
                tx.rotate(-Math.PI / 2);
            }
            default -> {
                return image;
            }
        }

        int type = (image.getTransparency() == Transparency.OPAQUE)
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;
        BufferedImage dest = new BufferedImage(destW, destH, type);
        Graphics2D g2 = dest.createGraphics();
        try {
            g2.drawImage(image, tx, null);
        } finally {
            g2.dispose();
        }
        return dest;
    }

    private static boolean isExifHeader(byte[] data) {
        return data.length >= 6
                && data[0] == 'E' && data[1] == 'x' && data[2] == 'i'
                && data[3] == 'f' && data[4] == 0 && data[5] == 0;
    }

    private static int parseTiffOrientation(byte[] data, int tiffStart) {
        if (tiffStart + 8 > data.length) {
            return 1;
        }
        boolean littleEndian;
        if (data[tiffStart] == 'I' && data[tiffStart + 1] == 'I') {
            littleEndian = true;
        } else if (data[tiffStart] == 'M' && data[tiffStart + 1] == 'M') {
            littleEndian = false;
        } else {
            return 1;
        }

        int magic = readUInt16(data, tiffStart + 2, littleEndian);
        if (magic != 42) {
            return 1;
        }

        int ifdOffset = readInt32(data, tiffStart + 4, littleEndian);
        int ifdStart = tiffStart + ifdOffset;
        if (ifdOffset < 8 || ifdStart + 2 > data.length) {
            return 1;
        }

        int entryCount = readUInt16(data, ifdStart, littleEndian);
        for (int i = 0; i < entryCount; i++) {
            int entryPos = ifdStart + 2 + (i * 12);
            if (entryPos + 12 > data.length) {
                break;
            }
            int tag = readUInt16(data, entryPos, littleEndian);
            if (tag == EXIF_ORIENTATION_TAG) {
                int type = readUInt16(data, entryPos + 2, littleEndian);
                int count = readInt32(data, entryPos + 4, littleEndian);
                if (type == 3 && count == 1) {
                    return readUInt16(data, entryPos + 8, littleEndian);
                }
            }
        }
        return 1;
    }

    private static int readUInt16(byte[] data, int offset, boolean littleEndian) {
        int b0 = data[offset] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        return littleEndian ? (b0 | (b1 << 8)) : ((b0 << 8) | b1);
    }

    private static int readInt32(byte[] data, int offset, boolean littleEndian) {
        int b0 = data[offset] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        int b2 = data[offset + 2] & 0xFF;
        int b3 = data[offset + 3] & 0xFF;
        return littleEndian
                ? (b0 | (b1 << 8) | (b2 << 16) | (b3 << 24))
                : ((b0 << 24) | (b1 << 16) | (b2 << 8) | b3);
    }

    private static void skipFully(InputStream in, long bytesToSkip) throws IOException {
        long remaining = bytesToSkip;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() == -1) {
                    break;
                }
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }
}

