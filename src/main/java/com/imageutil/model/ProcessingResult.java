package com.imageutil.model;

import java.nio.file.Path;

/**
 * Résultat du traitement d'une image individuelle.
 */
public record ProcessingResult(
        Path inputFile,
        Path outputFile,
        int originalWidth,
        int originalHeight,
        int newWidth,
        int newHeight,
        long originalSizeBytes,
        long newSizeBytes,
        boolean success,
        String errorMessage
) {
    public static ProcessingResult success(
            Path inputFile,
            Path outputFile,
            int originalWidth,
            int originalHeight,
            int newWidth,
            int newHeight,
            long originalSizeBytes,
            long newSizeBytes
    ) {
        return new ProcessingResult(
                inputFile, outputFile,
                originalWidth, originalHeight,
                newWidth, newHeight,
                originalSizeBytes, newSizeBytes,
                true, null
        );
    }

    public static ProcessingResult failure(Path inputFile, String errorMessage) {
        return new ProcessingResult(
                inputFile, null,
                0, 0, 0, 0,
                0, 0,
                false, errorMessage
        );
    }

    public double compressionRatioPercent() {
        if (originalSizeBytes <= 0) {
            return 0.0;
        }
        return (1.0 - ((double) newSizeBytes / originalSizeBytes)) * 100.0;
    }
}
