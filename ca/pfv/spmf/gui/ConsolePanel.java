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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
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
 * <p>
 * This panel can redirect System.out to display program output in real-time,
 * with automatic scrolling, text trimming for large outputs, and context menu
 * operations for user convenience.
 * </p>
 *
 * @author Philippe Fournier-Viger
 */
public class ConsolePanel extends JPanel {

	private static final long serialVersionUID = 8344817215180749939L;

	// -----------------------------------------------------------------------
	// Configuration constants
	// -----------------------------------------------------------------------

	/** Color for status messages */
	private static final Color STATUS_MESSAGE_COLOR = Color.GRAY;

	/** Color for regular messages */
	private static final Color REGULAR_MESSAGE_COLOR = Color.BLACK;

	/** Minimum allowed font size */
	private static final int MIN_FONT_SIZE = 6;

	/** Maximum allowed font size */
	private static final int MAX_FONT_SIZE = 72;

	/** Default font size if none is set in preferences */
	private static final int DEFAULT_FONT_SIZE = 12;

	/** Maximum number of characters before the console is auto-trimmed */
	private static final int MAX_DOCUMENT_LENGTH = 500_000;

	/** When trimming, reduce to this size */
	private static final int TRIM_TARGET_LENGTH = MAX_DOCUMENT_LENGTH / 2;

	/** System line separator */
	private static final String NEWLINE = System.lineSeparator();

	/** Initial buffer capacity for the output stream */
	private static final int BUFFER_INITIAL_CAPACITY = 256;

	// -----------------------------------------------------------------------
	// Instance fields
	// -----------------------------------------------------------------------

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

	/** The original System.err, preserved so it can be restored */
	private final PrintStream originalErr;

	/** Our custom output stream redirector */
	private TextPaneOutputStream outputStream;

	// -----------------------------------------------------------------------
	// Constructor
	// -----------------------------------------------------------------------

	/**
	 * Constructs a new ConsolePanel.
	 *
	 * @param showClearButton if true, a "Clear Console" button is shown at the
	 *                        bottom
	 */
	public ConsolePanel(boolean showClearButton) {
		// Pre-build reusable attribute sets to avoid repeated object creation
		regularAttributes = new SimpleAttributeSet();
		StyleConstants.setForeground(regularAttributes, REGULAR_MESSAGE_COLOR);

		statusAttributes = new SimpleAttributeSet();
		StyleConstants.setForeground(statusAttributes, STATUS_MESSAGE_COLOR);

		// Preserve original streams for restoration
		originalOut = System.out;
		originalErr = System.err;

		// Create and configure UI components
		textPane = createTextPane();
		doc = textPane.getStyledDocument();

		setupLayout(showClearButton);
		setupContextMenu();
	}

	// -----------------------------------------------------------------------
	// Public API
	// -----------------------------------------------------------------------

	/**
	 * Redirects {@link System#out} and {@link System#err} to this console panel.
	 * Call {@link #restoreOutputStream()} to undo.
	 */
	public void redirectOutputStream() {
		if (outputStream == null) {
			outputStream = new TextPaneOutputStream();
		}
		PrintStream ps = new PrintStream(outputStream, true);
		System.setOut(ps);
		System.setErr(ps);
	}

	/**
	 * Restores {@link System#out} and {@link System#err} to the original streams
	 * captured at construction time.
	 */
	public void restoreOutputStream() {
		System.setOut(originalOut);
		System.setErr(originalErr);
		if (outputStream != null) {
			outputStream.flush();
		}
	}

	/**
	 * Posts a grey status message (e.g. "Algorithm is running…").
	 *
	 * @param message the message to display
	 */
	public void postStatusMessage(String message) {
		if (message == null) {
			return;
		}
		appendToDocument(message + NEWLINE, statusAttributes);
	}

	/**
	 * Appends a line of text followed by a newline to the console.
	 *
	 * @param line the text to append
	 */
	public void appendLine(String line) {
		if (line == null) {
			return;
		}
		appendToDocument(line + NEWLINE, regularAttributes);
	}

	/**
	 * Clears all text from the console.
	 */
	public void clearConsole() {
		SwingUtilities.invokeLater(() -> {
			textPane.setText("");
			// Force garbage collection of the old document content
			System.gc();
		});
	}

	/**
	 * Copies the currently selected text to the system clipboard.
	 */
	public void copyText() {
		String text = textPane.getSelectedText();
		if (text != null && !text.isEmpty()) {
			try {
				Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
				clipboard.setContents(new StringSelection(text), null);
			} catch (IllegalStateException e) {
				// Clipboard unavailable - ignore
				originalOut.println("Clipboard unavailable: " + e.getMessage());
			}
		}
	}

	/**
	 * Selects all text in the console.
	 */
	public void selectAll() {
		textPane.selectAll();
	}

	/**
	 * Returns the current console text.
	 * 
	 * @return the complete text content
	 */
	public String getText() {
		return textPane.getText();
	}

	// -----------------------------------------------------------------------
	// Private helpers - UI setup
	// -----------------------------------------------------------------------

	/**
	 * Creates and configures the text pane, including font preferences.
	 */
	private JTextPane createTextPane() {
		JTextPane pane = new JTextPane();
		pane.setEditable(false);
		pane.setBackground(Color.WHITE);

		// Load font size from preferences
		Integer fontSize = PreferencesManager.getInstance().getConsoleFontSize();
		if (fontSize == null || fontSize < MIN_FONT_SIZE || fontSize > MAX_FONT_SIZE) {
			fontSize = DEFAULT_FONT_SIZE;
		}
		pane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));

		// Enable auto-scrolling
		DefaultCaret caret = (DefaultCaret) pane.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

		return pane;
	}

	/**
	 * Lays out the scroll pane (center) and optional clear button (south).
	 */
	private void setupLayout(boolean showClearButton) {
		setLayout(new BorderLayout());

		JScrollPane scrollPane = new JScrollPane(textPane);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		add(scrollPane, BorderLayout.CENTER);

		if (showClearButton) {
			JButton clearButton = new JButton("Clear Console");
			clearButton.addActionListener(e -> clearConsole());
			clearButton.setFocusPainted(false);
			add(clearButton, BorderLayout.SOUTH);
		}
	}

	/**
	 * Installs a right-click context menu on the text pane.
	 */
	private void setupContextMenu() {
		JPopupMenu menu = new JPopupMenu();

		JMenuItem selectAllItem = new JMenuItem("Select All");
		selectAllItem.addActionListener(e -> selectAll());
		menu.add(selectAllItem);

		JMenuItem copyItem = new JMenuItem("Copy");
		copyItem.addActionListener(e -> copyText());
		menu.add(copyItem);

		menu.addSeparator();

		JMenuItem clearItem = new JMenuItem("Clear");
		clearItem.addActionListener(e -> clearConsole());
		menu.add(clearItem);

		JMenuItem saveItem = new JMenuItem("Save Log to File...");
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
		increase.addActionListener(e -> changeFontSize(2));
		menu.add(increase);

		JMenuItem decrease = new JMenuItem("Decrease");
		decrease.addActionListener(e -> changeFontSize(-2));
		menu.add(decrease);

		menu.addSeparator();

		JMenuItem reset = new JMenuItem("Reset to Default");
		reset.addActionListener(e -> resetFontSize());
		menu.add(reset);

		return menu;
	}

	// -----------------------------------------------------------------------
	// Private helpers - Font management
	// -----------------------------------------------------------------------

	/**
	 * Adjusts the font size by {@code delta} points, clamped to
	 * [{@value #MIN_FONT_SIZE}, {@value #MAX_FONT_SIZE}].
	 */
	private void changeFontSize(int delta) {
		Font current = textPane.getFont();
		int newSize = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, current.getSize() + delta));
		if (newSize != current.getSize()) {
			textPane.setFont(current.deriveFont((float) newSize));
			PreferencesManager.getInstance().setConsoleFontSize(newSize);
		}
	}

	/**
	 * Resets font size to the default value.
	 */
	private void resetFontSize() {
		Font current = textPane.getFont();
		textPane.setFont(current.deriveFont((float) DEFAULT_FONT_SIZE));
		PreferencesManager.getInstance().setConsoleFontSize(DEFAULT_FONT_SIZE);
	}

	// -----------------------------------------------------------------------
	// Private helpers - File operations
	// -----------------------------------------------------------------------

	/**
	 * Prompts the user for a file and writes the current console text to it.
	 */
	private void saveLog() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Save Console Log");
		chooser.setSelectedFile(new File("console_log.txt"));

		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}

		File file = chooser.getSelectedFile();

		// Confirm overwrite if file exists
		if (file.exists()) {
			int result = JOptionPane.showConfirmDialog(this, "File already exists. Overwrite?", "Confirm Overwrite",
					JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (result != JOptionPane.YES_OPTION) {
				return;
			}
		}

		try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
			out.print(textPane.getText());
			JOptionPane.showMessageDialog(this, "Log saved successfully to:\n" + file.getAbsolutePath(),
					"Save Successful", JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this, "Error saving log:\n" + ex.getMessage(), "Save Error",
					JOptionPane.ERROR_MESSAGE);
			originalOut.println("Error saving log: " + ex.getMessage());
		}
	}

	// -----------------------------------------------------------------------
	// Private helpers - Document management
	// -----------------------------------------------------------------------

	/**
	 * Thread-safe helper that appends styled text to the document and auto-scrolls.
	 * Trims the beginning of the document if it exceeds
	 * {@value #MAX_DOCUMENT_LENGTH}.
	 */
	private void appendToDocument(String text, SimpleAttributeSet attributes) {
		if (text == null || text.isEmpty()) {
			return;
		}

		Runnable task = () -> {
			try {
				// Trim document if it's getting too large
				int currentLength = doc.getLength();
				if (currentLength > MAX_DOCUMENT_LENGTH) {
					int excess = currentLength - TRIM_TARGET_LENGTH;
					doc.remove(0, excess);
				}

				// Append new text
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
	// Inner class – buffered OutputStream → JTextPane
	// -----------------------------------------------------------------------

	/**
	 * An {@link OutputStream} that buffers bytes into complete chunks and flushes
	 * them to the text pane. Much more efficient than writing one byte at a time,
	 * because each Swing update can handle an entire string instead of a single
	 * character.
	 */
	private class TextPaneOutputStream extends OutputStream {

		private final StringBuilder buffer = new StringBuilder(BUFFER_INITIAL_CAPACITY);

		@Override
		public synchronized void write(int b) {
			buffer.append((char) b);
			// Flush on newline for immediate feedback
			if (b == '\n') {
				flushBuffer();
			}
		}

		@Override
		public synchronized void write(byte[] data, int off, int len) {
			if (data == null) {
				throw new NullPointerException();
			}
			if (off < 0 || len < 0 || off + len > data.length) {
				throw new IndexOutOfBoundsException();
			}

			buffer.append(new String(data, off, len));

			// Flush if we have a newline
			if (buffer.indexOf(NEWLINE) != -1) {
				flushBuffer();
			}
		}

		@Override
		public synchronized void flush() {
			if (buffer.length() > 0) {
				flushBuffer();
			}
		}

		@Override
		public synchronized void close() throws IOException {
			flush();
			super.close();
		}

		/**
		 * Sends the current buffer content to the document and clears the buffer.
		 */
		private void flushBuffer() {
			if (buffer.length() == 0) {
				return;
			}
			String text = buffer.toString();
			buffer.setLength(0);
			appendToDocument(text, regularAttributes);
		}
	}

	// -----------------------------------------------------------------------
	// Test / demo
	// -----------------------------------------------------------------------

	/**
	 * Simple demo that creates a frame with a ConsolePanel and prints some text.
	 * 
	 * @param args command-line arguments (not used)
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("ConsolePanel Test");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

			ConsolePanel console = new ConsolePanel(true);
			console.redirectOutputStream();

			frame.add(console, BorderLayout.CENTER);
			frame.setSize(700, 500);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);

			// Test output
			console.postStatusMessage("Console initialized.");
			System.out.println("Regular output line 1");
			System.out.println("Regular output line 2");
			System.out.println("Regular output line 3");
			console.postStatusMessage("Ready.");

			// Test bulk output
			new Thread(() -> {
				try {
					Thread.sleep(1000);
					console.postStatusMessage("Starting bulk output test...");
					for (int i = 0; i < 100; i++) {
						System.out.println("Line " + i + ": Test output");
						Thread.sleep(10);
					}
					console.postStatusMessage("Bulk output test complete.");
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}).start();
		});
	}
}