package com.imageutil.model;

/**
 * Modes de redimensionnement supportés.
 */
public enum ResizeMode {
    NONE("Aucun redimensionnement"),
    PERCENTAGE("Pourcentage (%)"),
    FIT_MAX("Dimensions max (ratio conservé)"),
    EXACT("Dimensions exactes (px)");

    private final String label;

    ResizeMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}

