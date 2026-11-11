package com.imageutil.model;

import java.nio.file.Path;

/**
 * Options de traitement appliquées à un lot d'images.
 */
public record ProcessingOptions(
        ResizeMode resizeMode,
        int percentage,
        int targetWidth,
        int targetHeight,
        ExactRatioMode exactRatioMode,
        boolean allowUpscale,
        OutputFormat outputFormat,
        int qualityPercent,
        Path outputDirectory,
        boolean useSubfolderCompressed,
        String fileNameSuffix,
        boolean overwriteExisting
) {
    public ProcessingOptions {
        if (resizeMode == null) {
            throw new IllegalArgumentException("resizeMode ne peut pas être null");
        }
        if (outputFormat == null) {
            throw new IllegalArgumentException("outputFormat ne peut pas être null");
        }
        if (percentage < 1 || percentage > 1000) {
            throw new IllegalArgumentException("percentage doit être entre 1 et 1000");
        }
        if (targetWidth < 1 || targetHeight < 1) {
            throw new IllegalArgumentException("targetWidth et targetHeight doivent être >= 1");
        }
        if (qualityPercent < 1 || qualityPercent > 100) {
            throw new IllegalArgumentException("qualityPercent doit être entre 1 et 100");
        }
        if (!useSubfolderCompressed && outputDirectory == null) {
            throw new IllegalArgumentException("outputDirectory est requis si useSubfolderCompressed est false");
        }
        exactRatioMode = exactRatioMode == null ? ExactRatioMode.NONE : exactRatioMode;
        fileNameSuffix = fileNameSuffix == null ? "" : fileNameSuffix;
    }

    public ProcessingOptions(
            ResizeMode resizeMode,
            int percentage,
            int targetWidth,
            int targetHeight,
            boolean allowUpscale,
            OutputFormat outputFormat,
            int qualityPercent,
            Path outputDirectory,
            boolean useSubfolderCompressed,
            String fileNameSuffix,
            boolean overwriteExisting
    ) {
        this(
                resizeMode, percentage, targetWidth, targetHeight,
                ExactRatioMode.NONE, allowUpscale, outputFormat, qualityPercent,
                outputDirectory, useSubfolderCompressed, fileNameSuffix, overwriteExisting
        );
    }
}

