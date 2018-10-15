package org.beryx.swing.handler;

import org.beryx.textio.swing.SwingTextTerminal;

import javax.swing.*;
import java.awt.event.*;
import java.util.logging.Logger;

public class MarsTerminal extends SwingTextTerminal {
    private static Logger logger = Logger.getLogger(MarsTerminal.class.getName());

    private final JPopupMenu popup = new JPopupMenu();

    private static class PopupListener extends MouseAdapter {
        private final JPopupMenu popup;

        public PopupListener(JPopupMenu popup) {
            this.popup = popup;
        }

        public void mousePressed(MouseEvent e) {
            maybeShowPopup(e);
        }

        public void mouseReleased(MouseEvent e) {
            maybeShowPopup(e);
        }

        private void maybeShowPopup(MouseEvent e) {
            if (e.isPopupTrigger()) {
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        }
    }

    public MarsTerminal() {
        configureMainMenu();

        JTextPane textPane = getTextPane();
        addAction("ctrl C", "Copy", () -> textPane.copy());
        addAction("ctrl V", "Paste", () -> textPane.paste());
        MouseListener popupListener = new PopupListener(popup);
        textPane.addMouseListener(popupListener);
    }

    private void configureMainMenu() {
        JFrame frame = getFrame();
        frame.setTitle("Mars Simulation");
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("Help");
        menu.setMnemonic(KeyEvent.VK_H);

        JMenuItem menuItem = new JMenuItem("About", KeyEvent.VK_A);
        menuItem.addActionListener(e -> JOptionPane.showMessageDialog(frame, "The Mars Simulation Project"));
        menu.add(menuItem);

        menuBar.add(menu);
        frame.setJMenuBar(menuBar);
    }

    private boolean addAction(String keyStroke, String menuText, Runnable action) {
        KeyStroke ks = KeyStroke.getKeyStroke(keyStroke);
        if(ks == null) {
            logger.warning("Invalid keyStroke: " + keyStroke);
            return false;
        }
        JMenuItem menuItem = new JMenuItem(menuText);
        menuItem.addActionListener(e -> action.run());
        popup.add(menuItem);

        JTextPane textPane = getTextPane();
        String actionKey = "MarsTerminal." + keyStroke.replaceAll("\\s", "-");
        textPane.getInputMap().put(ks, actionKey);
        textPane.getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
        return true;
    }
}
