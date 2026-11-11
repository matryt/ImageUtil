package com.imageutil.ui;

import com.imageutil.service.ImageProcessor;

import javax.swing.table.AbstractTableModel;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Modèle de tableau pour la liste des images sélectionnées.
 */
public class ImageTableModel extends AbstractTableModel {

    public static class ImageEntry {
        private final Path path;
        private final String fileName;
        private final Dimension dimension;
        private final String dimensionsText;
        private final String sizeText;
        private final long sizeBytes;

        public ImageEntry(Path path, Dimension dim, long sizeBytes) {
            this.path = path;
            this.fileName = path.getFileName().toString();
            this.dimension = dim;
            this.dimensionsText = (dim != null) ? (dim.width + " × " + dim.height) : "?";
            this.sizeBytes = sizeBytes;
            this.sizeText = ImageProcessor.formatSize(sizeBytes);
        }

        public Path getPath() {
            return path;
        }

        public Dimension getDimension() {
            return dimension;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }
    }

    private static final String[] COLUMNS = {"Fichier", "Dimensions", "Taille", "Emplacement"};
    private final List<ImageEntry> entries = new ArrayList<>();
    private final Set<Path> pathSet = new LinkedHashSet<>();

    public int addFiles(List<Path> paths, ImageProcessor processor) {
        int added = 0;
        for (Path raw : paths) {
            Path normalized = raw.toAbsolutePath().normalize();
            if (!ImageProcessor.isSupportedImage(normalized) || pathSet.contains(normalized)) {
                continue;
            }
            try {
                long size = Files.size(normalized);
                Dimension dim = processor.readImageDimensions(normalized);
                entries.add(new ImageEntry(normalized, dim, size));
                pathSet.add(normalized);
                added++;
            } catch (Exception ignored) {
                // Fichier illisible ignoré
            }
        }
        if (added > 0) {
            fireTableDataChanged();
        }
        return added;
    }

    public void removeIndices(int[] indices) {
        if (indices == null || indices.length == 0) {
            return;
        }
        // Suppression en ordre inverse pour préserver les indices
        java.util.Arrays.sort(indices);
        for (int i = indices.length - 1; i >= 0; i--) {
            int idx = indices[i];
            if (idx >= 0 && idx < entries.size()) {
                ImageEntry removed = entries.remove(idx);
                pathSet.remove(removed.getPath());
            }
        }
        fireTableDataChanged();
    }

    public void clear() {
        entries.clear();
        pathSet.clear();
        fireTableDataChanged();
    }

    public List<Path> getAllPaths() {
        List<Path> result = new ArrayList<>(entries.size());
        for (ImageEntry entry : entries) {
            result.add(entry.getPath());
        }
        return result;
    }

    public Dimension getDimensionAt(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < entries.size()) {
            return entries.get(rowIndex).getDimension();
        }
        return null;
    }

    public Dimension getFirstKnownDimension() {
        for (ImageEntry entry : entries) {
            Dimension d = entry.getDimension();
            if (d != null && d.width > 0 && d.height > 0) {
                return d;
            }
        }
        return null;
    }

    public long getTotalSizeBytes() {
        long sum = 0;
        for (ImageEntry entry : entries) {
            sum += entry.getSizeBytes();
        }
        return sum;
    }

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ImageEntry entry = entries.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> entry.fileName;
            case 1 -> entry.dimensionsText;
            case 2 -> entry.sizeText;
            case 3 -> entry.getPath().getParent() != null ? entry.getPath().getParent().toString() : "";
            default -> "";
        };
    }
}

