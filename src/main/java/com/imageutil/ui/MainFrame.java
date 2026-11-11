package com.imageutil.ui;

import com.imageutil.model.ExactRatioMode;
import com.imageutil.model.OutputFormat;
import com.imageutil.model.ProcessingOptions;
import com.imageutil.model.ProcessingResult;
import com.imageutil.model.ResizeMode;
import com.imageutil.service.ImageProcessor;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Fenêtre principale Swing de l'utilitaire ImageUtil.
 */
public class MainFrame extends JFrame {

    private final ImageProcessor processor = new ImageProcessor();
    private final ImageTableModel tableModel = new ImageTableModel();
    private final JTable imageTable = new JTable(tableModel);
    private final JLabel countLabel = new JLabel("0 image(s) sélectionnée(s)");

    // Contrôles de redimensionnement
    private final JComboBox<ResizeMode> resizeModeCombo = new JComboBox<>(ResizeMode.values());
    private final JSpinner percentageSpinner = new JSpinner(new SpinnerNumberModel(50, 1, 500, 5));
    private final JLabel widthLabel = new JLabel("Largeur (px) :");
    private final JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(1920, 1, 20000, 10));
    private final JLabel heightLabel = new JLabel("Hauteur (px) :");
    private final JSpinner heightSpinner = new JSpinner(new SpinnerNumberModel(1080, 1, 20000, 10));
    private final JCheckBox keepAspectRatioCheck = new JCheckBox("Conserver les proportions (ratio)", true);
    private final JRadioButton fixWidthRadio = new JRadioButton("Largeur", true);
    private final JRadioButton fixHeightRadio = new JRadioButton("Hauteur");
    private final JPanel ratioAnchorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JCheckBox allowUpscaleCheck = new JCheckBox("Autoriser l'agrandissement");

    // Contrôles de compression & sortie
    private final JComboBox<OutputFormat> outputFormatCombo = new JComboBox<>(OutputFormat.values());
    private final JLabel qualityLabel = new JLabel("Qualité JPEG :");
    private final JSlider qualitySlider = new JSlider(10, 100, 80);
    private final JLabel qualityValueLabel = new JLabel("80 %");
    private final JRadioButton subfolderRadio = new JRadioButton("Sous-dossier \"compressed\" à côté des originaux",
            true);
    private final JRadioButton customDirRadio = new JRadioButton("Dossier spécifique :");
    private final JTextField customDirField = new JTextField(20);
    private final JButton browseDirButton = new JButton("Parcourir...");
    private final JTextField suffixField = new JTextField("", 8);
    private final JCheckBox overwriteCheck = new JCheckBox("Écraser si existant");

    // Contrôles d'exécution
    private final JButton processButton = new JButton("Traiter les images");
    private final JButton cancelButton = new JButton("Annuler");
    private final JButton openOutputButton = new JButton("Ouvrir dossier de sortie");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JTextArea logArea = new JTextArea(7, 60);

    private SwingWorker<Void, String> currentWorker;
    private Path lastOutputFolder;
    private File lastVisitedDirectory;

    public MainFrame() {
        super("ImageUtil - Redimensionnement & Compression d'images");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(840, 680));
        setPreferredSize(new Dimension(920, 740));

        initLayout();
        initListeners();
        updateResizeControlsState();
        updateQualityControlsState();
        updateDestinationControlsState();

        pack();
        setLocationRelativeTo(null);
    }

    private void initLayout() {
        setLayout(new BorderLayout(8, 8));
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        // --- Haut : Liste des fichiers ---
        root.add(buildFilesPanel(), BorderLayout.CENTER);

        // --- Bas : Options + Exécution + Logs ---
        JPanel bottomContainer = new JPanel(new BorderLayout(8, 8));
        bottomContainer.add(buildOptionsPanel(), BorderLayout.NORTH);
        bottomContainer.add(buildExecutionPanel(), BorderLayout.CENTER);
        root.add(bottomContainer, BorderLayout.SOUTH);
    }

    private JPanel buildFilesPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("1. Images à traiter (glisser-déposer supporté)"));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton addFilesBtn = new JButton("Ajouter des images...");
        JButton addFolderBtn = new JButton("Ajouter un dossier...");
        JButton removeBtn = new JButton("Retirer la sélection");
        JButton clearBtn = new JButton("Tout vider");

        addFilesBtn.addActionListener(e -> chooseImages());
        addFolderBtn.addActionListener(e -> chooseFolder());
        removeBtn.addActionListener(e -> {
            tableModel.removeIndices(imageTable.getSelectedRows());
            updateCountLabel();
        });
        clearBtn.addActionListener(e -> {
            tableModel.clear();
            updateCountLabel();
        });

        toolbar.add(addFilesBtn);
        toolbar.add(addFolderBtn);
        toolbar.add(removeBtn);
        toolbar.add(clearBtn);
        toolbar.add(countLabel);

        imageTable.setFillsViewportHeight(true);
        imageTable.setRowHeight(22);
        imageTable.getColumnModel().getColumn(0).setPreferredWidth(220);
        imageTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        imageTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        imageTable.getColumnModel().getColumn(3).setPreferredWidth(360);

        JScrollPane tableScroll = new JScrollPane(imageTable);
        enableDragAndDrop(tableScroll);
        enableDragAndDrop(imageTable);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(tableScroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildOptionsPanel() {
        JPanel container = new JPanel(new GridLayout(1, 2, 10, 0));
        container.add(buildResizePanel());
        container.add(buildCompressionAndOutputPanel());
        return container;
    }

    private JPanel buildResizePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("2. Redimensionnement"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Mode :"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        resizeModeCombo.setSelectedItem(ResizeMode.NONE);
        panel.add(resizeModeCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Pourcentage (%) :"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(percentageSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        panel.add(keepAspectRatioCheck, gbc);

        ButtonGroup anchorGroup = new ButtonGroup();
        anchorGroup.add(fixWidthRadio);
        anchorGroup.add(fixHeightRadio);
        ratioAnchorPanel.add(new JLabel("Dimension fixe :"));
        ratioAnchorPanel.add(fixWidthRadio);
        ratioAnchorPanel.add(fixHeightRadio);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        panel.add(ratioAnchorPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(widthLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(widthSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0;
        panel.add(heightLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(heightSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        panel.add(allowUpscaleCheck, gbc);

        return panel;
    }

    private JPanel buildCompressionAndOutputPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("3. Compression & Destination"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Format
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Format :"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        outputFormatCombo.setSelectedItem(OutputFormat.ORIGINAL);
        panel.add(outputFormatCombo, gbc);

        // Qualité (affichée uniquement en cas de format JPEG)
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(qualityLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        qualitySlider.setMajorTickSpacing(30);
        qualitySlider.setMinorTickSpacing(10);
        qualitySlider.setPaintTicks(true);
        panel.add(qualitySlider, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        qualityValueLabel.setPreferredSize(new Dimension(48, 20));
        panel.add(qualityValueLabel, gbc);

        // Destination
        ButtonGroup destGroup = new ButtonGroup();
        destGroup.add(subfolderRadio);
        destGroup.add(customDirRadio);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        panel.add(subfolderRadio, gbc);

        gbc.gridy = 3;
        gbc.gridwidth = 1;
        panel.add(customDirRadio, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(customDirField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(browseDirButton, gbc);

        // Suffixe & écrasement
        JPanel extraRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        extraRow.add(new JLabel("Suffixe nom :"));
        extraRow.add(suffixField);
        extraRow.add(overwriteCheck);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        panel.add(extraRow, gbc);

        return panel;
    }

    private JPanel buildExecutionPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("4. Exécution & Journal"));

        JPanel actionRow = new JPanel(new BorderLayout(8, 0));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        processButton.setFont(processButton.getFont().deriveFont(Font.BOLD));
        cancelButton.setEnabled(false);
        openOutputButton.setEnabled(false);

        buttons.add(processButton);
        buttons.add(cancelButton);
        buttons.add(openOutputButton);

        progressBar.setStringPainted(true);
        progressBar.setString("Prêt");

        actionRow.add(buttons, BorderLayout.WEST);
        actionRow.add(progressBar, BorderLayout.CENTER);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(new Color(248, 248, 248));
        JScrollPane logScroll = new JScrollPane(logArea);

        panel.add(actionRow, BorderLayout.NORTH);
        panel.add(logScroll, BorderLayout.CENTER);
        return panel;
    }

    private void initListeners() {
        resizeModeCombo.addActionListener(e -> updateResizeControlsState());
        keepAspectRatioCheck.addActionListener(e -> updateResizeControlsState());
        fixWidthRadio.addActionListener(e -> updateResizeControlsState());
        fixHeightRadio.addActionListener(e -> updateResizeControlsState());

        outputFormatCombo.addActionListener(e -> updateQualityControlsState());
        qualitySlider.addChangeListener(e -> qualityValueLabel.setText(qualitySlider.getValue() + " %"));

        subfolderRadio.addActionListener(e -> updateDestinationControlsState());
        customDirRadio.addActionListener(e -> updateDestinationControlsState());
        browseDirButton.addActionListener(e -> chooseOutputDirectory());

        processButton.addActionListener(e -> startProcessing());
        cancelButton.addActionListener(e -> {
            if (currentWorker != null) {
                currentWorker.cancel(true);
                cancelButton.setEnabled(false);
            }
        });
        openOutputButton.addActionListener(e -> openLastOutputFolder());
    }

    private void updateResizeControlsState() {
        ResizeMode mode = (ResizeMode) resizeModeCombo.getSelectedItem();
        boolean isPercent = mode == ResizeMode.PERCENTAGE;
        boolean isExact = mode == ResizeMode.EXACT;
        boolean isDims = mode == ResizeMode.FIT_MAX || isExact;
        boolean isExactRatio = isExact && keepAspectRatioCheck.isSelected();
        boolean isActive = mode != ResizeMode.NONE;

        boolean showWidth = !isExactRatio || fixWidthRadio.isSelected();
        boolean showHeight = !isExactRatio || fixHeightRadio.isSelected();

        percentageSpinner.setEnabled(isPercent);
        keepAspectRatioCheck.setVisible(isExact);
        ratioAnchorPanel.setVisible(isExactRatio);
        widthLabel.setVisible(showWidth);
        widthSpinner.setVisible(showWidth);
        widthSpinner.setEnabled(isDims && showWidth);
        heightLabel.setVisible(showHeight);
        heightSpinner.setVisible(showHeight);
        heightSpinner.setEnabled(isDims && showHeight);
        allowUpscaleCheck.setEnabled(isActive);

        if (keepAspectRatioCheck.getParent() != null) {
            keepAspectRatioCheck.getParent().revalidate();
            keepAspectRatioCheck.getParent().repaint();
        }
    }

    private void updateQualityControlsState() {
        OutputFormat fmt = (OutputFormat) outputFormatCombo.getSelectedItem();
        boolean isJpeg = fmt == OutputFormat.JPEG;
        qualityLabel.setVisible(isJpeg);
        qualitySlider.setVisible(isJpeg);
        qualityValueLabel.setVisible(isJpeg);
        if (isJpeg) {
            qualityValueLabel.setText(qualitySlider.getValue() + " %");
        }
        if (qualitySlider.getParent() != null) {
            qualitySlider.getParent().revalidate();
            qualitySlider.getParent().repaint();
        }
    }

    private void updateDestinationControlsState() {
        boolean custom = customDirRadio.isSelected();
        customDirField.setEnabled(custom);
        browseDirButton.setEnabled(custom);
    }

    private void chooseImages() {
        JFileChooser chooser = new JFileChooser(lastVisitedDirectory);
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Images (JPG, PNG, BMP, GIF)", "jpg", "jpeg", "png", "bmp", "gif"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File[] selected = chooser.getSelectedFiles();
            if (selected.length > 0) {
                lastVisitedDirectory = selected[0].getParentFile();
            }
            List<Path> paths = new ArrayList<>();
            for (File f : selected) {
                paths.add(f.toPath());
            }
            tableModel.addFiles(paths, processor);
            updateCountLabel();
        }
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser(lastVisitedDirectory);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dir = chooser.getSelectedFile();
            lastVisitedDirectory = dir;
            addImagesFromDirectory(dir.toPath());
        }
    }

    private void addImagesFromDirectory(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = stream.filter(ImageProcessor::isSupportedImage).sorted().toList();
            tableModel.addFiles(files, processor);
            updateCountLabel();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Impossible de lire le dossier : " + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void chooseOutputDirectory() {
        JFileChooser chooser = new JFileChooser(lastVisitedDirectory);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dir = chooser.getSelectedFile();
            lastVisitedDirectory = dir;
            customDirField.setText(dir.getAbsolutePath());
        }
    }

    private void enableDragAndDrop(java.awt.Component component) {
        new DropTarget(component, new java.awt.dnd.DropTargetAdapter() {
            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent event) {
                try {
                    event.acceptDrop(DnDConstants.ACTION_COPY);
                    Object transferData = event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (transferData instanceof List<?> droppedList) {
                        List<Path> filesToAdd = new ArrayList<>();
                        for (Object item : droppedList) {
                            if (item instanceof File file) {
                                Path p = file.toPath();
                                if (Files.isDirectory(p)) {
                                    try (Stream<Path> stream = Files.list(p)) {
                                        stream.filter(ImageProcessor::isSupportedImage).forEach(filesToAdd::add);
                                    }
                                } else {
                                    filesToAdd.add(p);
                                }
                            }
                        }
                        tableModel.addFiles(filesToAdd, processor);
                        updateCountLabel();
                        event.dropComplete(true);
                    } else {
                        event.dropComplete(false);
                    }
                } catch (Exception ex) {
                    event.dropComplete(false);
                }
            }
        });
    }

    private void updateCountLabel() {
        int count = tableModel.getRowCount();
        long totalBytes = tableModel.getTotalSizeBytes();
        countLabel.setText(count + " image(s) (" + ImageProcessor.formatSize(totalBytes) + ")");
    }

    private void startProcessing() {
        List<Path> files = tableModel.getAllPaths();
        if (files.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Veuillez ajouter au moins une image à traiter.",
                    "Aucune image", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean useSubfolder = subfolderRadio.isSelected();
        Path customDir = null;
        if (!useSubfolder) {
            String dirText = customDirField.getText().trim();
            if (dirText.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Veuillez choisir un dossier de destination.",
                        "Dossier manquant", JOptionPane.WARNING_MESSAGE);
                return;
            }
            customDir = Path.of(dirText);
        }

        ResizeMode selectedResizeMode = (ResizeMode) resizeModeCombo.getSelectedItem();
        ExactRatioMode exactRatioMode = ExactRatioMode.NONE;
        if (selectedResizeMode == ResizeMode.EXACT && keepAspectRatioCheck.isSelected()) {
            exactRatioMode = fixWidthRadio.isSelected()
                    ? ExactRatioMode.FIXED_WIDTH
                    : ExactRatioMode.FIXED_HEIGHT;
        }

        ProcessingOptions options = new ProcessingOptions(
                selectedResizeMode,
                (Integer) percentageSpinner.getValue(),
                (Integer) widthSpinner.getValue(),
                (Integer) heightSpinner.getValue(),
                exactRatioMode,
                allowUpscaleCheck.isSelected(),
                (OutputFormat) outputFormatCombo.getSelectedItem(),
                qualitySlider.getValue(),
                customDir,
                useSubfolder,
                suffixField.getText().trim(),
                overwriteCheck.isSelected());

        logArea.setText("");
        progressBar.setMaximum(files.size());
        progressBar.setValue(0);
        progressBar.setString("0 / " + files.size());
        setRunningState(true);

        currentWorker = new SwingWorker<>() {
            private int successCount = 0;
            private int errorCount = 0;
            private long totalOriginal = 0;
            private long totalNew = 0;

            @Override
            protected Void doInBackground() {
                for (int i = 0; i < files.size(); i++) {
                    if (isCancelled()) {
                        publish("[ANNULÉ] Traitement interrompu par l'utilisateur.");
                        break;
                    }
                    Path input = files.get(i);
                    ProcessingResult result = processor.process(input, options);
                    if (result.success()) {
                        successCount++;
                        totalOriginal += result.originalSizeBytes();
                        totalNew += result.newSizeBytes();
                        if (result.outputFile() != null && result.outputFile().getParent() != null) {
                            lastOutputFolder = result.outputFile().getParent();
                        }
                        publish(String.format(Locale.FRANCE,
                                "[OK] %s : %dx%d -> %dx%d | %s -> %s (%.1f%%)",
                                input.getFileName(),
                                result.originalWidth(), result.originalHeight(),
                                result.newWidth(), result.newHeight(),
                                ImageProcessor.formatSize(result.originalSizeBytes()),
                                ImageProcessor.formatSize(result.newSizeBytes()),
                                -result.compressionRatioPercent()));
                    } else {
                        errorCount++;
                        publish("[ERREUR] " + input.getFileName() + " : " + result.errorMessage());
                    }
                    final int doneCount = i + 1;
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(doneCount);
                        progressBar.setString(doneCount + " / " + files.size());
                    });
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    logArea.append(line + "\n");
                }
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                setRunningState(false);
                double savedPct = totalOriginal > 0
                        ? (1.0 - ((double) totalNew / totalOriginal)) * 100.0
                        : 0.0;
                logArea.append(String.format(Locale.FRANCE,
                        "\n--- Terminé : %d réussie(s), %d erreur(s) | Total : %s -> %s (économie : %.1f%%) ---\n",
                        successCount, errorCount,
                        ImageProcessor.formatSize(totalOriginal),
                        ImageProcessor.formatSize(totalNew),
                        savedPct));
                openOutputButton.setEnabled(lastOutputFolder != null && Files.isDirectory(lastOutputFolder));
            }
        };

        currentWorker.execute();
    }

    private void setRunningState(boolean running) {
        processButton.setEnabled(!running);
        cancelButton.setEnabled(running);
        if (running) {
            openOutputButton.setEnabled(false);
        }
    }

    private void openLastOutputFolder() {
        if (lastOutputFolder == null || !Files.isDirectory(lastOutputFolder)) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(lastOutputFolder.toFile());
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Impossible d'ouvrir le dossier : " + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }
}
