package ca.pfv.spmf.gui.texteditor;

/*
Copyright (c) 2008-2022 Philippe Fournier-Viger
This file is part of the SPMF DATA MINING SOFTWARE
(http://www.philippe-fournier-viger.com/spmf).
SPMF is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.
SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
A PARTICULAR PURPOSE. See the GNU General Public License for more details.
You should have received a copy of the GNU General Public License along with
SPMF. If not, see http://www.gnu.org/licenses/.
*/

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import ca.pfv.spmf.gui.preferences.PreferencesManager;

/**
 * This is a simple text editor, adapted for SPMF.
 * 
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class SPMFTextEditor {

	// ==================== CONSTANTS ====================
	private static final String WINDOW_TITLE_PREFIX = "SPMF Text Editor - ";
	private static final String DEFAULT_FILE_NAME = "Untitled";
	private static final double BYTES_TO_MB = 1024.0 * 1024.0;
	private static final double LARGE_FILE_WARNING_MB = 10;
	private static final int MIN_FONT_SIZE = 6;
	private static final int MAX_FONT_SIZE = 72;
	private static final int DEFAULT_FONT_SIZE = 12;
	private static final int TAB_SIZE = 4;
	private static final int MAX_RECENT_FILES = 8;
	/** Platform menu shortcut modifier (CTRL on Windows/Linux, CMD on Mac) */
	private static final int SHORTCUT_MASK = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
	/** Preferences node for recent files and last directory */
	private static final Preferences PREFS = Preferences.userNodeForPackage(SPMFTextEditor.class);
	private static final String PREF_RECENT_FILE = "textEditorRecentFile";
	private static final String PREF_LAST_DIRECTORY = "textEditorLastDirectory";

	// ==================== UI COMPONENTS ====================
	private JTextArea textAreaX;
	private JFrame frame;
	private JScrollPane scrollPane;
	private LineNumberPane lineNumberPane;
	private LinePainter linePainter;
	private JTextField statusBar;
	private FindReplaceBar findReplaceBar;
	private JCheckBoxMenuItem menuSearchBar;
	private JMenu menuRecentFiles;
	private UndoTool undoTool;

	// ==================== STATE ====================
	private final boolean runAsStandalone;
	private boolean nightMode = false;
	private String currentFilePath = null;
	private String currentFileName = DEFAULT_FILE_NAME;
	/** Current file size in MB */
	private double currentFileSize = 0;
	/** Encoding of the current file */
	private Charset currentCharset = StandardCharsets.UTF_8;
	/** Line separator of the current file */
	private String currentLineSeparator = System.lineSeparator();
	/** Whether the current file has a UTF-8 BOM */
	private boolean currentHasBom = false;
	/** Last-modified timestamp of the file when we last read or wrote it */
	private long lastModifiedOnDisk = 0;
	private boolean isModified = false;
	/** True while a file is being loaded in the background */
	private boolean loading = false;
	/** Re-entrancy guard for the "file changed on disk" dialog */
	private boolean checkingExternalChange = false;

	/**
	 * Entry point when used as a standalone program.
	 * 
	 * @param args optional path of a file to open
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			SPMFTextEditor editor = new SPMFTextEditor(true);
			if (args.length > 0) {
				editor.openAFile(new File(args[0]));
			}
		});
	}

	/**
	 * Constructor
	 * 
	 * @param runAsStandalone true if run as a standalone program, otherwise false
	 */
	public SPMFTextEditor(boolean runAsStandalone) {
		this.runAsStandalone = runAsStandalone;
		loadPreferencesAndInitializeUI();
	}

	// =====================================================================
	// Initialization
	// =====================================================================

	private void loadPreferencesAndInitializeUI() {
		PreferencesManager prefs = PreferencesManager.getInstance();

		nightMode = prefs.getNightMode();
		int fontsize = prefs.getTextEditorFontSize();
		boolean lineWrap = prefs.getTextEditorLineWrap();
		boolean wordWrap = prefs.getTextEditorWordWrap();
		String fontFamily = prefs.getFontFamilly();

		int windowWidth = prefs.getTextEditorWidth();
		int windowHeight = prefs.getTextEditorHeight();
		int textAreaWidth = prefs.getTextEditorAreaWidth();
		int textAreaHeight = prefs.getTextEditorAreaHeight();
		int x = prefs.getTextEditorX();
		int y = prefs.getTextEditorY();

		// Keep the window inside the (multi-monitor) screen area
		java.awt.Rectangle screen = getEffectiveScreenArea();
		x = clamp(x, screen.x, screen.x + screen.width - windowWidth);
		y = clamp(y, screen.y, screen.y + screen.height - windowHeight);

		createMainFrame(windowWidth, windowHeight, x, y);
		createTextArea(fontsize, fontFamily, lineWrap, wordWrap);
		undoTool = new UndoTool(textAreaX);
		frame.setJMenuBar(createMenuBar());
		createScrollPane(textAreaWidth, textAreaHeight);
		createStatusBar();
		createFindReplaceBar();
		linePainter = new LinePainter(textAreaX);
		installDragAndDrop();

		setNightMode(nightMode);
		rebuildRecentFilesMenu();
		updateStatusBar();

		frame.pack();
		frame.setVisible(true);
		textAreaX.requestFocusInWindow();
	}

	private void createMainFrame(int width, int height, int xPosition, int yPosition) {
		frame = new JFrame(WINDOW_TITLE_PREFIX + currentFileName);
		URL iconUrl = SPMFTextEditor.class.getResource("/ca/pfv/spmf/gui/icons/History24.gif");
		if (iconUrl != null) {
			frame.setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
		}
		frame.setSize(width, height);
		frame.setLocation(xPosition, yPosition);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.getContentPane().setLayout(new BorderLayout(1, 0));

		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if (runAsStandalone) {
					handleQuit();
				} else {
					handleClose();
				}
			}
		});

		// Detect files modified by another program when the window regains focus
		frame.addWindowFocusListener(new WindowAdapter() {
			@Override
			public void windowGainedFocus(WindowEvent e) {
				SwingUtilities.invokeLater(SPMFTextEditor.this::checkForExternalModification);
			}
		});

		frame.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				saveWindowDimensions();
			}

			@Override
			public void componentMoved(ComponentEvent e) {
				saveWindowPosition();
			}
		});
	}

	private void createTextArea(int fontSize, String fontFamily, boolean lineWrap, boolean wordWrap) {
		textAreaX = new JTextArea();
		textAreaX.setLineWrap(lineWrap);
		textAreaX.setWrapStyleWord(wordWrap);
		textAreaX.setTabSize(TAB_SIZE);
		textAreaX.setFont(new java.awt.Font(fontFamily, java.awt.Font.PLAIN, fontSize));

		textAreaX.addCaretListener(e -> updateStatusBar());

		textAreaX.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) {
				markAsModified();
			}

			public void removeUpdate(DocumentEvent e) {
				markAsModified();
			}

			public void changedUpdate(DocumentEvent e) {
				markAsModified();
			}
		});

		// ESC hides the find bar
		bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "hideSearch", () -> {
			if (findReplaceBar != null && findReplaceBar.isVisible()) {
				findReplaceBar.hideBar();
			}
		});

		// Extra zoom keys (the menu accelerators cover Ctrl+=, Ctrl+-, Ctrl+0)
		bindKey(shortcut(KeyEvent.VK_PLUS), "zoomIn1", () -> zoom(1));
		bindKey(shortcut(KeyEvent.VK_ADD), "zoomIn2", () -> zoom(1));
		bindKey(shortcut(KeyEvent.VK_SUBTRACT), "zoomOut2", () -> zoom(-1));
		bindKey(shortcut(KeyEvent.VK_NUMPAD0), "zoomReset2", () -> setFontSize(DEFAULT_FONT_SIZE));

		// Ctrl + mouse wheel zooms; other wheel events go to the scroll pane
		textAreaX.addMouseWheelListener(e -> {
			if ((e.getModifiersEx() & SHORTCUT_MASK) != 0) {
				zoom(e.getWheelRotation() < 0 ? 1 : -1);
			} else if (scrollPane != null) {
				scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(textAreaX, e, scrollPane));
			}
		});
	}

	private void createScrollPane(int width, int height) {
		scrollPane = new JScrollPane(textAreaX);
		scrollPane.setPreferredSize(new Dimension(width, height));
		lineNumberPane = new LineNumberPane(textAreaX);
		scrollPane.setRowHeaderView(lineNumberPane);
		frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
	}

	private void createStatusBar() {
		statusBar = new JTextField();
		statusBar.setEditable(false);
		statusBar.setColumns(10);
		frame.getContentPane().add(statusBar, BorderLayout.SOUTH);
	}

	private void createFindReplaceBar() {
		findReplaceBar = new FindReplaceBar(textAreaX, undoTool);
		findReplaceBar.setOnClose(() -> {
			if (menuSearchBar != null) {
				menuSearchBar.setSelected(false);
			}
		});
		frame.getContentPane().add(findReplaceBar, BorderLayout.NORTH);
	}

	/** Allow dropping a file (opens it) or text (inserted at the drop point) */
	private void installDragAndDrop() {
		new DropTarget(textAreaX, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetAdapter() {
			@Override
			public void drop(DropTargetDropEvent event) {
				try {
					Transferable transferable = event.getTransferable();
					if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
						event.acceptDrop(DnDConstants.ACTION_COPY);
						List<?> files = (List<?>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
						event.dropComplete(true);
						if (!files.isEmpty() && files.get(0) instanceof File) {
							File file = (File) files.get(0);
							SwingUtilities.invokeLater(() -> {
								if (confirmDiscardChanges("opening another file")) {
									loadFile(file, false);
								}
							});
						}
					} else if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
						event.acceptDrop(DnDConstants.ACTION_COPY);
						String text = (String) transferable.getTransferData(DataFlavor.stringFlavor);
						int position = textAreaX.viewToModel2D(event.getLocation());
						textAreaX.getDocument().insertString(position, text, null);
						event.dropComplete(true);
					} else {
						event.rejectDrop();
					}
				} catch (Exception e) {
					event.dropComplete(false);
				}
			}
		});
	}

	// =====================================================================
	// Menus
	// =====================================================================

	private JMenuBar createMenuBar() {
		JMenuBar menuBar = new JMenuBar();
		menuBar.add(createFileMenu());
		menuBar.add(createEditMenu());
		menuBar.add(createViewMenu());
		return menuBar;
	}

	private JMenu createFileMenu() {
		JMenu menu = new JMenu("File");
		menu.setMnemonic(KeyEvent.VK_F);

		menu.add(menuItem("New", "/ca/pfv/spmf/gui/icons/New24.gif", KeyEvent.VK_N, shortcut(KeyEvent.VK_N),
				this::newFile));
		menu.add(menuItem("Open...", "/ca/pfv/spmf/gui/icons/open.gif", KeyEvent.VK_O, shortcut(KeyEvent.VK_O),
				this::openFile));

		menuRecentFiles = new JMenu("Open recent");
		menuRecentFiles.setMnemonic(KeyEvent.VK_R);
		menu.add(menuRecentFiles);

		menu.add(menuItem("Reload from disk", null, KeyEvent.VK_L, KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0),
				this::reloadFile));
		menu.addSeparator();
		menu.add(menuItem("Save", "/ca/pfv/spmf/gui/icons/save.gif", KeyEvent.VK_S, shortcut(KeyEvent.VK_S),
				this::save));
		menu.add(menuItem("Save as...", "/ca/pfv/spmf/gui/icons/SaveAs24.gif", KeyEvent.VK_A,
				KeyStroke.getKeyStroke(KeyEvent.VK_S, SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK), this::saveAs));
		menu.addSeparator();
		menu.add(menuItem("Print...", "/ca/pfv/spmf/gui/icons/print.gif", KeyEvent.VK_P, shortcut(KeyEvent.VK_P),
				this::print));
		menu.addSeparator();
		if (runAsStandalone) {
			menu.add(menuItem("Quit", null, KeyEvent.VK_Q, shortcut(KeyEvent.VK_Q), this::handleQuit));
		} else {
			menu.add(menuItem("Close", null, KeyEvent.VK_C, shortcut(KeyEvent.VK_W), this::handleClose));
		}
		return menu;
	}

	private JMenu createEditMenu() {
		JMenu menu = new JMenu("Edit");
		menu.setMnemonic(KeyEvent.VK_E);

		menu.add(menuItem("Undo", "/ca/pfv/spmf/gui/icons/Undo24.gif", KeyEvent.VK_U, shortcut(KeyEvent.VK_Z),
				undoTool::undo));
		menu.add(menuItem("Redo", "/ca/pfv/spmf/gui/icons/Redo24.gif", KeyEvent.VK_R, shortcut(KeyEvent.VK_Y),
				undoTool::redo));
		menu.addSeparator();
		menu.add(menuItem("Cut", "/ca/pfv/spmf/gui/icons/Cut24.gif", KeyEvent.VK_T, shortcut(KeyEvent.VK_X),
				textAreaX::cut));
		menu.add(menuItem("Copy", "/ca/pfv/spmf/gui/icons/Copy24.gif", KeyEvent.VK_C, shortcut(KeyEvent.VK_C),
				textAreaX::copy));
		menu.add(menuItem("Paste", "/ca/pfv/spmf/gui/icons/Paste24.gif", KeyEvent.VK_P, shortcut(KeyEvent.VK_V),
				textAreaX::paste));
		menu.addSeparator();
		menu.add(menuItem("Delete line", null, KeyEvent.VK_D, shortcut(KeyEvent.VK_D), this::deleteLines));
		menu.add(menuItem("Duplicate line", null, KeyEvent.VK_L,
				KeyStroke.getKeyStroke(KeyEvent.VK_D, SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK),
				this::duplicateLines));
		menu.add(menuItem("Select all", null, KeyEvent.VK_A, shortcut(KeyEvent.VK_A), textAreaX::selectAll));
		menu.addSeparator();
		menu.add(menuItem("Find / Replace...", null, KeyEvent.VK_F, shortcut(KeyEvent.VK_F), this::showFindBar));
		menu.add(menuItem("Find next", null, KeyEvent.VK_N, KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0),
				() -> findNext(true)));
		menu.add(menuItem("Find previous", null, KeyEvent.VK_V,
				KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK), () -> findNext(false)));
		menu.addSeparator();
		menu.add(menuItem("Go to line...", null, KeyEvent.VK_G, shortcut(KeyEvent.VK_G), this::showGoToLineDialog));
		return menu;
	}

	private JMenu createViewMenu() {
		JMenu menu = new JMenu("View");
		menu.setMnemonic(KeyEvent.VK_V);
		PreferencesManager prefs = PreferencesManager.getInstance();

		JCheckBoxMenuItem menuLineWrap = new JCheckBoxMenuItem("Line wrap");
		menuLineWrap.setSelected(prefs.getTextEditorLineWrap());
		menuLineWrap.addActionListener(e -> setLineWrap(menuLineWrap.isSelected()));
		menu.add(menuLineWrap);

		JCheckBoxMenuItem menuWordWrap = new JCheckBoxMenuItem("Word wrap");
		menuWordWrap.setSelected(prefs.getTextEditorWordWrap());
		menuWordWrap.addActionListener(e -> {
			setWordWrap(menuWordWrap.isSelected());
			if (menuWordWrap.isSelected()) {
				menuLineWrap.setSelected(true);
				setLineWrap(true);
			}
		});
		menu.add(menuWordWrap);
		menu.addSeparator();

		JCheckBoxMenuItem menuNightMode = new JCheckBoxMenuItem("Night mode");
		menuNightMode.setSelected(nightMode);
		menuNightMode.addActionListener(e -> setNightMode(menuNightMode.isSelected()));
		menu.add(menuNightMode);
		menu.addSeparator();

		menuSearchBar = new JCheckBoxMenuItem("Find / Replace bar");
		menuSearchBar.setSelected(false);
		menuSearchBar.addActionListener(e -> {
			if (menuSearchBar.isSelected()) {
				findReplaceBar.showBar();
			} else {
				findReplaceBar.hideBar();
			}
		});
		menu.add(menuSearchBar);

		JCheckBoxMenuItem menuStatusBar = new JCheckBoxMenuItem("Status bar");
		menuStatusBar.setSelected(true);
		menuStatusBar.addActionListener(e -> setStatusBar(menuStatusBar.isSelected()));
		menu.add(menuStatusBar);

		JCheckBoxMenuItem menuLineNumbers = new JCheckBoxMenuItem("Line numbers");
		menuLineNumbers.setSelected(true);
		menuLineNumbers.addActionListener(e -> setLineNumbers(menuLineNumbers.isSelected()));
		menu.add(menuLineNumbers);
		menu.addSeparator();

		menu.add(menuItem("Font family...", null, KeyEvent.VK_M, null, this::chooseFontFamily));
		menu.add(menuItem("Font size...", null, KeyEvent.VK_Z, null, this::chooseFontSize));
		menu.add(menuItem("Zoom in", null, KeyEvent.VK_I, shortcut(KeyEvent.VK_EQUALS), () -> zoom(1)));
		menu.add(menuItem("Zoom out", null, KeyEvent.VK_O, shortcut(KeyEvent.VK_MINUS), () -> zoom(-1)));
		menu.add(menuItem("Reset zoom", null, KeyEvent.VK_R, shortcut(KeyEvent.VK_0),
				() -> setFontSize(DEFAULT_FONT_SIZE)));
		return menu;
	}

	/** Create a menu item */
	private JMenuItem menuItem(String text, String iconPath, int mnemonic, KeyStroke accelerator, Runnable action) {
		JMenuItem item = new JMenuItem(text);
		if (iconPath != null) {
			URL url = SPMFTextEditor.class.getResource(iconPath);
			if (url != null) {
				item.setIcon(new ImageIcon(url));
			}
		}
		if (mnemonic != 0) {
			item.setMnemonic(mnemonic);
		}
		if (accelerator != null) {
			item.setAccelerator(accelerator);
		}
		item.addActionListener(e -> action.run());
		return item;
	}

	/** Keystroke with the platform menu shortcut modifier */
	private static KeyStroke shortcut(int keyCode) {
		return KeyStroke.getKeyStroke(keyCode, SHORTCUT_MASK);
	}

	/** Bind a key to an action in the text area */
	private void bindKey(KeyStroke key, String name, Runnable action) {
		textAreaX.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
		textAreaX.getActionMap().put(name, new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				action.run();
			}
		});
	}

	// =====================================================================
	// Recent files
	// =====================================================================

	private List<String> getRecentFiles() {
		List<String> result = new ArrayList<>();
		for (int i = 0; i < MAX_RECENT_FILES; i++) {
			String path = PREFS.get(PREF_RECENT_FILE + i, null);
			if (path != null && !path.isEmpty() && !result.contains(path)) {
				result.add(path);
			}
		}
		return result;
	}

	private void saveRecentFiles(List<String> recent) {
		for (int i = 0; i < MAX_RECENT_FILES; i++) {
			if (i < recent.size()) {
				PREFS.put(PREF_RECENT_FILE + i, recent.get(i));
			} else {
				PREFS.remove(PREF_RECENT_FILE + i);
			}
		}
	}

	private void addRecentFile(String path) {
		List<String> recent = getRecentFiles();
		recent.remove(path);
		recent.add(0, path);
		while (recent.size() > MAX_RECENT_FILES) {
			recent.remove(recent.size() - 1);
		}
		saveRecentFiles(recent);
		rebuildRecentFilesMenu();
	}

	private void removeRecentFile(String path) {
		List<String> recent = getRecentFiles();
		if (recent.remove(path)) {
			saveRecentFiles(recent);
			rebuildRecentFilesMenu();
		}
	}

	private void rebuildRecentFilesMenu() {
		if (menuRecentFiles == null) {
			return;
		}
		menuRecentFiles.removeAll();
		List<String> recent = getRecentFiles();
		if (recent.isEmpty()) {
			JMenuItem none = new JMenuItem("(no recent files)");
			none.setEnabled(false);
			menuRecentFiles.add(none);
			return;
		}
		int index = 1;
		for (String path : recent) {
			File file = new File(path);
			JMenuItem item = new JMenuItem(index + "  " + file.getName());
			if (index <= 9) {
				item.setMnemonic(KeyEvent.VK_0 + index);
			}
			item.setToolTipText(path);
			item.addActionListener(e -> openRecentFile(file));
			menuRecentFiles.add(item);
			index++;
		}
		menuRecentFiles.addSeparator();
		JMenuItem clear = new JMenuItem("Clear list");
		clear.addActionListener(e -> {
			saveRecentFiles(new ArrayList<>());
			rebuildRecentFilesMenu();
		});
		menuRecentFiles.add(clear);
	}

	private void openRecentFile(File file) {
		if (!file.isFile()) {
			JOptionPane.showMessageDialog(frame, "This file no longer exists:\n" + file.getAbsolutePath(),
					"File Not Found", JOptionPane.WARNING_MESSAGE);
			removeRecentFile(file.getAbsolutePath());
			return;
		}
		if (confirmDiscardChanges("opening another file")) {
			loadFile(file, false);
		}
	}

	// =====================================================================
	// View settings
	// =====================================================================

	protected void setLineWrap(boolean selected) {
		textAreaX.setLineWrap(selected);
		PreferencesManager.getInstance().setTextEditorLineWrap(selected);
	}

	protected void setWordWrap(boolean selected) {
		textAreaX.setWrapStyleWord(selected);
		PreferencesManager.getInstance().setTextEditorWordWrap(selected);
	}

	protected void setStatusBar(boolean selected) {
		statusBar.setVisible(selected);
		frame.getContentPane().revalidate();
		frame.getContentPane().repaint();
	}

	protected void setLineNumbers(boolean selected) {
		scrollPane.setRowHeaderView(selected ? lineNumberPane : null);
		scrollPane.revalidate();
		scrollPane.repaint();
	}

	protected void setNightMode(boolean selected) {
		this.nightMode = selected;
		if (selected) {
			textAreaX.setBackground(Color.BLACK);
			textAreaX.setForeground(Color.WHITE);
			textAreaX.setCaretColor(Color.WHITE);
			textAreaX.setSelectionColor(new Color(60, 90, 140));
			textAreaX.setSelectedTextColor(Color.WHITE);
			statusBar.setBackground(Color.DARK_GRAY);
			statusBar.setForeground(Color.WHITE);
			if (linePainter != null) {
				linePainter.setColor(new Color(40, 40, 40));
			}
		} else {
			textAreaX.setBackground(Color.WHITE);
			textAreaX.setForeground(Color.BLACK);
			textAreaX.setCaretColor(Color.BLACK);
			textAreaX.setSelectionColor(new Color(184, 207, 229));
			textAreaX.setSelectedTextColor(Color.BLACK);
			statusBar.setBackground(Color.WHITE);
			statusBar.setForeground(Color.BLACK);
			if (linePainter != null) {
				linePainter.setLighter(Color.LIGHT_GRAY);
			}
		}
		if (lineNumberPane != null) {
			lineNumberPane.setNightMode(selected);
		}
		if (findReplaceBar != null) {
			findReplaceBar.setNightMode(selected);
		}
		textAreaX.repaint();
		PreferencesManager.getInstance().setNightMode(selected);
	}

	protected void chooseFontFamily() {
		java.awt.Font currentFont = textAreaX.getFont();
		String currentFamily = currentFont.getFamily();
		String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();

		int selectedIndex = 0;
		for (int i = 0; i < fontNames.length; i++) {
			if (fontNames[i].equals(currentFamily)) {
				selectedIndex = i;
				break;
			}
		}

		JLabel preview = new JLabel("The quick brown fox jumps over the lazy dog 0123456789");
		preview.setFont(currentFont);

		JList<String> list = new JList<>(fontNames);
		list.setSelectedIndex(selectedIndex);
		list.ensureIndexIsVisible(selectedIndex);
		list.addListSelectionListener(e -> {
			String family = list.getSelectedValue();
			if (!e.getValueIsAdjusting() && family != null) {
				preview.setFont(new java.awt.Font(family, currentFont.getStyle(), currentFont.getSize()));
			}
		});

		JScrollPane listScroll = new JScrollPane(list);
		listScroll.setPreferredSize(new Dimension(320, 380));
		JPanel panel = new JPanel(new BorderLayout(5, 5));
		panel.add(listScroll, BorderLayout.CENTER);
		panel.add(preview, BorderLayout.SOUTH);

		int result = JOptionPane.showConfirmDialog(frame, panel, "Choose font family", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		String family = list.getSelectedValue();
		if (result == JOptionPane.OK_OPTION && family != null && !family.equals(currentFamily)) {
			textAreaX.setFont(new java.awt.Font(family, currentFont.getStyle(), currentFont.getSize()));
			PreferencesManager.getInstance().setFontFamilly(family);
			refreshAfterFontChange();
		}
	}

	protected void chooseFontSize() {
		String result = JOptionPane.showInputDialog(frame,
				"Enter font size (" + MIN_FONT_SIZE + "-" + MAX_FONT_SIZE + "):", textAreaX.getFont().getSize());
		if (result == null) {
			return;
		}
		try {
			int size = Integer.parseInt(result.trim());
			if (size < MIN_FONT_SIZE || size > MAX_FONT_SIZE) {
				JOptionPane.showMessageDialog(frame,
						"Font size must be between " + MIN_FONT_SIZE + " and " + MAX_FONT_SIZE + ".",
						"Invalid Font Size", JOptionPane.ERROR_MESSAGE);
				return;
			}
			setFontSize(size);
		} catch (NumberFormatException e) {
			JOptionPane.showMessageDialog(frame, "Font size must be a valid number.", "Invalid Input",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	protected void setFontSize(int fontSize) {
		fontSize = clamp(fontSize, MIN_FONT_SIZE, MAX_FONT_SIZE);
		if (textAreaX.getFont().getSize() == fontSize) {
			return;
		}
		textAreaX.setFont(textAreaX.getFont().deriveFont((float) fontSize));
		PreferencesManager.getInstance().setTextEditorFontSize(fontSize);
		refreshAfterFontChange();
	}

	private void zoom(int delta) {
		setFontSize(textAreaX.getFont().getSize() + delta);
	}

	private void refreshAfterFontChange() {
		if (lineNumberPane != null) {
			lineNumberPane.revalidate();
			lineNumberPane.repaint();
		}
		frame.revalidate();
		frame.repaint();
		scrollCaretIntoView();
	}

	// =====================================================================
	// Find / Go to line / line editing
	// =====================================================================

	private void showFindBar() {
		findReplaceBar.showBar();
		menuSearchBar.setSelected(true);
	}

	private void findNext(boolean forward) {
		if (!findReplaceBar.hasQuery() || !findReplaceBar.findNext(forward)) {
			showFindBar();
		}
	}

	private void showGoToLineDialog() {
		int totalLines = textAreaX.getLineCount();
		Object answer = JOptionPane.showInputDialog(frame, "Enter line number (1-" + totalLines + "):",
				"Go to Line", JOptionPane.PLAIN_MESSAGE, null, null, String.valueOf(getCurrentLine()));
		if (answer == null || answer.toString().trim().isEmpty()) {
			return;
		}
		try {
			int line = Integer.parseInt(answer.toString().trim());
			if (line < 1 || line > totalLines) {
				JOptionPane.showMessageDialog(frame, "Line number must be between 1 and " + totalLines + ".",
						"Invalid Line Number", JOptionPane.WARNING_MESSAGE);
				return;
			}
			textAreaX.setCaretPosition(textAreaX.getLineStartOffset(line - 1));
			scrollCaretIntoView();
			textAreaX.requestFocusInWindow();
		} catch (NumberFormatException e) {
			JOptionPane.showMessageDialog(frame, "Please enter a valid line number.", "Invalid Input",
					JOptionPane.ERROR_MESSAGE);
		} catch (BadLocationException e) {
			JOptionPane.showMessageDialog(frame, "Error navigating to line: " + e.getMessage(), "Navigation Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private int getCurrentLine() {
		try {
			return textAreaX.getLineOfOffset(textAreaX.getCaretPosition()) + 1;
		} catch (BadLocationException e) {
			return 1;
		}
	}

	private void scrollCaretIntoView() {
		try {
			Rectangle2D r = textAreaX.modelToView2D(textAreaX.getCaretPosition());
			if (r != null) {
				textAreaX.scrollRectToVisible(r.getBounds());
			}
		} catch (BadLocationException ignored) {
		}
	}

	/** Delete the line(s) covered by the caret / selection */
	private void deleteLines() {
		// Only act when the text area has the focus, so that Ctrl+D typed in the
		// find bar does not delete text
		if (!textAreaX.isEditable() || !textAreaX.isFocusOwner()) {
			return;
		}
		try {
			Document doc = textAreaX.getDocument();
			int firstLine = textAreaX.getLineOfOffset(textAreaX.getSelectionStart());
			int lastLine = textAreaX.getLineOfOffset(textAreaX.getSelectionEnd());
			int start = textAreaX.getLineStartOffset(firstLine);
			int end = Math.min(textAreaX.getLineEndOffset(lastLine), doc.getLength());
			// The last line has no trailing newline: remove the preceding one instead
			if (end == doc.getLength() && start > 0) {
				start--;
			}
			undoTool.beginCompoundEdit();
			try {
				doc.remove(start, end - start);
			} finally {
				undoTool.endCompoundEdit();
			}
		} catch (BadLocationException ignored) {
		}
	}

	/** Duplicate the line(s) covered by the caret / selection */
	private void duplicateLines() {
		if (!textAreaX.isEditable() || !textAreaX.isFocusOwner()) {
			return;
		}
		try {
			Document doc = textAreaX.getDocument();
			int caret = textAreaX.getCaretPosition();
			int firstLine = textAreaX.getLineOfOffset(textAreaX.getSelectionStart());
			int lastLine = textAreaX.getLineOfOffset(textAreaX.getSelectionEnd());
			int start = textAreaX.getLineStartOffset(firstLine);
			int end = Math.min(textAreaX.getLineEndOffset(lastLine), doc.getLength());
			String text = doc.getText(start, end - start);
			String insert = text.endsWith("\n") ? text : "\n" + text;
			undoTool.beginCompoundEdit();
			try {
				doc.insertString(end, insert, null);
			} finally {
				undoTool.endCompoundEdit();
			}
			textAreaX.setCaretPosition(Math.min(caret + insert.length(), doc.getLength()));
		} catch (BadLocationException ignored) {
		}
	}

	// =====================================================================
	// Status bar / title / modification tracking
	// =====================================================================

	protected void updateStatusBar() {
		if (statusBar == null || textAreaX == null) {
			return;
		}
		if (loading) {
			statusBar.setText("Loading " + currentFileName + "...");
			return;
		}
		try {
			int caret = textAreaX.getCaretPosition();
			int line = textAreaX.getLineOfOffset(caret);
			int column = caret - textAreaX.getLineStartOffset(line) + 1;
			int selection = Math.abs(textAreaX.getSelectionEnd() - textAreaX.getSelectionStart());

			StringBuilder s = new StringBuilder();
			s.append("Line: ").append(line + 1);
			s.append("  |  Column: ").append(column);
			s.append("  |  Lines: ").append(textAreaX.getLineCount());
			s.append("  |  Characters: ").append(textAreaX.getDocument().getLength());
			if (selection > 0) {
				s.append("  |  Selected: ").append(selection);
			}
			s.append("  |  ").append(currentCharset.name());
			if (currentHasBom) {
				s.append(" BOM");
			}
			s.append("  |  ").append(lineSeparatorName(currentLineSeparator));
			if (isModified) {
				s.append("  |  Modified");
			}
			statusBar.setText(s.toString());
		} catch (BadLocationException e) {
			statusBar.setText("Ready");
		}
	}

	private static String lineSeparatorName(String separator) {
		if (TextFileIO.CRLF.equals(separator)) {
			return "CRLF (Windows)";
		}
		if (TextFileIO.CR.equals(separator)) {
			return "CR (Mac)";
		}
		return "LF (Unix)";
	}

	private void markAsModified() {
		if (loading) {
			return;
		}
		if (!isModified) {
			isModified = true;
			updateWindowTitle();
			updateStatusBar();
		}
	}

	private void markAsSaved() {
		isModified = false;
		updateWindowTitle();
		updateStatusBar();
	}

	private void updateWindowTitle() {
		if (frame == null) {
			return;
		}
		StringBuilder title = new StringBuilder(WINDOW_TITLE_PREFIX);
		if (isModified) {
			title.append("* ");
		}
		title.append(currentFileName);
		if (currentFilePath != null) {
			title.append("  (").append(String.format("%.4f", currentFileSize)).append(" MB)");
		}
		frame.setTitle(title.toString());
	}

	// =====================================================================
	// File operations
	// =====================================================================

	/**
	 * Ask the user what to do with unsaved changes (if any).
	 * 
	 * @return true if the caller may proceed, false if it must abort
	 */
	private boolean confirmDiscardChanges(String action) {
		if (loading) {
			JOptionPane.showMessageDialog(frame, "Please wait until the current file is loaded.", "Loading",
					JOptionPane.INFORMATION_MESSAGE);
			return false;
		}
		if (!isModified) {
			return true;
		}
		int result = JOptionPane.showConfirmDialog(frame,
				"The document has unsaved changes. Do you want to save before " + action + "?", "Unsaved Changes",
				JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (result == JOptionPane.YES_OPTION) {
			save();
			return !isModified; // a cancelled "Save as" or a failed save aborts
		}
		return result == JOptionPane.NO_OPTION;
	}

	private void newFile() {
		if (!confirmDiscardChanges("creating a new file")) {
			return;
		}
		replaceText("", 0);
		currentFilePath = null;
		currentFileName = DEFAULT_FILE_NAME;
		currentFileSize = 0;
		currentCharset = StandardCharsets.UTF_8;
		currentLineSeparator = System.lineSeparator();
		currentHasBom = false;
		lastModifiedOnDisk = 0;
		markAsSaved();
	}

	private void openFile() {
		if (!confirmDiscardChanges("opening another file")) {
			return;
		}
		JFileChooser chooser = createFileChooser();
		if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
			loadFile(chooser.getSelectedFile(), false);
		}
	}

	/**
	 * Load a file (public API, used by the SPMF GUI).
	 * 
	 * @param file the file to load
	 */
	public void openAFile(File file) {
		if (!confirmDiscardChanges("opening another file")) {
			return;
		}
		loadFile(file, false);
	}

	private void reloadFile() {
		if (currentFilePath == null || loading) {
			return;
		}
		if (isModified) {
			int result = JOptionPane.showConfirmDialog(frame,
					"Reloading will discard your unsaved changes. Continue?", "Reload",
					JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (result != JOptionPane.YES_OPTION) {
				return;
			}
		}
		loadFile(new File(currentFilePath), true);
	}

	private JFileChooser createFileChooser() {
		JFileChooser chooser = new JFileChooser();
		File dir = null;
		if (currentFilePath != null) {
			dir = new File(currentFilePath).getAbsoluteFile().getParentFile();
		}
		if (dir == null) {
			String last = PREFS.get(PREF_LAST_DIRECTORY, null);
			if (last != null) {
				dir = new File(last);
			}
		}
		if (dir != null && dir.isDirectory()) {
			chooser.setCurrentDirectory(dir);
		}
		return chooser;
	}

	private void rememberDirectory(File file) {
		File parent = file.getAbsoluteFile().getParentFile();
		if (parent != null) {
			PREFS.put(PREF_LAST_DIRECTORY, parent.getAbsolutePath());
		}
	}

	/**
	 * Load a file in a background thread.
	 * 
	 * @param file              the file
	 * @param keepCaretPosition true to try to keep the caret where it is (reload)
	 */
	private void loadFile(File file, boolean keepCaretPosition) {
		if (loading) {
			return;
		}
		if (file == null || !file.isFile()) {
			JOptionPane.showMessageDialog(frame,
					"File does not exist: " + (file == null ? "" : file.getAbsolutePath()), "File Not Found",
					JOptionPane.ERROR_MESSAGE);
			if (file != null) {
				removeRecentFile(file.getAbsolutePath());
			}
			return;
		}
		if (!file.canRead()) {
			JOptionPane.showMessageDialog(frame, "Cannot read file: " + file.getAbsolutePath(), "File Read Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		double sizeMb = file.length() / BYTES_TO_MB;
		if (sizeMb > LARGE_FILE_WARNING_MB) {
			int result = JOptionPane.showConfirmDialog(frame,
					String.format("This file is %.2f MB. Opening large files may take time. Continue?", sizeMb),
					"Large File Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (result != JOptionPane.YES_OPTION) {
				return;
			}
		}

		final File absolute = file.getAbsoluteFile();
		final int caretToRestore = keepCaretPosition ? textAreaX.getCaretPosition() : 0;
		setLoading(true);

		SwingWorker<TextFileIO.LoadedText, Void> worker = new SwingWorker<TextFileIO.LoadedText, Void>() {
			@Override
			protected TextFileIO.LoadedText doInBackground() throws Exception {
				return TextFileIO.load(absolute);
			}

			@Override
			protected void done() {
				try {
					TextFileIO.LoadedText loaded = get();
					replaceText(loaded.text, caretToRestore);
					currentFilePath = absolute.getAbsolutePath();
					currentFileName = absolute.getName();
					currentFileSize = absolute.length() / BYTES_TO_MB;
					currentCharset = loaded.charset;
					currentLineSeparator = loaded.lineSeparator;
					currentHasBom = loaded.hasBom;
					lastModifiedOnDisk = loaded.lastModified;
					addRecentFile(currentFilePath);
					rememberDirectory(absolute);
					markAsSaved();
				} catch (ExecutionException e) {
					Throwable cause = e.getCause();
					if (cause instanceof OutOfMemoryError) {
						JOptionPane.showMessageDialog(frame, "File is too large to open. Not enough memory.",
								"Out of Memory", JOptionPane.ERROR_MESSAGE);
					} else {
						JOptionPane.showMessageDialog(frame, "Error reading file: " + cause.getMessage(),
								"File Read Error", JOptionPane.ERROR_MESSAGE);
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					setLoading(false);
					updateStatusBar();
					textAreaX.requestFocusInWindow();
				}
			}
		};
		worker.execute();
	}

	/** Lock/unlock the editor while a file is loading */
	private void setLoading(boolean value) {
		loading = value;
		textAreaX.setEditable(!value);
		frame.setCursor(value ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
		if (value) {
			updateStatusBar();
		}
	}

	/**
	 * Replace the whole text without recording it as a modification or an
	 * undoable edit.
	 */
	private void replaceText(String text, int caretPosition) {
		boolean wasLoading = loading;
		loading = true; // suppress modification tracking
		try {
			findReplaceBar.clearHighlights();
			textAreaX.setText(text);
		} finally {
			loading = wasLoading;
		}
		undoTool.discardAllEdits();
		textAreaX.setCaretPosition(clamp(caretPosition, 0, textAreaX.getDocument().getLength()));
		scrollCaretIntoView();
		if (findReplaceBar.isVisible()) {
			findReplaceBar.refresh();
		}
	}

	/**
	 * Save the document (asks for a name if it has none).
	 * 
	 * @return true if the document was saved
	 */
	private boolean save() {
		if (currentFilePath == null) {
			return saveAs();
		}
		File file = new File(currentFilePath);
		if (file.exists() && lastModifiedOnDisk != 0 && file.lastModified() != lastModifiedOnDisk) {
			int result = JOptionPane.showConfirmDialog(frame,
					"The file has been modified on disk by another program.\nOverwrite it with your version?",
					"File Changed on Disk", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (result != JOptionPane.YES_OPTION) {
				return false;
			}
		}
		return saveToFile(file);
	}

	/**
	 * Save the document under a new name.
	 * 
	 * @return true if the document was saved
	 */
	private boolean saveAs() {
		JFileChooser chooser = createFileChooser();
		if (currentFilePath != null) {
			chooser.setSelectedFile(new File(currentFilePath));
		} else {
			chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), currentFileName));
		}
		while (true) {
			if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
				return false;
			}
			File file = chooser.getSelectedFile();
			if (file.isDirectory()) {
				JOptionPane.showMessageDialog(frame, "Please enter a file name.", "Save", JOptionPane.WARNING_MESSAGE);
				continue;
			}
			if (file.exists()) {
				int result = JOptionPane.showConfirmDialog(frame,
						file.getName() + " already exists. Do you want to overwrite it?", "Confirm Overwrite",
						JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
				if (result == JOptionPane.NO_OPTION) {
					continue; // choose another name
				}
				if (result != JOptionPane.YES_OPTION) {
					return false;
				}
			}
			return saveToFile(file);
		}
	}

	/**
	 * Write the document to a file and update the editor state on success.
	 */
	private boolean saveToFile(File file) {
		try {
			TextFileIO.SaveResult result = TextFileIO.save(file, textAreaX.getText(), currentCharset,
					currentLineSeparator, currentHasBom);
			if (!result.charset.equals(currentCharset)) {
				JOptionPane.showMessageDialog(frame,
						"The text contains characters that cannot be represented in " + currentCharset.name()
								+ ".\nThe file was saved as UTF-8 instead.",
						"Encoding Changed", JOptionPane.INFORMATION_MESSAGE);
				currentCharset = result.charset;
			}
			currentFilePath = file.getAbsolutePath();
			currentFileName = file.getName();
			currentFileSize = result.size / BYTES_TO_MB;
			lastModifiedOnDisk = result.lastModified;
			addRecentFile(currentFilePath);
			rememberDirectory(file);
			markAsSaved();
			return true;
		} catch (IOException e) {
			JOptionPane.showMessageDialog(frame, "Error saving file: " + e.getMessage(), "File Save Error",
					JOptionPane.ERROR_MESSAGE);
			return false;
		}
	}

	/** Offer to reload the file if another program changed it */
	private void checkForExternalModification() {
		if (checkingExternalChange || loading || currentFilePath == null || lastModifiedOnDisk == 0) {
			return;
		}
		File file = new File(currentFilePath);
		if (!file.exists()) {
			return;
		}
		long onDisk = file.lastModified();
		if (onDisk == lastModifiedOnDisk) {
			return;
		}
		checkingExternalChange = true;
		try {
			String message = currentFileName + " has been modified by another program.\n"
					+ (isModified ? "Reload it and lose your unsaved changes?" : "Reload it?");
			int result = JOptionPane.showConfirmDialog(frame, message, "File Changed on Disk",
					JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
			if (result == JOptionPane.YES_OPTION) {
				loadFile(file, true);
			} else {
				lastModifiedOnDisk = onDisk; // do not ask again for this change
			}
		} finally {
			checkingExternalChange = false;
		}
	}

	private void print() {
		try {
			if (textAreaX.print()) {
				JOptionPane.showMessageDialog(frame, "Printing completed successfully.", "Print",
						JOptionPane.INFORMATION_MESSAGE);
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(frame, "Error printing document: " + e.getMessage(), "Print Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	// =====================================================================
	// Closing
	// =====================================================================

	private void handleQuit() {
		if (!confirmDiscardChanges("quitting")) {
			return;
		}
		if (runAsStandalone) {
			frame.dispose();
			System.exit(0);
		} else {
			frame.setVisible(false);
		}
	}

	private void handleClose() {
		if (!confirmDiscardChanges("closing")) {
			return;
		}
		frame.setVisible(false);
	}

	// =====================================================================
	// Window geometry
	// =====================================================================

	private void saveWindowDimensions() {
		if (frame == null || scrollPane == null) {
			return;
		}
		// Do not remember a maximized size as the normal size
		if ((frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0) {
			return;
		}
		PreferencesManager prefs = PreferencesManager.getInstance();
		prefs.setTextEditorAreaHeight(scrollPane.getHeight());
		prefs.setTextEditorAreaWidth(scrollPane.getWidth());
		prefs.setTextEditorHeight(frame.getHeight());
		prefs.setTextEditorWidth(frame.getWidth());
	}

	private void saveWindowPosition() {
		if (frame == null || (frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0) {
			return;
		}
		PreferencesManager prefs = PreferencesManager.getInstance();
		prefs.setTextEditorX(frame.getX());
		prefs.setTextEditorY(frame.getY());
	}

	/** Union of all monitors */
	private java.awt.Rectangle getEffectiveScreenArea() {
		java.awt.Rectangle union = null;
		for (java.awt.GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
			java.awt.Rectangle bounds = device.getDefaultConfiguration().getBounds();
			union = (union == null) ? new java.awt.Rectangle(bounds) : union.union(bounds);
		}
		if (union == null) {
			Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
			union = new java.awt.Rectangle(0, 0, screen.width, screen.height);
		}
		return union;
	}

	private static int clamp(int value, int min, int max) {
		if (max < min) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}

	// =====================================================================
	// Public API
	// =====================================================================

	/** @return the main frame */
	public JFrame getFrame() {
		return frame;
	}

	/** @return the text area */
	public JTextArea getTextArea() {
		return textAreaX;
	}

	/**
	 * Replace the text content (keeps the current file association).
	 * 
	 * @param text the text to set
	 */
	public void setText(String text) {
		replaceText(text == null ? "" : TextFileIO.normalizeLineEndings(text), 0);
		markAsSaved();
	}

	/** @return the current text */
	public String getText() {
		return textAreaX.getText();
	}

	/** @return true if the document has unsaved changes */
	public boolean isModified() {
		return isModified;
	}

	/** @return the path of the current file, or null if none */
	public String getCurrentFilePath() {
		return currentFilePath;
	}
}