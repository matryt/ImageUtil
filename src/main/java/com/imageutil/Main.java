package com.imageutil;

import com.imageutil.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Point d'entrée de l'application ImageUtil.
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Conserve le Look & Feel par défaut si le L&F système échoue
            }
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}

