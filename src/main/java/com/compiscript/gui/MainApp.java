package com.compiscript.gui;

import javax.swing.*;

/** Application entry point. */
public final class MainApp {

    private MainApp() {
    }

    public static void main(String[] args) {
        applyNimbusLookAndFeel();
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }

    private static void applyNimbusLookAndFeel() {
        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
                try {
                    UIManager.setLookAndFeel(info.getClassName());
                } catch (Exception ignored) {
                    // Keep default look and feel if Nimbus is unavailable.
                }
                return;
            }
        }
    }
}
