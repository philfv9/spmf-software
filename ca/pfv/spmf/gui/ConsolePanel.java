package ca.pfv.spmf.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import ca.pfv.spmf.gui.preferences.PreferencesManager;

/*
 * Copyright (c) 2008-2015 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * A console panel used by the SPMF GUI. Displays text output with support for
 * status messages, font resizing, clipboard operations, and log saving.
 *
 * @author Philippe Fournier-Viger
 */
public class ConsolePanel extends JPanel {

    private static final long serialVersionUID = 8344817215180749939L;

    /** Color for status messages */
    private static final Color STATUS_MESSAGE_COLOR = Color.GRAY;
    /** Color for regular messages */
    private static final Color REGULAR_MESSAGE_COLOR = Color.BLACK;
    /** Minimum allowed font size */
    private static final int MIN_FONT_SIZE = 6;
    /** Maximum allowed font size */
    private static final int MAX_FONT_SIZE = 72;
    /** Maximum number of characters before the console is auto-trimmed */
    private static final int MAX_DOCUMENT_LENGTH = 500_000;
    /** System line separator */
    private static final String NEWLINE = System.lineSeparator();

    /** Pre-built attribute set for regular messages (immutable after init) */
    private final SimpleAttributeSet regularAttributes;
    /** Pre-built attribute set for status messages (immutable after init) */
    private final SimpleAttributeSet statusAttributes;

    /** Text pane to show console output */
    private final JTextPane textPane;
    /** The styled document backing the text pane */
    private final StyledDocument doc;
    /** The original System.out, preserved so it can be restored */
    private final PrintStream originalOut;

    /**
     * Constructs a new ConsolePanel.
     *
     * @param showClearButton if true, a "Clear Console" button is shown at the bottom
     */
    public ConsolePanel(boolean showClearButton) {
        // Pre-build reusable attribute sets
        regularAttributes = new SimpleAttributeSet();
        StyleConstants.setForeground(regularAttributes, REGULAR_MESSAGE_COLOR);

        statusAttributes = new SimpleAttributeSet();
        StyleConstants.setForeground(statusAttributes, STATUS_MESSAGE_COLOR);

        originalOut = System.out;

        textPane = createTextPane();
        doc = textPane.getStyledDocument();

        setupLayout(showClearButton);
        setupContextMenu();
    }

    /**
     * Redirects {@link System#out} to this console panel.
     * Call {@link #restoreOutputStream()} to undo.
     */
    public void redirectOutputStream() {
        System.setOut(new PrintStream(new TextPaneOutputStream(), true));
    }

    /**
     * Restores {@link System#out} to the original stream captured at construction time.
     */
    public void restoreOutputStream() {
        System.setOut(originalOut);
    }

    /**
     * Posts a grey status message (e.g. "Algorithm is running…").
     *
     * @param message the message to display
     */
    public void postStatusMessage(String message) {
        appendToDocument(message + NEWLINE, statusAttributes);
    }

    /**
     * Appends a line of text followed by a newline to the console.
     *
     * @param line the text to append
     */
    public void appendLine(String line) {
        appendToDocument(line + NEWLINE, regularAttributes);
    }

    /**
     * Clears all text from the console.
     */
    public void clearConsole() {
        if (SwingUtilities.isEventDispatchThread()) {
            textPane.setText("");
        } else {
            SwingUtilities.invokeLater(() -> textPane.setText(""));
        }
    }

    /**
     * Copies the currently selected text to the system clipboard.
     */
    public void copyText() {
        String text = textPane.getSelectedText();
        if (text != null && !text.isEmpty()) {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(text), null);
        }
    }

    // -----------------------------------------------------------------------
    //  Private helpers
    // -----------------------------------------------------------------------

    /**
     * Creates and configures the text pane, including font preferences.
     */
    private JTextPane createTextPane() {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setBackground(Color.WHITE);

        Integer fontSize = PreferencesManager.getInstance().getConsoleFontSize();
        if (fontSize != null && fontSize >= MIN_FONT_SIZE && fontSize <= MAX_FONT_SIZE) {
            pane.setFont(pane.getFont().deriveFont((float) fontSize));
        }
        return pane;
    }

    /**
     * Lays out the scroll pane (center) and optional clear button (south).
     */
    private void setupLayout(boolean showClearButton) {
        setLayout(new BorderLayout());
        add(new JScrollPane(textPane), BorderLayout.CENTER);

        if (showClearButton) {
            JButton clearButton = new JButton("Clear Console");
            clearButton.addActionListener(e -> clearConsole());
            add(clearButton, BorderLayout.SOUTH);
        }
    }

    /**
     * Installs a right-click context menu on the text pane.
     */
    private void setupContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem clearItem = new JMenuItem("Clear");
        clearItem.addActionListener(e -> clearConsole());
        menu.add(clearItem);

        JMenuItem saveItem = new JMenuItem("Save Log to File");
        saveItem.addActionListener(e -> saveLog());
        menu.add(saveItem);

        menu.addSeparator();
        menu.add(createFontSizeMenu());

        textPane.setComponentPopupMenu(menu);
    }

    /**
     * Creates a submenu with Increase / Decrease font-size options.
     */
    private JMenu createFontSizeMenu() {
        JMenu menu = new JMenu("Font Size");

        JMenuItem increase = new JMenuItem("Increase");
        increase.addActionListener(e -> changeFontSize(1));
        menu.add(increase);

        JMenuItem decrease = new JMenuItem("Decrease");
        decrease.addActionListener(e -> changeFontSize(-1));
        menu.add(decrease);

        return menu;
    }

    /**
     * Adjusts the font size by {@code delta} points, clamped to
     * [{@value #MIN_FONT_SIZE}, {@value #MAX_FONT_SIZE}].
     */
    private void changeFontSize(int delta) {
        Font current = textPane.getFont();
        int newSize = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, current.getSize() + delta));
        textPane.setFont(current.deriveFont((float) newSize));
        PreferencesManager.getInstance().setConsoleFontSize(newSize);
    }

    /**
     * Prompts the user for a file and writes the current console text to it.
     */
    private void saveLog() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
            out.print(textPane.getText());
        } catch (IOException ex) {
            originalOut.println("Error saving log: " + ex.getMessage());
        }
    }

    /**
     * Thread-safe helper that appends styled text to the document and auto-scrolls.
     * Trims the beginning of the document if it exceeds {@value #MAX_DOCUMENT_LENGTH}.
     */
    private void appendToDocument(String text, SimpleAttributeSet attributes) {
        Runnable task = () -> {
            try {
                // Trim document if it's getting too large
                if (doc.getLength() > MAX_DOCUMENT_LENGTH) {
                    int excess = doc.getLength() - MAX_DOCUMENT_LENGTH / 2;
                    doc.remove(0, excess);
                }
                doc.insertString(doc.getLength(), text, attributes);
                // Auto-scroll to the bottom
                textPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                originalOut.println("Console append error: " + e.getMessage());
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    // -----------------------------------------------------------------------
    //  Inner class – buffered OutputStream → JTextPane
    // -----------------------------------------------------------------------

    /**
     * An {@link OutputStream} that buffers bytes into complete chunks and flushes
     * them to the text pane. Much more efficient than writing one byte at a time,
     * because each Swing update can handle an entire string instead of a single
     * character.
     */
    private class TextPaneOutputStream extends OutputStream {

        private final StringBuilder buffer = new StringBuilder(256);

        @Override
        public synchronized void write(int b) {
            buffer.append((char) b);
            if (b == '\n') {
                flushBuffer();
            }
        }

        @Override
        public synchronized void write(byte[] data, int off, int len) {
            buffer.append(new String(data, off, len));
            if (buffer.indexOf("\n") != -1) {
                flushBuffer();
            }
        }

        @Override
        public synchronized void flush() {
            if (buffer.length() > 0) {
                flushBuffer();
            }
        }

        /**
         * Sends the current buffer content to the document and clears the buffer.
         */
        private void flushBuffer() {
            String text = buffer.toString();
            buffer.setLength(0);
            appendToDocument(text, regularAttributes);
        }
    }

    // -----------------------------------------------------------------------
    //  Test / demo
    // -----------------------------------------------------------------------

    /**
     * Simple demo that creates a frame with a ConsolePanel and prints some text.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("ConsolePanel Test");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            ConsolePanel console = new ConsolePanel(true);
            console.redirectOutputStream();

            frame.add(console, BorderLayout.CENTER);
            frame.setSize(600, 400);
            frame.setVisible(true);

            console.postStatusMessage("Algorithm is running...");
            System.out.println("Regular output line 1");
            System.out.println("Regular output line 2");
            console.postStatusMessage("Algorithm stopped.");
        });
    }
}