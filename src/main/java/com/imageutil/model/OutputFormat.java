package com.imageutil.model;

/**
 * Formats de sortie supportés pour la compression / conversion.
 */
public enum OutputFormat {
    JPEG("JPEG (.jpg) - idéal pour photos", "jpg", "jpeg"),
    PNG("PNG (.png) - sans perte", "png", "png"),
    ORIGINAL("Conserver le format d'origine", null, null);

    private final String label;
    private final String extension;
    private final String imageIoName;

    OutputFormat(String label, String extension, String imageIoName) {
        this.label = label;
        this.extension = extension;
        this.imageIoName = imageIoName;
    }

    public String getExtension() {
        return extension;
    }

    public String getImageIoName() {
        return imageIoName;
    }

    /**
     * Détermine le nom du writer ImageIO à utiliser pour un fichier source donné.
     */
    public String resolveImageIoName(String sourceFileName) {
        if (this != ORIGINAL) {
            return imageIoName;
        }
        String ext = extractExtension(sourceFileName);
        return switch (ext) {
            case "jpg", "jpeg" -> "jpeg";
            case "png" -> "png";
            case "bmp" -> "bmp";
            case "gif" -> "gif";
            default -> "jpeg";
        };
    }

    /**
     * Détermine l'extension de fichier de sortie à utiliser pour un fichier source donné.
     */
    public String resolveExtension(String sourceFileName) {
        if (this != ORIGINAL) {
            return extension;
        }
        String ext = extractExtension(sourceFileName);
        return switch (ext) {
            case "jpg", "jpeg", "png", "bmp", "gif" -> ext;
            default -> "jpg";
        };
    }

    private static String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    @Override
    public String toString() {
        return label;
    }
}

