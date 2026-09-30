package ca.pfv.spmf.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import ca.pfv.spmf.gui.preferences.PreferencesManager;

/*
 * Copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * 
 * Do not remove copyright and license
 */

/**
 * Console panel of the SPMF GUI. Can capture {@code System.out} (black) and
 * {@code System.err} (red), shows status messages in grey, follows new output
 * only while the view is scrolled to the bottom, and offers copy / save /
 * clear / zoom via a context menu, Ctrl+wheel and Ctrl+plus / Ctrl+minus.
 *
 * @author Philippe Fournier-Viger
 */
public class ConsolePanel extends JPanel {

	private static final long serialVersionUID = 8344817215180749939L;

	private static final Color COLOR_STATUS = new Color(120, 120, 120);
	private static final Color COLOR_REGULAR = new Color(30, 30, 30);
	private static final Color COLOR_ERROR = new Color(192, 57, 43);

	private static final int MIN_FONT_SIZE = 6;
	private static final int MAX_FONT_SIZE = 72;
	private static final int DEFAULT_FONT_SIZE = 12;

	private static final int MAX_DOCUMENT_LENGTH = 500_000;
	private static final int TRIM_TARGET_LENGTH = MAX_DOCUMENT_LENGTH / 2;

	/** Pixels of slack when deciding whether the view is "at the bottom". */
	private static final int AUTOSCROLL_TOLERANCE = 24;

	private static final String NEWLINE = System.lineSeparator();

	/** The true standard streams, captured once before any panel redirects them. */
	private static final PrintStream ORIGINAL_OUT = System.out;
	private static final PrintStream ORIGINAL_ERR = System.err;

	private final SimpleAttributeSet regularAttributes = new SimpleAttributeSet();
	private final SimpleAttributeSet statusAttributes = new SimpleAttributeSet();
	private final SimpleAttributeSet errorAttributes = new SimpleAttributeSet();

	private final JTextPane textPane;
	private final StyledDocument doc;
	private final JScrollPane scrollPane;

	private PrintStream redirectedOut;
	private PrintStream redirectedErr;

	/**
	 * @param showClearButton if true, a "Clear console" button is shown at the bottom
	 */
	public ConsolePanel(boolean showClearButton) {
		StyleConstants.setForeground(regularAttributes, COLOR_REGULAR);
		StyleConstants.setForeground(statusAttributes, COLOR_STATUS);
		StyleConstants.setForeground(errorAttributes, COLOR_ERROR);

		textPane = new JTextPane();
		textPane.setEditable(false);
		textPane.setBackground(Color.WHITE);
		textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, loadFontSize()));
		// We manage scrolling ourselves so the user can read older output while
		// new output keeps arriving.
		((DefaultCaret) textPane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
		doc = textPane.getStyledDocument();

		scrollPane = new JScrollPane(textPane);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

		setLayout(new BorderLayout());
		add(scrollPane, BorderLayout.CENTER);
		if (showClearButton) {
			JButton clear = new JButton("Clear console");
			clear.setFocusPainted(false);
			clear.addActionListener(e -> clearConsole());
			add(clear, BorderLayout.SOUTH);
		}

		setupContextMenu();
		setupZoom();
	}

	// -----------------------------------------------------------------------
	// Public API
	// -----------------------------------------------------------------------

	/** Redirects {@code System.out} and {@code System.err} to this console. */
	public void redirectOutputStream() {
		if (redirectedOut == null) {
			redirectedOut = new PrintStream(new TextPaneOutputStream(regularAttributes), true, StandardCharsets.UTF_8);
			redirectedErr = new PrintStream(new TextPaneOutputStream(errorAttributes), true, StandardCharsets.UTF_8);
		}
		System.setOut(redirectedOut);
		System.setErr(redirectedErr);
	}

	/**
	 * Restores the original standard streams, but only if they are currently
	 * redirected to this console (so another console's redirection is kept).
	 */
	public void restoreOutputStream() {
		if (redirectedOut != null && System.out == redirectedOut) {
			redirectedOut.flush();
			System.setOut(ORIGINAL_OUT);
		}
		if (redirectedErr != null && System.err == redirectedErr) {
			redirectedErr.flush();
			System.setErr(ORIGINAL_ERR);
		}
	}

	/** Posts a grey status message. */
	public void postStatusMessage(String message) {
		if (message != null) {
			appendToDocument(message + NEWLINE, statusAttributes);
		}
	}

	/** Appends a line of regular output. */
	public void appendLine(String line) {
		if (line != null) {
			appendToDocument(line + NEWLINE, regularAttributes);
		}
	}

	/** Clears the console. */
	public void clearConsole() {
		onEdt(() -> textPane.setText(""));
	}

	/** Copies the selection (or everything, if nothing is selected) to the clipboard. */
	public void copyText() {
		String text = textPane.getSelectedText();
		if (text == null || text.isEmpty()) {
			text = textPane.getText();
		}
		if (text != null && !text.isEmpty()) {
			try {
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
			} catch (IllegalStateException e) {
				ORIGINAL_OUT.println("Clipboard unavailable: " + e.getMessage());
			}
		}
	}

	/** Selects all text. */
	public void selectAll() {
		textPane.requestFocusInWindow();
		textPane.selectAll();
	}

	/** @return the whole console text */
	public String getText() {
		return textPane.getText();
	}

	// -----------------------------------------------------------------------
	// UI setup
	// -----------------------------------------------------------------------

	private static int loadFontSize() {
		Integer size = PreferencesManager.getInstance().getConsoleFontSize();
		return (size == null || size < MIN_FONT_SIZE || size > MAX_FONT_SIZE) ? DEFAULT_FONT_SIZE : size;
	}

	private void setupContextMenu() {
		JPopupMenu menu = new JPopupMenu();
		menu.add(item("Copy", e -> copyText()));
		menu.add(item("Select all", e -> selectAll()));
		menu.addSeparator();
		menu.add(item("Scroll to end", e -> scrollToEnd()));
		menu.add(item("Clear", e -> clearConsole()));
		menu.add(item("Save log to file\u2026", e -> saveLog()));
		menu.addSeparator();

		JMenu fontMenu = new JMenu("Font size");
		fontMenu.add(item("Increase  (Ctrl +)", e -> changeFontSize(+2)));
		fontMenu.add(item("Decrease  (Ctrl -)", e -> changeFontSize(-2)));
		fontMenu.addSeparator();
		fontMenu.add(item("Reset to default", e -> setFontSize(DEFAULT_FONT_SIZE)));
		menu.add(fontMenu);

		textPane.setComponentPopupMenu(menu);
	}

	private static JMenuItem item(String text, java.awt.event.ActionListener l) {
		JMenuItem i = new JMenuItem(text);
		i.addActionListener(l);
		return i;
	}

	private void setupZoom() {
		// Ctrl + wheel: zoom; otherwise let the scroll pane scroll
		textPane.addMouseWheelListener(e -> {
			if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
				changeFontSize(e.getWheelRotation() < 0 ? +1 : -1);
			} else {
				scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(textPane, e, scrollPane));
			}
		});
		int ctrl = InputEvent.CTRL_DOWN_MASK;
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, ctrl), "zoomIn", () -> changeFontSize(+2));
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, ctrl), "zoomIn", () -> changeFontSize(+2));
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, ctrl), "zoomIn", () -> changeFontSize(+2));
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, ctrl), "zoomOut", () -> changeFontSize(-2));
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, ctrl), "zoomOut", () -> changeFontSize(-2));
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_0, ctrl), "zoomReset", () -> setFontSize(DEFAULT_FONT_SIZE));
	}

	private void bind(KeyStroke key, String name, Runnable action) {
		textPane.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
		textPane.getActionMap().put(name, new javax.swing.AbstractAction() {
			private static final long serialVersionUID = 1L;
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				action.run();
			}
		});
	}

	// -----------------------------------------------------------------------
	// Font size
	// -----------------------------------------------------------------------

	private void changeFontSize(int delta) {
		setFontSize(textPane.getFont().getSize() + delta);
	}

	private void setFontSize(int size) {
		int clamped = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, size));
		if (clamped != textPane.getFont().getSize()) {
			textPane.setFont(textPane.getFont().deriveFont((float) clamped));
			PreferencesManager.getInstance().setConsoleFontSize(clamped);
		}
	}

	// -----------------------------------------------------------------------
	// Save
	// -----------------------------------------------------------------------

	private void saveLog() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Save console log");
		chooser.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
		chooser.setSelectedFile(new File("console_log.txt"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = chooser.getSelectedFile();
		if (!file.getName().contains(".")) {
			file = new File(file.getParentFile(), file.getName() + ".txt");
		}
		if (file.exists() && JOptionPane.showConfirmDialog(this,
				"\"" + file.getName() + "\" already exists. Overwrite it?", "Confirm overwrite",
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
			return;
		}
		try {
			Files.writeString(file.toPath(), textPane.getText(), StandardCharsets.UTF_8);
			postStatusMessage("Log saved to " + file.getAbsolutePath());
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this, "Could not save the log:\n" + ex.getMessage(), "Save error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	// -----------------------------------------------------------------------
	// Document management
	// -----------------------------------------------------------------------

	private void appendToDocument(String text, SimpleAttributeSet attributes) {
		if (text == null || text.isEmpty()) {
			return;
		}
		onEdt(() -> {
			boolean followTail = isAtBottom();
			try {
				trimIfNeeded();
				doc.insertString(doc.getLength(), text, attributes);
			} catch (BadLocationException e) {
				ORIGINAL_OUT.println("Console append error: " + e.getMessage());
			}
			if (followTail) {
				scrollToEnd();
			}
		});
	}

	/** Removes the oldest lines once the document grows past the limit. */
	private void trimIfNeeded() throws BadLocationException {
		int length = doc.getLength();
		if (length <= MAX_DOCUMENT_LENGTH) {
			return;
		}
		int excess = length - TRIM_TARGET_LENGTH;
		// Cut at the next line break so no partial line remains at the top
		String around = doc.getText(excess, Math.min(4000, length - excess));
		int nl = around.indexOf('\n');
		if (nl >= 0) {
			excess += nl + 1;
		}
		doc.remove(0, excess);
	}

	private boolean isAtBottom() {
		JScrollBar bar = scrollPane.getVerticalScrollBar();
		return bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - AUTOSCROLL_TOLERANCE;
	}

	private void scrollToEnd() {
		// After the insert, the scroll range is updated on a later layout pass
		SwingUtilities.invokeLater(() -> {
			JScrollBar bar = scrollPane.getVerticalScrollBar();
			bar.setValue(bar.getMaximum());
		});
	}

	private static void onEdt(Runnable r) {
		if (SwingUtilities.isEventDispatchThread()) {
			r.run();
		} else {
			SwingUtilities.invokeLater(r);
		}
	}

	// -----------------------------------------------------------------------
	// OutputStream → document
	// -----------------------------------------------------------------------

	/**
	 * Buffers bytes until a line break or flush, then decodes them as UTF-8 and
	 * appends the text with the given style.
	 */
	private final class TextPaneOutputStream extends OutputStream {

		private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(256);
		private final SimpleAttributeSet attributes;

		TextPaneOutputStream(SimpleAttributeSet attributes) {
			this.attributes = attributes;
		}

		@Override
		public synchronized void write(int b) {
			buffer.write(b);
			if (b == '\n') {
				flushBuffer();
			}
		}

		@Override
		public synchronized void write(byte[] data, int off, int len) {
			buffer.write(data, off, len);
			for (int i = off + len - 1; i >= off; i--) {
				if (data[i] == '\n') {
					flushBuffer();
					return;
				}
			}
		}

		@Override
		public synchronized void flush() {
			flushBuffer();
		}

		private void flushBuffer() {
			if (buffer.size() == 0) {
				return;
			}
			String text = buffer.toString(StandardCharsets.UTF_8);
			buffer.reset();
			appendToDocument(text, attributes);
		}
	}

	// -----------------------------------------------------------------------
	// Demo
	// -----------------------------------------------------------------------

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("ConsolePanel test");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			ConsolePanel console = new ConsolePanel(true);
			console.redirectOutputStream();
			frame.add(console);
			frame.setSize(700, 500);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);

			console.postStatusMessage("Console initialised.");
			System.out.println("Regular output \u2014 accents: \u00E9\u00E8\u00E0 \u4E2D\u6587");
			System.err.println("This is an error line");
			Thread t = new Thread(() -> {
				for (int i = 0; i < 200; i++) {
					System.out.println("Line " + i);
					try {
						Thread.sleep(25);
					} catch (InterruptedException e) {
						return;
					}
				}
				console.postStatusMessage("Done. Try scrolling up while this runs.");
			});
			t.setDaemon(true);
			t.start();
		});
	}
}