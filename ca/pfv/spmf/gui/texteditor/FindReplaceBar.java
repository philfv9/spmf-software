package ca.pfv.spmf.gui.texteditor;

/*
 * Copyright (c) 2008-2022 Philippe Fournier-Viger
 * This file is part of the SPMF DATA MINING SOFTWARE (http://www.philippe-fournier-viger.com/spmf).
 * SPMF is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version. SPMF is distributed WITHOUT ANY
 * WARRANTY. See the GNU General Public License for more details.
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;

/**
 * A find / replace bar for a JTextArea. Supports find next/previous with wrap
 * around, match case, whole word, regular expressions, replace and replace all,
 * and highlights all matches.
 * <p>
 * The bar is responsive: the text fields always take the remaining width, and
 * when the window is too narrow the buttons and options move to an extra row
 * that wraps, so the fields never disappear.
 * 
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class FindReplaceBar extends JPanel {

	/** Do not add more highlights than this (performance on huge files) */
	private static final int MAX_HIGHLIGHTS = 5000;
	/** Stop counting matches after this many */
	private static final int MAX_COUNTED_MATCHES = 200_000;
	/** Maximum length of a selection used to pre-fill the find field */
	private static final int MAX_PREFILL_LENGTH = 200;
	/** Below this field width the bar switches to the compact layout */
	private static final int MIN_FIELD_WIDTH = 140;
	/** Gap between components */
	private static final int GAP = 3;
	/** Background of the find field when the query is invalid */
	private static final Color ERROR_BACKGROUND = new Color(255, 190, 190);

	private final JTextArea textArea;
	private final UndoTool undoTool;

	// ---- widgets ----
	private final JLabel findLabel = new JLabel("Find:");
	private final JLabel replaceLabel = new JLabel("Replace:");
	private final JTextField findField = new JTextField(12);
	private final JTextField replaceField = new JTextField(12);
	private final JButton previousButton = new JButton("Prev");
	private final JButton nextButton = new JButton("Next");
	private final JButton replaceButton = new JButton("Replace");
	private final JButton replaceAllButton = new JButton("Replace all");
	private final JButton closeButton = new JButton("\u00D7");
	private final JToggleButton matchCaseButton = new JToggleButton("Aa");
	private final JToggleButton wholeWordButton = new JToggleButton("W");
	private final JToggleButton regexButton = new JToggleButton(".*");
	private final JLabel statusLabel = new JLabel(" ");

	// ---- containers ----
	private final JPanel findRow = transparent(new BorderLayout(GAP, 0));
	private final JPanel replaceRow = transparent(new BorderLayout(GAP, 0));
	private final JPanel findEast = transparent(new BorderLayout(GAP, 0));
	private final JPanel replaceEast = transparent(new BorderLayout(GAP, 0));
	private final JPanel findControls = transparent(new FlowLayout(FlowLayout.LEFT, GAP, 0));
	private final JPanel replaceControls = transparent(new FlowLayout(FlowLayout.LEFT, GAP, 0));
	/** Third row used in compact mode */
	private final WrapPanel extraRow = new WrapPanel();
	/** Controls that live in the find row (normal) or the extra row (compact) */
	private final Component[] findControlList;
	/** Controls that live in the replace row (normal) or the extra row (compact) */
	private final Component[] replaceControlList;
	private boolean compactMode = false;
	private boolean layoutInitialized = false;

	// ---- search state ----
	/** Debounces the re-highlighting while the user types */
	private final Timer refreshTimer;
	/** Highlight tags currently installed in the text area */
	private final List<Object> highlightTags = new ArrayList<>();
	/** Start/end offsets of the matches found by the last refresh */
	private final List<int[]> matches = new ArrayList<>();
	private boolean matchesTruncated = false;
	/** Compiled query, or null if there is none / it is invalid */
	private Pattern pattern = null;
	/** Message prepended to the status once, at the next refresh */
	private String pendingStatusPrefix = null;

	private Color highlightColor = Color.ORANGE;
	private Color fieldBackground = Color.WHITE;
	private Color fieldForeground = Color.BLACK;
	private Runnable onClose = null;

	/**
	 * Constructor
	 * 
	 * @param textArea the text area to search in
	 * @param undoTool the undo tool of the text area (used to group replacements)
	 */
	public FindReplaceBar(JTextArea textArea, UndoTool undoTool) {
		this.textArea = textArea;
		this.undoTool = undoTool;
		this.refreshTimer = new Timer(150, e -> refresh());
		this.refreshTimer.setRepeats(false);
		this.findControlList = new Component[] { previousButton, nextButton, matchCaseButton, wholeWordButton,
				regexButton, statusLabel };
		this.replaceControlList = new Component[] { replaceButton, replaceAllButton };

		buildLayout();
		installListeners();
		installKeyBindings();
		setVisible(false);
	}

	// =====================================================================
	// UI construction / responsive layout
	// =====================================================================

	private static JPanel transparent(LayoutManager layout) {
		JPanel panel = new JPanel(layout);
		panel.setOpaque(false);
		return panel;
	}

	private void buildLayout() {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(new EmptyBorder(2, 4, 2, 4));

		// Rows always get the full width of the bar
		findRow.setBorder(new EmptyBorder(1, 0, 1, 0));
		replaceRow.setBorder(new EmptyBorder(1, 0, 1, 0));
		extraRow.setBorder(new EmptyBorder(1, 0, 1, 0));
		add(findRow);
		add(replaceRow);
		add(extraRow);

		// Same width for both labels so that the fields line up
		Dimension labelSize = new Dimension(
				Math.max(findLabel.getPreferredSize().width, replaceLabel.getPreferredSize().width),
				findLabel.getPreferredSize().height);
		findLabel.setPreferredSize(labelSize);
		replaceLabel.setPreferredSize(labelSize);

		findRow.add(findLabel, BorderLayout.WEST);
		findRow.add(findField, BorderLayout.CENTER);
		findRow.add(findEast, BorderLayout.EAST);
		findEast.add(findControls, BorderLayout.CENTER);
		findEast.add(closeButton, BorderLayout.EAST);

		replaceRow.add(replaceLabel, BorderLayout.WEST);
		replaceRow.add(replaceField, BorderLayout.CENTER);
		replaceRow.add(replaceEast, BorderLayout.EAST);
		replaceEast.add(replaceControls, BorderLayout.CENTER);

		// Compact buttons
		Insets buttonMargin = new Insets(1, 6, 1, 6);
		for (AbstractButton b : new AbstractButton[] { previousButton, nextButton, replaceButton, replaceAllButton,
				closeButton, matchCaseButton, wholeWordButton, regexButton }) {
			b.setFocusable(false);
			b.setMargin(buttonMargin);
		}
		previousButton.setToolTipText("Find previous (Shift+Enter / Shift+F3)");
		nextButton.setToolTipText("Find next (Enter / F3)");
		replaceButton.setToolTipText("Replace the current match and find the next one");
		replaceAllButton.setToolTipText("Replace all matches (one undo step)");
		closeButton.setToolTipText("Close (Esc)");
		matchCaseButton.setToolTipText("Match case");
		wholeWordButton.setToolTipText("Whole word");
		regexButton.setToolTipText(
				"Regular expression (Java syntax). Use $1, $2... in the replacement for groups.");

		// Fixed width so that the layout does not jump while typing
		FontMetrics fm = statusLabel.getFontMetrics(statusLabel.getFont());
		statusLabel.setPreferredSize(new Dimension(fm.stringWidth("99999 of 99999") + 8, fm.getHeight()));

		// Start in normal mode; the real mode is chosen as soon as the width is known
		applyLayoutMode(Integer.MAX_VALUE);

		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				if (applyLayoutMode(getWidth())) {
					revalidate();
				}
			}
		});
	}

	/**
	 * The preferred height depends on the available width (compact mode, wrapped
	 * rows), so the layout mode is chosen here, before the parent allocates our
	 * bounds.
	 */
	@Override
	public Dimension getPreferredSize() {
		if (!isPreferredSizeSet()) {
			int width = availableWidth();
			if (width > 0) {
				applyLayoutMode(width);
			}
		}
		return super.getPreferredSize();
	}

	/** Width the parent will most likely give us */
	private int availableWidth() {
		Container parent = getParent();
		if (parent != null && parent.getWidth() > 0) {
			Insets in = parent.getInsets();
			return parent.getWidth() - in.left - in.right;
		}
		return getWidth();
	}

	/**
	 * Choose between the normal layout (controls beside the fields) and the
	 * compact layout (controls on a wrapped third row) for a given width.
	 * 
	 * @return true if something changed and a new layout pass is needed
	 */
	private boolean applyLayoutMode(int availableWidth) {
		boolean changed = false;
		boolean compact = availableWidth < normalModeMinimumWidth();

		if (!layoutInitialized || compact != compactMode) {
			layoutInitialized = true;
			compactMode = compact;
			findControls.removeAll();
			replaceControls.removeAll();
			extraRow.removeAll();
			if (compact) {
				for (Component c : findControlList) {
					extraRow.add(c);
				}
				for (Component c : replaceControlList) {
					extraRow.add(c);
				}
			} else {
				for (Component c : findControlList) {
					findControls.add(c);
				}
				for (Component c : replaceControlList) {
					replaceControls.add(c);
				}
			}
			extraRow.setVisible(compact);
			changed = true;
		}

		Insets in = getInsets();
		if (extraRow.setWidthHint(Math.max(0, availableWidth - in.left - in.right)) && compact) {
			changed = true;
		}
		return changed;
	}

	/** Width needed to show the normal layout with a usable text field */
	private int normalModeMinimumWidth() {
		Insets in = getInsets();
		int label = findLabel.getPreferredSize().width;
		int findRowControls = flowWidth(findControlList) + GAP + closeButton.getPreferredSize().width;
		int replaceRowControls = flowWidth(replaceControlList);
		int controls = Math.max(findRowControls, replaceRowControls);
		return in.left + in.right + label + GAP + MIN_FIELD_WIDTH + GAP + controls;
	}

	private static int flowWidth(Component[] components) {
		int width = 2 * GAP;
		for (Component c : components) {
			width += c.getPreferredSize().width + GAP;
		}
		return width;
	}

	/**
	 * A left-aligned flow panel whose preferred height accounts for wrapping
	 * (the standard FlowLayout always reports a single-row height).
	 */
	private static final class WrapPanel extends JPanel {
		private int widthHint = 0;

		WrapPanel() {
			super(new FlowLayout(FlowLayout.LEFT, GAP, 2));
			setOpaque(false);
		}

		/** @return true if the hint changed */
		boolean setWidthHint(int width) {
			if (width == widthHint) {
				return false;
			}
			widthHint = width;
			invalidate();
			return true;
		}

		@Override
		public Dimension getPreferredSize() {
			int width = widthHint > 0 ? widthHint : getWidth();
			if (width <= 0) {
				return super.getPreferredSize();
			}
			FlowLayout flow = (FlowLayout) getLayout();
			Insets in = getInsets();
			int hgap = flow.getHgap();
			int vgap = flow.getVgap();
			int maxWidth = width - in.left - in.right - 2 * hgap;

			int rowWidth = 0;
			int rowHeight = 0;
			int totalWidth = 0;
			int totalHeight = 0;
			for (Component c : getComponents()) {
				if (!c.isVisible()) {
					continue;
				}
				Dimension d = c.getPreferredSize();
				// Same wrapping rule as FlowLayout.layoutContainer
				if (rowWidth > 0 && rowWidth + hgap + d.width > maxWidth) {
					totalWidth = Math.max(totalWidth, rowWidth);
					totalHeight += rowHeight + vgap;
					rowWidth = 0;
					rowHeight = 0;
				}
				rowWidth += (rowWidth > 0 ? hgap : 0) + d.width;
				rowHeight = Math.max(rowHeight, d.height);
			}
			totalWidth = Math.max(totalWidth, rowWidth);
			totalHeight += rowHeight;
			return new Dimension(totalWidth + in.left + in.right + 2 * hgap,
					totalHeight + in.top + in.bottom + 2 * vgap);
		}

		@Override
		public Dimension getMinimumSize() {
			return getPreferredSize();
		}
	}

	// =====================================================================
	// Listeners / key bindings
	// =====================================================================

	private void installListeners() {
		DocumentListener queryListener = new DocumentListener() {
			public void insertUpdate(DocumentEvent e) {
				scheduleRefresh();
			}

			public void removeUpdate(DocumentEvent e) {
				scheduleRefresh();
			}

			public void changedUpdate(DocumentEvent e) {
				scheduleRefresh();
			}
		};
		findField.getDocument().addDocumentListener(queryListener);
		matchCaseButton.addActionListener(e -> refresh());
		wholeWordButton.addActionListener(e -> refresh());
		regexButton.addActionListener(e -> refresh());

		// Matches become stale when the document changes
		textArea.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) {
				documentChanged();
			}

			public void removeUpdate(DocumentEvent e) {
				documentChanged();
			}

			public void changedUpdate(DocumentEvent e) {
				documentChanged();
			}

			private void documentChanged() {
				if (isVisible()) {
					scheduleRefresh();
				}
			}
		});

		findField.addActionListener(e -> findNext(true));
		replaceField.addActionListener(e -> replaceCurrent());
		nextButton.addActionListener(e -> findNext(true));
		previousButton.addActionListener(e -> findNext(false));
		replaceButton.addActionListener(e -> replaceCurrent());
		replaceAllButton.addActionListener(e -> replaceAll());
		closeButton.addActionListener(e -> hideBar());
	}

	private void installKeyBindings() {
		findField.getInputMap(JComponent.WHEN_FOCUSED)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "findPrevious");
		findField.getActionMap().put("findPrevious", action(() -> findNext(false)));

		getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
				"closeBar");
		getActionMap().put("closeBar", action(this::hideBar));
	}

	private static AbstractAction action(Runnable runnable) {
		return new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				runnable.run();
			}
		};
	}

	// =====================================================================
	// Public API
	// =====================================================================

	/** Set a callback invoked when the bar hides itself */
	public void setOnClose(Runnable onClose) {
		this.onClose = onClose;
	}

	/** Show the bar, pre-fill it with the selection and focus the find field */
	public void showBar() {
		String selected = textArea.getSelectedText();
		if (selected != null && !selected.isEmpty() && selected.indexOf('\n') < 0
				&& selected.length() <= MAX_PREFILL_LENGTH) {
			findField.setText(selected);
		}
		if (!isVisible()) {
			setVisible(true);
			revalidateParent();
		}
		refresh();
		findField.requestFocusInWindow();
		findField.selectAll();
	}

	/** Hide the bar and remove the highlights */
	public void hideBar() {
		if (isVisible()) {
			setVisible(false);
			revalidateParent();
		}
		clearHighlights();
		matches.clear();
		textArea.requestFocusInWindow();
		if (onClose != null) {
			onClose.run();
		}
	}

	/** @return true if there is something in the find field */
	public boolean hasQuery() {
		return !findField.getText().isEmpty();
	}

	/** Apply day/night colors */
	public void setNightMode(boolean night) {
		Color labelColor;
		if (night) {
			setBackground(Color.DARK_GRAY);
			fieldBackground = new Color(60, 60, 60);
			fieldForeground = Color.WHITE;
			labelColor = Color.WHITE;
			highlightColor = Color.RED;
		} else {
			setBackground(UIManager.getColor("Panel.background"));
			fieldBackground = Color.WHITE;
			fieldForeground = Color.BLACK;
			labelColor = Color.BLACK;
			highlightColor = Color.ORANGE;
		}
		for (JComponent c : new JComponent[] { findLabel, replaceLabel, statusLabel }) {
			c.setForeground(labelColor);
		}
		for (JTextField f : new JTextField[] { findField, replaceField }) {
			f.setBackground(fieldBackground);
			f.setForeground(fieldForeground);
			f.setCaretColor(fieldForeground);
		}
		refresh();
	}

	/** Remove all search highlights from the text area */
	public void clearHighlights() {
		Highlighter highlighter = textArea.getHighlighter();
		for (Object tag : highlightTags) {
			highlighter.removeHighlight(tag);
		}
		highlightTags.clear();
	}

	/**
	 * Recompile the query and re-highlight all matches.
	 */
	public void refresh() {
		refreshTimer.stop();
		clearHighlights();
		matches.clear();
		matchesTruncated = false;
		pattern = null;
		String prefix = pendingStatusPrefix;
		pendingStatusPrefix = null;

		String query = findField.getText();
		if (query.isEmpty()) {
			setQueryError(false);
			statusLabel.setText(" ");
			return;
		}
		try {
			pattern = buildPattern(query);
		} catch (PatternSyntaxException e) {
			setQueryError(true);
			statusLabel.setText("Invalid regex");
			statusLabel.setToolTipText(e.getDescription());
			return;
		}
		setQueryError(false);

		if (!isVisible()) {
			return; // keep the pattern for F3, but do not highlight
		}

		String text = documentText();
		Highlighter highlighter = textArea.getHighlighter();
		Highlighter.HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(highlightColor);
		try {
			Matcher m = pattern.matcher(text);
			while (m.find()) {
				if (m.end() == m.start()) {
					continue; // ignore empty matches (e.g. "a*")
				}
				if (matches.size() >= MAX_COUNTED_MATCHES) {
					matchesTruncated = true;
					break;
				}
				matches.add(new int[] { m.start(), m.end() });
				if (highlightTags.size() < MAX_HIGHLIGHTS) {
					try {
						highlightTags.add(highlighter.addHighlight(m.start(), m.end(), painter));
					} catch (BadLocationException ignored) {
					}
				}
			}
		} catch (StackOverflowError e) {
			setQueryError(true);
			statusLabel.setText("Too complex");
			return;
		}
		updateStatus(prefix);
	}

	/**
	 * Select the next (or previous) match starting from the current selection,
	 * wrapping around the document.
	 * 
	 * @param forward true to search forward, false to search backward
	 * @return false if there is no query to search for
	 */
	public boolean findNext(boolean forward) {
		if (refreshTimer.isRunning()) {
			refresh(); // make sure the pattern reflects what is typed
		}
		if (pattern == null) {
			return false;
		}

		String text = documentText();
		int selStart = textArea.getSelectionStart();
		int selEnd = textArea.getSelectionEnd();
		int[] found;
		boolean wrapped = false;
		try {
			Matcher m = pattern.matcher(text);
			m.useTransparentBounds(true);
			m.useAnchoringBounds(false);
			if (forward) {
				found = firstMatchFrom(m, selEnd, text.length());
				if (found == null && selEnd > 0) {
					found = firstMatchFrom(m, 0, text.length());
					wrapped = true;
				}
			} else {
				found = lastMatchBefore(m, selStart);
				if (found == null) {
					found = lastMatchBefore(m, Integer.MAX_VALUE);
					wrapped = true;
				}
			}
		} catch (StackOverflowError e) {
			statusLabel.setText("Too complex");
			return true;
		}

		if (found == null) {
			statusLabel.setText("No matches");
			Toolkit.getDefaultToolkit().beep();
			return true;
		}
		selectRange(found[0], found[1]);
		if (isVisible()) {
			updateStatus(wrapped ? "Wrapped." : null);
		}
		return true;
	}

	// =====================================================================
	// Replace
	// =====================================================================

	private void replaceCurrent() {
		if (refreshTimer.isRunning()) {
			refresh();
		}
		if (pattern == null) {
			return;
		}
		String selected = textArea.getSelectedText();
		if (selected != null && !selected.isEmpty()) {
			Matcher m = pattern.matcher(selected);
			if (m.matches()) {
				String replacement;
				try {
					StringBuffer sb = new StringBuffer();
					m.appendReplacement(sb, replacementTemplate());
					m.appendTail(sb);
					replacement = sb.toString();
				} catch (IllegalArgumentException | IndexOutOfBoundsException e) {
					statusLabel.setText("Invalid replacement");
					return;
				}
				undoTool.beginCompoundEdit();
				try {
					textArea.replaceSelection(replacement);
				} finally {
					undoTool.endCompoundEdit();
				}
			}
		}
		findNext(true);
	}

	private void replaceAll() {
		if (refreshTimer.isRunning()) {
			refresh();
		}
		if (pattern == null) {
			return;
		}
		String text = documentText();
		String template = replacementTemplate();

		Matcher m = pattern.matcher(text);
		StringBuffer sb = new StringBuffer();
		int count = 0;
		int firstStart = -1;
		int lastEnd = -1;
		try {
			while (m.find()) {
				if (m.end() == m.start()) {
					continue;
				}
				if (firstStart < 0) {
					firstStart = m.start();
				}
				lastEnd = m.end();
				m.appendReplacement(sb, template);
				count++;
			}
			m.appendTail(sb);
		} catch (IllegalArgumentException | IndexOutOfBoundsException e) {
			statusLabel.setText("Invalid replacement");
			return;
		} catch (StackOverflowError e) {
			statusLabel.setText("Too complex");
			return;
		}
		if (count == 0) {
			statusLabel.setText("No matches");
			return;
		}

		// Only replace the region of the document that actually changed
		int tailLength = text.length() - lastEnd;
		String newRegion = sb.substring(firstStart, sb.length() - tailLength);

		Document doc = textArea.getDocument();
		undoTool.beginCompoundEdit();
		try {
			if (doc instanceof AbstractDocument) {
				((AbstractDocument) doc).replace(firstStart, lastEnd - firstStart, newRegion, null);
			} else {
				doc.remove(firstStart, lastEnd - firstStart);
				doc.insertString(firstStart, newRegion, null);
			}
		} catch (BadLocationException e) {
			statusLabel.setText("Replace failed");
			return;
		} finally {
			undoTool.endCompoundEdit();
		}

		pendingStatusPrefix = "Replaced " + count + ".";
		refresh();
	}

	private String replacementTemplate() {
		String replacement = replaceField.getText();
		return regexButton.isSelected() ? replacement : Matcher.quoteReplacement(replacement);
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	private Pattern buildPattern(String query) throws PatternSyntaxException {
		int flags = Pattern.MULTILINE;
		if (!matchCaseButton.isSelected()) {
			flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		}
		String expression = regexButton.isSelected() ? query : Pattern.quote(query);
		if (wholeWordButton.isSelected()) {
			expression = "\\b(?:" + expression + ")\\b";
		}
		return Pattern.compile(expression, flags);
	}

	private static int[] firstMatchFrom(Matcher m, int from, int length) {
		if (from > length) {
			from = length;
		}
		m.region(from, length);
		while (m.find()) {
			if (m.end() > m.start()) {
				return new int[] { m.start(), m.end() };
			}
		}
		return null;
	}

	private static int[] lastMatchBefore(Matcher m, int limit) {
		m.reset();
		int[] last = null;
		while (m.find()) {
			if (m.start() >= limit) {
				break;
			}
			if (m.end() > m.start()) {
				last = new int[] { m.start(), m.end() };
			}
		}
		return last;
	}

	private void selectRange(int start, int end) {
		textArea.setCaretPosition(start);
		textArea.moveCaretPosition(end);
		// The selection is normally hidden while the text area does not have the focus
		textArea.getCaret().setSelectionVisible(true);
		try {
			Rectangle2D r = textArea.modelToView2D(start);
			if (r != null) {
				textArea.scrollRectToVisible(r.getBounds());
			}
		} catch (BadLocationException ignored) {
		}
	}

	private void updateStatus(String prefix) {
		String total = matchesTruncated ? MAX_COUNTED_MATCHES + "+" : String.valueOf(matches.size());
		String status;
		if (matches.isEmpty() && !matchesTruncated) {
			status = "No matches";
		} else {
			int index = currentMatchIndex();
			if (index >= 0) {
				status = (index + 1) + " of " + total;
			} else {
				status = total + ((matches.size() == 1 && !matchesTruncated) ? " match" : " matches");
			}
		}
		statusLabel.setText(prefix == null ? status : prefix + " " + status);
		statusLabel.setToolTipText(statusLabel.getText());
	}

	/** @return index of the match equal to the current selection, or -1 */
	private int currentMatchIndex() {
		int start = textArea.getSelectionStart();
		int end = textArea.getSelectionEnd();
		if (start == end) {
			return -1;
		}
		int lo = 0;
		int hi = matches.size() - 1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			int[] m = matches.get(mid);
			if (m[0] < start) {
				lo = mid + 1;
			} else if (m[0] > start) {
				hi = mid - 1;
			} else {
				return m[1] == end ? mid : -1;
			}
		}
		return -1;
	}

	private void setQueryError(boolean error) {
		findField.setBackground(error ? ERROR_BACKGROUND : fieldBackground);
		findField.setForeground(error ? Color.BLACK : fieldForeground);
		if (!error) {
			statusLabel.setToolTipText(null);
		}
	}

	private void scheduleRefresh() {
		refreshTimer.restart();
	}

	private String documentText() {
		Document doc = textArea.getDocument();
		try {
			return doc.getText(0, doc.getLength());
		} catch (BadLocationException e) {
			return "";
		}
	}

	private void revalidateParent() {
		Container parent = getParent();
		if (parent != null) {
			parent.revalidate();
			parent.repaint();
		}
	}
}