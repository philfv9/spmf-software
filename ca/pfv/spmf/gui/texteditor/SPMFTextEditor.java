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
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
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
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

import ca.pfv.spmf.gui.preferences.PreferencesManager;

/**
 * This is a simple text editor, adapted for SPMF.
 * 
 * @author Philippe Fournier-Viger
 */
public class SPMFTextEditor implements ActionListener {

	// ==================== CONSTANTS ====================
	private static final String WINDOW_TITLE_PREFIX = "SPMF Text Editor - ";
	private static final String DEFAULT_FILE_NAME = "Untitled";
	private static final double BYTES_TO_MB = 1024.0 * 1024.0;
	private static final int MIN_FONT_SIZE = 6;
	private static final int MAX_FONT_SIZE = 72;

	// ==================== UI COMPONENTS ====================
	/** The text area */
	private JTextArea textAreaX;

	/** The JFrame */
	private JFrame frame;

	/** A JScrollPane so as to have scrollbars */
	private JScrollPane scrollPane = null;

	/** An object to highlight the current line in the text area */
	private LinePainter linePainter = null;

	/** The status bar */
	private JTextField statusBar;

	/** The search bar */
	private JTextField searchBar;

	/** The search bar panel */
	private JPanel searchBarPanel;

	/** The search bar label */
	private JLabel searchBarLabel;

	// ==================== STATE VARIABLES ====================
	/** Flag indicating if the application is run as a standalone program or not */
	private boolean runAsStandalone = true;

	/** If true the night mode is activated. Otherwise not */
	private boolean nightMode = false;

	/** The path of the current file */
	private String currentFilePath = null;

	/** The name of the current file */
	private String currentFileName = DEFAULT_FILE_NAME;

	/** The color used for highlighting words that we search */
	private Color colorSearchHighlights = Color.ORANGE;

	/** List of current objects highlighted by the search bar */
	private List<Object> currentHighlightTags = new ArrayList<Object>();

	/** Current file size in MB */
	private double currentFileSize = 0;

	/** Tool to manage undo */
	private UndoTool undoTool;

	/** Flag to track if document has been modified */
	private boolean isModified = false;

	/** menu search bar */
	JCheckBoxMenuItem menuSearchBar = null;

	/**
	 * Constructor
	 * 
	 * @param runAsStandalone true if run as a standalone program, otherwise false
	 */
	public SPMFTextEditor(boolean runAsStandalone) {
		this.runAsStandalone = runAsStandalone;

		// Load preferences and initialize UI
		loadPreferencesAndInitializeUI();
	}

	/**
	 * Load preferences from registry and initialize the UI
	 */
	private void loadPreferencesAndInitializeUI() {
		// ============== Load preferences from registry ======================
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

		// Get the previous position of the window
		int textEditorXPosition = prefs.getTextEditorX();
		int textEditorYPosition = prefs.getTextEditorY();

		// Get the effective screen area
		java.awt.Rectangle screenArea = getEffectiveScreenArea();

		// Adjust the window position to make sure it is not outside the effective
		// screen area
		textEditorXPosition = clamp(textEditorXPosition, screenArea.x, screenArea.width - windowWidth);
		textEditorYPosition = clamp(textEditorYPosition, screenArea.y, screenArea.height - windowHeight);

		// ========================================================

		// Create and configure the main frame
		createMainFrame(windowWidth, windowHeight, textEditorXPosition, textEditorYPosition);

		// Create the text area
		createTextArea(fontsize, fontFamily, lineWrap, wordWrap);

		// Create menu bar
		JMenuBar menuBar = createMenuBar();
		frame.setJMenuBar(menuBar);

		// Create scroll pane with line numbers
		createScrollPane(textAreaWidth, textAreaHeight);

		// Create status bar and search bar
		createStatusBar();
		createSearchBar();

		// Initialize line painter for current line highlighting
		linePainter = new LinePainter(textAreaX);

		// Add the undo tool
		undoTool = new UndoTool(textAreaX);

		// Apply night mode setting
		setNightMode(nightMode);

		// Update the status bar
		updateStatusBar();

		// Show the frame
		frame.pack();
		frame.setVisible(true);

		// Request focus on text area
		textAreaX.requestFocusInWindow();
	}

	/**
	 * Create and configure the main frame
	 */
	private void createMainFrame(int width, int height, int xPosition, int yPosition) {
		frame = new JFrame(WINDOW_TITLE_PREFIX + currentFileName);
		frame.setIconImage(Toolkit.getDefaultToolkit()
				.getImage(SPMFTextEditor.class.getResource("/ca/pfv/spmf/gui/icons/History24.gif")));

		frame.setSize(width, height);
		frame.setLocation(xPosition, yPosition);

		// Set close operation with save prompt if modified
		if (runAsStandalone) {
			frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			frame.addWindowListener(new java.awt.event.WindowAdapter() {
				@Override
				public void windowClosing(java.awt.event.WindowEvent windowEvent) {
					handleQuit();
				}
			});
		} else {
			frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			frame.addWindowListener(new java.awt.event.WindowAdapter() {
				@Override
				public void windowClosing(java.awt.event.WindowEvent windowEvent) {
					handleClose();
				}
			});
		}

		frame.getContentPane().setLayout(new BorderLayout(1, 0));

		// Add component listener for window resize/move events
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

	/**
	 * Create and configure the text area
	 */
	private void createTextArea(int fontSize, String fontFamily, boolean lineWrap, boolean wordWrap) {
		textAreaX = new JTextArea();
		textAreaX.setLineWrap(lineWrap);
		textAreaX.setWrapStyleWord(wordWrap);
		textAreaX.setFont(new java.awt.Font(fontFamily, java.awt.Font.PLAIN, fontSize));

		// Add caret listener to update status bar
		textAreaX.addCaretListener(new CaretListener() {
			public void caretUpdate(CaretEvent e) {
				updateStatusBar();
			}
		});

		// Add document listener to track modifications
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

		// Add key bindings for CTRL+F (Find) and CTRL+G (Go to Line)
		textAreaX.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F, ActionEvent.CTRL_MASK), "showSearch");
		textAreaX.getActionMap().put("showSearch", new javax.swing.AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showSearchBar();
			}
		});

		textAreaX.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_G, ActionEvent.CTRL_MASK), "goToLine");
		textAreaX.getActionMap().put("goToLine", new javax.swing.AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showGoToLineDialog();
			}
		});

		// Add ESC key binding to hide search bar
		textAreaX.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "hideSearch");
		textAreaX.getActionMap().put("hideSearch", new javax.swing.AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (searchBarPanel.isVisible()) {
					setSearchBar(false);
					clearHighlights();
				}
			}
		});
	}

	/**
	 * Create the scroll pane with line numbers
	 */
	private void createScrollPane(int width, int height) {
		scrollPane = new JScrollPane(textAreaX);
		scrollPane.setPreferredSize(new Dimension(width, height));
		scrollPane.setRowHeaderView(new LineNumberPane(textAreaX));
		frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
	}

	/**
	 * Create the status bar
	 */
	private void createStatusBar() {
		statusBar = new JTextField();
		statusBar.setEditable(false);
		statusBar.setEnabled(true);
		statusBar.setColumns(10);
		frame.getContentPane().add(statusBar, BorderLayout.SOUTH);
	}

	/**
	 * Create the search bar
	 */
	private void createSearchBar() {
		searchBar = new JTextField();
		searchBar.addCaretListener(new CaretListener() {
			public void caretUpdate(CaretEvent e) {
				find();
			}
		});
		searchBar.setEditable(true);
		searchBar.setEnabled(true);
		searchBar.setColumns(10);

		// Add ESC key binding to search bar
		searchBar.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "hideSearch");
		searchBar.getActionMap().put("hideSearch", new javax.swing.AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				setSearchBar(false);
				clearHighlights();
				textAreaX.requestFocusInWindow();
			}
		});

		searchBarPanel = new JPanel(new BorderLayout(1, 0));
		searchBarLabel = new JLabel("Search: ");
		searchBarPanel.add(searchBarLabel, BorderLayout.WEST);
		searchBarPanel.add(searchBar, BorderLayout.CENTER);
		frame.getContentPane().add(searchBarPanel, BorderLayout.NORTH);
	}

	/**
	 * Show the search bar and focus on search field
	 */
	private void showSearchBar() {
		if (!searchBarPanel.isVisible()) {
			setSearchBar(true);
		}
		searchBar.requestFocusInWindow();
		searchBar.selectAll();
	}

	/**
	 * Show the "Go to Line" dialog
	 */
	private void showGoToLineDialog() {
		int totalLines = textAreaX.getLineCount();
		int currentLine = getCurrentLine();

		String input = (String) JOptionPane.showInputDialog(frame, "Enter line number (1-" + totalLines + "):",
				"Go to Line", JOptionPane.PLAIN_MESSAGE, null, null, currentLine);

		if (input != null && !input.trim().isEmpty()) {
			try {
				int lineNumber = Integer.parseInt(input.trim());

				if (lineNumber < 1 || lineNumber > totalLines) {
					JOptionPane.showMessageDialog(frame, "Line number must be between 1 and " + totalLines + ".",
							"Invalid Line Number", JOptionPane.WARNING_MESSAGE);
					return;
				}

				goToLine(lineNumber);

			} catch (NumberFormatException ex) {
				JOptionPane.showMessageDialog(frame, "Please enter a valid line number.", "Invalid Input",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	/**
	 * Go to a specific line number
	 * 
	 * @param lineNumber the line number to go to (1-based)
	 */
	private void goToLine(int lineNumber) {
		try {
			// Convert to 0-based index
			int lineIndex = lineNumber - 1;

			// Get the start offset of the line
			int startOffset = textAreaX.getLineStartOffset(lineIndex);

			// Set the caret position
			textAreaX.setCaretPosition(startOffset);

			// Scroll to make the line visible
			textAreaX.requestFocusInWindow();

		} catch (BadLocationException ex) {
			JOptionPane.showMessageDialog(frame, "Error navigating to line: " + ex.getMessage(), "Navigation Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Get the current line number (1-based)
	 * 
	 * @return the current line number
	 */
	private int getCurrentLine() {
		try {
			int caretPosition = textAreaX.getCaretPosition();
			return textAreaX.getLineOfOffset(caretPosition) + 1;
		} catch (BadLocationException ex) {
			return 1;
		}
	}

	/**
	 * Clear all search highlights
	 */
	private void clearHighlights() {
		Highlighter highlighter = textAreaX.getHighlighter();
		for (Object tag : currentHighlightTags) {
			highlighter.removeHighlight(tag);
		}
		currentHighlightTags.clear();
	}

	/**
	 * Handle window close event
	 */
	private void handleClose() {
		if (isModified) {
			int result = JOptionPane.showConfirmDialog(frame,
					"The document has unsaved changes. Do you want to save before closing?", "Unsaved Changes",
					JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

			if (result == JOptionPane.YES_OPTION) {
				save();
			} else if (result == JOptionPane.CANCEL_OPTION) {
				return;
			}
		}

		frame.setVisible(false);
	}

	/**
	 * Create the menu bar with all menus
	 * 
	 * @return the configured menu bar
	 */
	private JMenuBar createMenuBar() {
		JMenuBar menuBar = new JMenuBar();

		menuBar.add(createFileMenu());
		menuBar.add(createEditMenu());
		menuBar.add(createViewMenu());

		return menuBar;
	}

	/**
	 * Create the File menu
	 * 
	 * @return the File menu
	 */
	private JMenu createFileMenu() {
		JMenu menuFile = new JMenu("File");
		menuFile.setMnemonic(KeyEvent.VK_F);

		// New
		JMenuItem menuFileNew = createMenuItem("New", "/ca/pfv/spmf/gui/icons/New24.gif", KeyEvent.VK_N, KeyEvent.VK_N,
				ActionEvent.CTRL_MASK);
		menuFile.add(menuFileNew);

		// Open
		JMenuItem menuFileOpen = createMenuItem("Open", "/ca/pfv/spmf/gui/icons/open.gif", KeyEvent.VK_O, KeyEvent.VK_O,
				ActionEvent.CTRL_MASK);
		menuFile.add(menuFileOpen);

		// Save
		JMenuItem menuFileSave = createMenuItem("Save", "/ca/pfv/spmf/gui/icons/save.gif", KeyEvent.VK_S, KeyEvent.VK_S,
				ActionEvent.CTRL_MASK);
		menuFile.add(menuFileSave);

		// Save as
		JMenuItem menuFileSaveAs = createMenuItem("Save as...", "/ca/pfv/spmf/gui/icons/SaveAs24.gif", KeyEvent.VK_A, 0,
				0);
		menuFile.add(menuFileSaveAs);

		menuFile.addSeparator();

		// Print
		JMenuItem menuFilePrint = createMenuItem("Print", "/ca/pfv/spmf/gui/icons/print.gif", KeyEvent.VK_P,
				KeyEvent.VK_P, ActionEvent.CTRL_MASK);
		menuFile.add(menuFilePrint);

		if (runAsStandalone) {
			menuFile.addSeparator();
			JMenuItem menuFileQuit = createMenuItem("Quit", null, KeyEvent.VK_Q, 0, 0);
			menuFile.add(menuFileQuit);
		}

		return menuFile;
	}

	/**
	 * Create the Edit menu
	 * 
	 * @return the Edit menu
	 */
	private JMenu createEditMenu() {
		JMenu menuEdit = new JMenu("Edit");
		menuEdit.setMnemonic(KeyEvent.VK_E);

		// Undo
		JMenuItem menuItemUndo = createMenuItem("Undo", "/ca/pfv/spmf/gui/icons/Undo24.gif", KeyEvent.VK_U,
				KeyEvent.VK_Z, ActionEvent.CTRL_MASK);
		menuEdit.add(menuItemUndo);

		// Redo
		JMenuItem menuItemRedo = createMenuItem("Redo", "/ca/pfv/spmf/gui/icons/Redo24.gif", KeyEvent.VK_R,
				KeyEvent.VK_Y, ActionEvent.CTRL_MASK);
		menuEdit.add(menuItemRedo);

		menuEdit.addSeparator();

		// Cut
		JMenuItem menuEditCut = createMenuItem("Cut", "/ca/pfv/spmf/gui/icons/Cut24.gif", KeyEvent.VK_T, KeyEvent.VK_X,
				ActionEvent.CTRL_MASK);
		menuEdit.add(menuEditCut);

		// Copy
		JMenuItem menuEditCopy = createMenuItem("Copy", "/ca/pfv/spmf/gui/icons/Copy24.gif", KeyEvent.VK_C,
				KeyEvent.VK_C, ActionEvent.CTRL_MASK);
		menuEdit.add(menuEditCopy);

		// Paste
		JMenuItem menuEditPaste = createMenuItem("Paste", "/ca/pfv/spmf/gui/icons/Paste24.gif", KeyEvent.VK_P,
				KeyEvent.VK_V, ActionEvent.CTRL_MASK);
		menuEdit.add(menuEditPaste);

		menuEdit.addSeparator();

		// Select all
		JMenuItem menuSelectAll = createMenuItem("Select all", null, KeyEvent.VK_A, KeyEvent.VK_A,
				ActionEvent.CTRL_MASK);
		menuEdit.add(menuSelectAll);

		menuEdit.addSeparator();

		// Find
		JMenuItem menuFind = createMenuItem("Find...", null, KeyEvent.VK_F, KeyEvent.VK_F, ActionEvent.CTRL_MASK);
		menuEdit.add(menuFind);

		menuEdit.addSeparator();

		// Go to Line
		JMenuItem menuGoToLine = createMenuItem("Go to Line...", null, KeyEvent.VK_G, KeyEvent.VK_G,
				ActionEvent.CTRL_MASK);
		menuEdit.add(menuGoToLine);

		return menuEdit;
	}

	/**
	 * Create the View menu
	 * 
	 * @return the View menu
	 */
	private JMenu createViewMenu() {
		JMenu menuView = new JMenu("View");
		menuView.setMnemonic(KeyEvent.VK_V);

		PreferencesManager prefs = PreferencesManager.getInstance();

		// Line wrap
		JCheckBoxMenuItem menuLineWrap = new JCheckBoxMenuItem("Line wrap");
		menuLineWrap.setSelected(prefs.getTextEditorLineWrap());
		menuLineWrap.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setLineWrap(menuLineWrap.isSelected());
			}
		});
		menuView.add(menuLineWrap);

		// Word wrap
		JCheckBoxMenuItem menuWordWrap = new JCheckBoxMenuItem("Word wrap");
		menuWordWrap.setSelected(prefs.getTextEditorWordWrap());
		menuWordWrap.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setWordWrap(menuWordWrap.isSelected());
				if (menuWordWrap.isSelected()) {
					menuLineWrap.setSelected(true);
					setLineWrap(true);
				}
			}
		});
		menuView.add(menuWordWrap);

		menuView.addSeparator();

		// Night mode
		JCheckBoxMenuItem menuNightMode = new JCheckBoxMenuItem("Night mode");
		menuNightMode.setSelected(nightMode);
		menuNightMode.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setNightMode(menuNightMode.isSelected());
			}
		});
		menuView.add(menuNightMode);

		menuView.addSeparator();

		// Search bar
		menuSearchBar = new JCheckBoxMenuItem("Search bar");
		menuSearchBar.setSelected(true);
		menuSearchBar.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setSearchBar(menuSearchBar.isSelected());
				if (!menuSearchBar.isSelected()) {
					clearHighlights();
				}
			}
		});
		menuView.add(menuSearchBar);

		// Status bar
		JCheckBoxMenuItem menuStatusBar = new JCheckBoxMenuItem("Status bar");
		menuStatusBar.setSelected(true);
		menuStatusBar.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setStatusBar(menuStatusBar.isSelected());
			}
		});
		menuView.add(menuStatusBar);

		menuView.addSeparator();

		// Font family
		JMenuItem menuFontFamily = new JMenuItem("Font family...");
		menuFontFamily.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				chooseFontFamily();
			}
		});
		menuView.add(menuFontFamily);

		// Font size
		JMenuItem menuSetFontSize = new JMenuItem("Font size...");
		menuSetFontSize.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				chooseFontSize();
			}
		});
		menuView.add(menuSetFontSize);

		return menuView;
	}

	/**
	 * Create a menu item with icon and accelerator
	 * 
	 * @param text                the menu item text
	 * @param iconPath            the path to the icon (can be null)
	 * @param mnemonic            the mnemonic key
	 * @param acceleratorKey      the accelerator key (0 if none)
	 * @param acceleratorModifier the accelerator modifier (0 if none)
	 * @return the created menu item
	 */
	private JMenuItem createMenuItem(String text, String iconPath, int mnemonic, int acceleratorKey,
			int acceleratorModifier) {
		JMenuItem menuItem = new JMenuItem(text);

		if (iconPath != null) {
			menuItem.setIcon(new ImageIcon(SPMFTextEditor.class.getResource(iconPath)));
		}

		if (mnemonic != 0) {
			menuItem.setMnemonic(mnemonic);
		}

		if (acceleratorKey != 0) {
			menuItem.setAccelerator(KeyStroke.getKeyStroke(acceleratorKey, acceleratorModifier));
		}

		menuItem.addActionListener(this);

		return menuItem;
	}

	/**
	 * Get the effective screen area (handles multiple monitors)
	 * 
	 * @return the effective screen area
	 */
	private java.awt.Rectangle getEffectiveScreenArea() {
		int minX = 0, minY = 0, maxX = 0, maxY = 0;
		GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
		int screenDevices = environment.getScreenDevices().length;

		for (java.awt.GraphicsDevice device : environment.getScreenDevices()) {
			java.awt.Rectangle bounds = device.getDefaultConfiguration().getBounds();
			minX = Math.min(minX, bounds.x);
			minY = Math.min(minY, bounds.y);
			maxX = Math.max(maxX, bounds.x + bounds.width);
			maxY = Math.max(maxY, bounds.y + bounds.height);
		}

		return new java.awt.Rectangle(minX, minY, (maxX - minX) / screenDevices, (maxY - minY) / screenDevices);
	}

	/**
	 * Choose font family dialog
	 */
	protected void chooseFontFamily() {
		// Get current font
		java.awt.Font currentFont = textAreaX.getFont();
		String currentFontFamily = currentFont.getFamily();

		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		String[] fontNames = ge.getAvailableFontFamilyNames();

		int selectedIndex = 0;
		for (int i = 0; i < fontNames.length; i++) {
			if (fontNames[i].equals(currentFontFamily)) {
				selectedIndex = i;
				break;
			}
		}

		JPanel panel = new JPanel(new BorderLayout(5, 5));
		JLabel label = new JLabel("The quick brown fox jumps over the lazy dog");
		label.setFont(textAreaX.getFont());

		JList<String> jlist = new JList<String>(fontNames);
		jlist.setSelectedIndex(selectedIndex);
		jlist.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				if (!e.getValueIsAdjusting()) {
					String fontFamily = jlist.getSelectedValue();
					if (fontFamily != null) {
						java.awt.Font currentFont = label.getFont();
						java.awt.Font newFont = new java.awt.Font(fontFamily, currentFont.getStyle(),
								currentFont.getSize());
						label.setFont(newFont);
					}
				}
			}
		});

		JScrollPane scrollPane = new JScrollPane(jlist);
		scrollPane.setPreferredSize(new Dimension(300, 400));
		panel.add(scrollPane, BorderLayout.CENTER);
		panel.add(label, BorderLayout.SOUTH);

		int result = JOptionPane.showConfirmDialog(frame, panel, "Choose font family:", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);

		// If the user clicked OK and selected a different font
		if (result == JOptionPane.OK_OPTION && selectedIndex != jlist.getSelectedIndex()) {
			String fontFamily = jlist.getSelectedValue();
			if (fontFamily != null) {
				setFontFamily(fontFamily);
			}
		}
	}

	/**
	 * Choose font size dialog
	 */
	protected void chooseFontSize() {
		int fontSize = PreferencesManager.getInstance().getTextEditorFontSize();

		String result = JOptionPane.showInputDialog(frame,
				"Enter font size (" + MIN_FONT_SIZE + "-" + MAX_FONT_SIZE + "):", fontSize);

		if (result != null) {
			try {
				int newFontSize = Integer.parseInt(result.trim());
				if (newFontSize >= MIN_FONT_SIZE && newFontSize <= MAX_FONT_SIZE) {
					setFontSize(newFontSize);
				} else {
					JOptionPane.showMessageDialog(frame,
							"Font size must be between " + MIN_FONT_SIZE + " and " + MAX_FONT_SIZE + ".",
							"Invalid Font Size", JOptionPane.ERROR_MESSAGE);
				}
			} catch (NumberFormatException exception) {
				JOptionPane.showMessageDialog(frame, "Font size must be a valid number.", "Invalid Input",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	/**
	 * Set the font family
	 * 
	 * @param fontFamily the font family name
	 */
	private void setFontFamily(String fontFamily) {
		java.awt.Font currentFont = textAreaX.getFont();
		java.awt.Font newFont = new java.awt.Font(fontFamily, currentFont.getStyle(), currentFont.getSize());
		textAreaX.setFont(newFont);
		PreferencesManager.getInstance().setFontFamilly(fontFamily);

		frame.revalidate();
		frame.repaint();
	}

	/**
	 * Show or hide the search bar
	 * 
	 * @param selected if true, show; if false, hide
	 */
	protected void setSearchBar(boolean selected) {
		searchBarPanel.setVisible(selected);
		frame.getContentPane().revalidate();
		frame.getContentPane().repaint();

		if (selected) {
			searchBar.requestFocusInWindow();
		} else {
			textAreaX.requestFocusInWindow();
		}

		menuSearchBar.setSelected(selected);
	}

	/**
	 * Activate or deactivate WordWrap
	 * 
	 * @param selected True = activate, False = deactivate
	 */
	protected void setWordWrap(boolean selected) {
		textAreaX.setWrapStyleWord(selected);
		PreferencesManager.getInstance().setTextEditorWordWrap(selected);
	}

	/**
	 * Show or hide the status bar
	 * 
	 * @param selected if true, show; if false, hide
	 */
	protected void setStatusBar(boolean selected) {
		statusBar.setVisible(selected);
		frame.getContentPane().revalidate();
		frame.getContentPane().repaint();
	}

	/**
	 * Find the word in the search bar and highlight all its occurrences
	 */
	protected void find() {
		// Remove previous highlights
		clearHighlights();

		// Get the search term
		String query = searchBar.getText();

		// If no search term or text is empty, return
		if (query.isEmpty() || textAreaX.getText().isEmpty()) {
			return;
		}

		String text = textAreaX.getText();
		int queryLength = query.length();

		Highlighter.HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(colorSearchHighlights);
		Highlighter highlighter = textAreaX.getHighlighter();

		int offset = 0;
		while ((offset = text.indexOf(query, offset)) != -1) {
			try {
				Object highlightTag = highlighter.addHighlight(offset, offset + queryLength, painter);
				currentHighlightTags.add(highlightTag);
				offset += 1;
			} catch (BadLocationException ex) {
				System.err.println("Error highlighting text: " + ex.getMessage());
				break;
			}
		}
	}

	/**
	 * Activate or deactivate the night mode
	 * 
	 * @param selected true = activate, otherwise deactivate
	 */
	protected void setNightMode(boolean selected) {
		this.nightMode = selected;

		if (selected) {
			// Night mode colors
			textAreaX.setBackground(Color.BLACK);
			textAreaX.setForeground(Color.WHITE);
			textAreaX.setCaretColor(Color.WHITE);

			// Apply night mode to search bar components
			searchBar.setBackground(Color.DARK_GRAY);
			searchBar.setForeground(Color.WHITE);
			searchBar.setCaretColor(Color.WHITE);
			searchBarPanel.setBackground(Color.DARK_GRAY);
			searchBarLabel.setForeground(Color.WHITE);

			// Apply night mode to status bar
			statusBar.setBackground(Color.DARK_GRAY);
			statusBar.setForeground(Color.WHITE);

			if (linePainter != null) {
				linePainter.setColor(Color.DARK_GRAY);
			}
			colorSearchHighlights = Color.RED;
		} else {
			// Day mode colors
			textAreaX.setBackground(Color.WHITE);
			textAreaX.setForeground(Color.BLACK);
			textAreaX.setCaretColor(Color.BLACK);

			// Apply day mode to search bar components
			searchBar.setBackground(Color.WHITE);
			searchBar.setForeground(Color.BLACK);
			searchBar.setCaretColor(Color.BLACK);
			searchBarPanel.setBackground(null);
			searchBarLabel.setForeground(Color.BLACK);

			// Apply day mode to status bar
			statusBar.setBackground(Color.WHITE);
			statusBar.setForeground(Color.BLACK);

			if (linePainter != null) {
				linePainter.setLighter(Color.LIGHT_GRAY);
			}
			colorSearchHighlights = Color.ORANGE;
		}

		// Reapply search highlights with new color
		find();

		PreferencesManager.getInstance().setNightMode(selected);
	}

	/**
	 * Set font size
	 * 
	 * @param fontSize the font size
	 */
	protected void setFontSize(int fontSize) {
		java.awt.Font newFont = textAreaX.getFont().deriveFont((float) fontSize);
		textAreaX.setFont(newFont);

		PreferencesManager.getInstance().setTextEditorFontSize(fontSize);

		frame.revalidate();
		frame.repaint();
	}

	/**
	 * Update the status bar with current cursor position and line count
	 */
	protected void updateStatusBar() {
		int caretPosition = textAreaX.getCaretPosition();
		int columnPosition = 1;
		int linePosition = 1;

		try {
			linePosition = textAreaX.getLineOfOffset(caretPosition);
			columnPosition = caretPosition - textAreaX.getLineStartOffset(linePosition);
			linePosition = linePosition + 1;

			int lineCount = textAreaX.getLineCount();
			int charCount = textAreaX.getText().length();
			String selectedText = textAreaX.getSelectedText();

			StringBuilder status = new StringBuilder();
			status.append("Line: ").append(linePosition);
			status.append("  |  Column: ").append(columnPosition);
			status.append("  |  Lines: ").append(lineCount);
			status.append("  |  Characters: ").append(charCount);

			if (selectedText != null && !selectedText.isEmpty()) {
				status.append("  |  Selected: ").append(selectedText.length());
			}

			if (isModified) {
				status.append("  |  Modified");
			}

			statusBar.setText(status.toString());
		} catch (BadLocationException exception) {
			statusBar.setText("Ready");
		}
	}

	/**
	 * Activate or deactivate the line wrap function
	 * 
	 * @param selected if true, activate, otherwise, deactivate
	 */
	protected void setLineWrap(boolean selected) {
		textAreaX.setLineWrap(selected);
		PreferencesManager.getInstance().setTextEditorLineWrap(selected);
	}

	/**
	 * Mark the document as modified
	 */
	private void markAsModified() {
		if (!isModified) {
			isModified = true;
			updateWindowTitle();
			updateStatusBar();
		}
	}

	/**
	 * Mark the document as saved (not modified)
	 */
	private void markAsSaved() {
		isModified = false;
		updateWindowTitle();
		updateStatusBar();
	}

	/**
	 * Update the window title
	 */
	private void updateWindowTitle() {
		StringBuilder title = new StringBuilder(WINDOW_TITLE_PREFIX);

		if (isModified) {
			title.append("* ");
		}

		title.append(currentFileName);

		if (currentFilePath != null) {
			String fileSize = String.format("%.4f", currentFileSize);
			title.append("  (").append(fileSize).append(" MB)");
		}

		if (frame != null)
			frame.setTitle(title.toString());
	}

	/**
	 * Save window dimensions to preferences
	 */
	private void saveWindowDimensions() {
		PreferencesManager prefs = PreferencesManager.getInstance();
		prefs.setTextEditorAreaHeight(scrollPane.getHeight());
		prefs.setTextEditorAreaWidth(scrollPane.getWidth());
		prefs.setTextEditorHeight(frame.getHeight());
		prefs.setTextEditorWidth(frame.getWidth());
	}

	/**
	 * Save window position to preferences
	 */
	private void saveWindowPosition() {
		PreferencesManager prefs = PreferencesManager.getInstance();
		prefs.setTextEditorX(frame.getX());
		prefs.setTextEditorY(frame.getY());
	}

	/**
	 * Clamp a value between min and max
	 * 
	 * @param value the value to clamp
	 * @param min   the minimum value
	 * @param max   the maximum value
	 * @return the clamped value
	 */
	private int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	/**
	 * Handle menu item actions
	 */
	public void actionPerformed(ActionEvent actionEvent) {
		String actionCommand = actionEvent.getActionCommand();

		switch (actionCommand) {
		case "Cut":
			textAreaX.cut();
			break;
		case "Copy":
			textAreaX.copy();
			break;
		case "Paste":
			textAreaX.paste();
			break;
		case "Undo":
			if (undoTool != null) {
				undoTool.undo();
			}
			break;
		case "Redo":
			if (undoTool != null) {
				undoTool.redo();
			}
			break;
		case "Quit":
			handleQuit();
			break;
		case "Save":
			save();
			break;
		case "Save as...":
			saveAs();
			break;
		case "Select all":
			textAreaX.selectAll();
			break;
		case "Print":
			print();
			break;
		case "Open":
			openFile();
			break;
		case "New":
			newFile();
			break;
		case "Find...":
			showSearchBar();
			break;
		case "Go to Line...":
			showGoToLineDialog();
			break;
		default:
			break;
		}
	}

	/**
	 * Handle quit action
	 */
	private void handleQuit() {
		if (isModified) {
			int result = JOptionPane.showConfirmDialog(frame,
					"The document has unsaved changes. Do you want to save before quitting?", "Unsaved Changes",
					JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

			if (result == JOptionPane.YES_OPTION) {
				save();
			} else if (result == JOptionPane.CANCEL_OPTION) {
				return;
			}
		}

		if (runAsStandalone) {
			System.exit(0);
		} else {
			frame.setVisible(false);
		}
	}

	/**
	 * Create a new file
	 */
	private void newFile() {
		if (isModified) {
			int result = JOptionPane.showConfirmDialog(frame,
					"The current document has unsaved changes. Do you want to save before creating a new file?",
					"Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

			if (result == JOptionPane.YES_OPTION) {
				save();
			} else if (result == JOptionPane.CANCEL_OPTION) {
				return;
			}
		}

		setCurrentFile(null, DEFAULT_FILE_NAME);
		textAreaX.setText("");
		markAsSaved();
	}

	/**
	 * Open a file using file chooser
	 */
	private void openFile() {
		if (isModified) {
			int result = JOptionPane.showConfirmDialog(frame,
					"The current document has unsaved changes. Do you want to save before opening a new file?",
					"Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

			if (result == JOptionPane.YES_OPTION) {
				save();
			} else if (result == JOptionPane.CANCEL_OPTION) {
				return;
			}
		}

		JFileChooser fileChooser = new JFileChooser();

		// Set the current directory to the last opened file's directory
		if (currentFilePath != null) {
			fileChooser.setCurrentDirectory(new File(currentFilePath).getParentFile());
		}

		int resultCode = fileChooser.showOpenDialog(frame);

		if (resultCode == JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();
			openAFile(file);
		}
	}

	/**
	 * Print the document
	 */
	private void print() {
		try {
			boolean complete = textAreaX.print();
			if (complete) {
				JOptionPane.showMessageDialog(frame, "Printing completed successfully.", "Print",
						JOptionPane.INFORMATION_MESSAGE);
			} else {
				JOptionPane.showMessageDialog(frame, "Printing was cancelled.", "Print", JOptionPane.WARNING_MESSAGE);
			}
		} catch (Exception exception) {
			JOptionPane.showMessageDialog(frame, "Error printing document: " + exception.getMessage(), "Print Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Load a file
	 * 
	 * @param file the file to load
	 */
	public void openAFile(File file) {
		if (!file.exists()) {
			JOptionPane.showMessageDialog(frame, "File does not exist: " + file.getAbsolutePath(), "File Not Found",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (!file.canRead()) {
			JOptionPane.showMessageDialog(frame, "Cannot read file: " + file.getAbsolutePath(), "File Read Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		try {
			// Calculate file size
			currentFileSize = file.length() / BYTES_TO_MB;

			// Warn for large files
			if (currentFileSize > 10) {
				int result = JOptionPane.showConfirmDialog(frame,
						String.format("This file is %.2f MB. Opening large files may take time. Continue?",
								currentFileSize),
						"Large File Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

				if (result != JOptionPane.YES_OPTION) {
					return;
				}
			}

			// Remember the file name and path
			String absolutePath = file.getAbsolutePath();
			String filename = file.getName();
			setCurrentFile(absolutePath, filename);

			// Read file content
			String content = new String(Files.readAllBytes(Paths.get(absolutePath)), StandardCharsets.UTF_8);

			// Set the text
			textAreaX.setText(content);
			textAreaX.setCaretPosition(0);

			// Mark as saved
			markAsSaved();

			// Update the status bar
			updateStatusBar();

		} catch (IOException exception) {
			JOptionPane.showMessageDialog(frame, "Error reading file: " + exception.getMessage(), "File Read Error",
					JOptionPane.ERROR_MESSAGE);
		} catch (OutOfMemoryError error) {
			JOptionPane.showMessageDialog(frame, "File is too large to open. Not enough memory.", "Out of Memory",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Save to file
	 */
	private void save() {
		if (currentFilePath == null) {
			saveAs();
		} else {
			saveToFile(new File(currentFilePath));
		}
	}

	/**
	 * Save the currently opened file as a new file
	 */
	private void saveAs() {
		JFileChooser fileChooser = new JFileChooser();

		// Set the current directory
		if (currentFilePath != null) {
			fileChooser.setCurrentDirectory(new File(currentFilePath).getParentFile());
			fileChooser.setSelectedFile(new File(currentFileName));
		}

		int resultCode = fileChooser.showSaveDialog(frame);

		if (resultCode == JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();

			// Check if file exists and confirm overwrite
			if (file.exists()) {
				int result = JOptionPane.showConfirmDialog(frame, "File already exists. Do you want to overwrite it?",
						"Confirm Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

				if (result != JOptionPane.YES_OPTION) {
					return;
				}
			}

			// Remember the file name and path
			String absolutePath = file.getAbsolutePath();
			String filename = file.getName();
			setCurrentFile(absolutePath, filename);

			saveToFile(file);
		}
	}

	/**
	 * Save content to a specific file
	 * 
	 * @param file the file to save to
	 */
	private void saveToFile(File file) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
			String text = textAreaX.getText();
			writer.write(text);
			writer.flush();

			currentFileSize = file.length() / BYTES_TO_MB;
			markAsSaved();

		} catch (IOException exception) {
			JOptionPane.showMessageDialog(frame, "Error saving file: " + exception.getMessage(), "File Save Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Set information about the currently opened file
	 * 
	 * @param filePath the file path
	 * @param fileName the file name
	 */
	void setCurrentFile(String filePath, String fileName) {
		this.currentFilePath = filePath;
		this.currentFileName = fileName;

		if (filePath == null) {
			currentFileSize = 0;
		}

		updateWindowTitle();
	}

	/**
	 * Get the main frame
	 * 
	 * @return the JFrame
	 */
	public JFrame getFrame() {
		return frame;
	}

	/**
	 * Get the text area
	 * 
	 * @return the JTextArea
	 */
	public JTextArea getTextArea() {
		return textAreaX;
	}

	/**
	 * Set the text content
	 * 
	 * @param text the text to set
	 */
	public void setText(String text) {
		textAreaX.setText(text);
		textAreaX.setCaretPosition(0);
		markAsSaved();
	}

	/**
	 * Get the text content
	 * 
	 * @return the current text
	 */
	public String getText() {
		return textAreaX.getText();
	}

	/**
	 * Check if the document has been modified
	 * 
	 * @return true if modified, false otherwise
	 */
	public boolean isModified() {
		return isModified;
	}
}