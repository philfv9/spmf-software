package ca.pfv.spmf.gui;

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

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseListener;
import java.net.URL;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentListener;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.parameterselectionpanel.ParameterSelectionPanel;
import ca.pfv.spmf.gui.preferences.PreferencesManager;

/**
 * Reusable panel containing the complete algorithm-runner UI.
 * <p>
 * Every row is a {@link RowSlot}: a fixed-height card that can show its content
 * or a blank spacer, so showing/hiding rows never shifts the layout. Rows share
 * the same 5-column structure (label | field | Select | Recent | View/Guide)
 * with identical button sizes so columns line up across rows.
 * <p>
 * Keyboard: F5 or Ctrl+R runs the selected algorithm.
 *
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class AlgorithmRunnerPanel extends JPanel {

	// --- Layout constants -------------------------------------------------
	private static final int INSET_V = 4;
	private static final int INSET_H = 4;
	private static final int INSET_SECTION_TOP = 8;
	private static final Color LABEL_COLOR = new Color(60, 60, 60);

	private static final String CARD_VISIBLE = "visible";
	private static final String CARD_HIDDEN = "hidden";

	private static final int TAB_CONSOLE = 0;
	private static final int TAB_HISTORY = 1;
	private static final int TAB_OUTPUT = 2;

	private static final String RUN_TEXT = "\u25B6  Run algorithm";
	private static final String STOP_TEXT = "\u25A0  Stop algorithm";

	private static final int DEFAULT_MAX_SECONDS = 60;

	// --- Configuration ----------------------------------------------------
	private final boolean showViewAndTransform;
	private final boolean showGenerateData;
	private final boolean showTools;
	private final boolean showAlgorithms;
	private final boolean showExperimentTools;
	private final boolean showSpmfLogo;

	private final Font labelFont;

	// =====================================================================
	// RowSlot
	// =====================================================================

	/**
	 * A panel that always occupies the preferred height of its content, whether
	 * the content is shown or replaced by a transparent spacer.
	 */
	private static final class RowSlot extends JPanel {

		private final CardLayout cardLayout = new CardLayout();
		private final JPanel contentPanel;
		private boolean contentShown = true;

		RowSlot(JPanel content) {
			setLayout(cardLayout);
			setOpaque(false);
			this.contentPanel = content;
			JPanel spacer = new JPanel();
			spacer.setOpaque(false);
			add(content, CARD_VISIBLE);
			add(spacer, CARD_HIDDEN);
		}

		void setContentVisible(boolean visible) {
			cardLayout.show(this, visible ? CARD_VISIBLE : CARD_HIDDEN);
			contentShown = visible;
		}

		boolean isContentVisible() {
			return contentShown;
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(super.getPreferredSize().width, contentPanel.getPreferredSize().height);
		}

		@Override
		public Dimension getMinimumSize() {
			return getPreferredSize();
		}
	}

	// =====================================================================
	// Widgets
	// =====================================================================

	private JLabel labelSPMF;
	private JTextField textFieldAlgorithm;
	private JTextField textFieldInput, textFieldOutput;
	private JSpinner spinnerMaxSeconds;
	private JButton buttonSelectAlgorithm, buttonRecentAlgorithm, buttonExample;
	private JButton buttonInput, buttonRecentInput, buttonViewInput;
	private JButton buttonOutput, buttonRecentOutput;
	private JButton buttonRun;
	private JCheckBox chckbxRunAsExternal;
	private JCheckBox chckbxBeepOnCompletion;
	private JCheckBox chckbxMaxSeconds;
	private JPanel optionsTimeLimitWrapper;
	private AlgorithmProgressBar progressBar;
	private ParameterSelectionPanel parameterPanel;

	private JTabbedPane bottomTabbedPane;
	private ConsolePanel consolePanel;
	private RunHistoryPanel runHistoryPanel;
	private OutputPanel outputPanel;

	private RowSlot rowSlotInput, rowSlotParameters, rowSlotOutput, rowSlotOptions, rowSlotRun, rowSlotProgress;

	/** True between {@link #setUIToRunningState()} and {@link #resetUIAfterThreadCompletion()}. */
	private boolean running;

	// =====================================================================
	// Construction
	// =====================================================================

	/**
	 * Builds the panel, showing the SPMF logo.
	 *
	 * @throws Exception if AlgorithmManager cannot be initialised
	 */
	public AlgorithmRunnerPanel(boolean showViewAndTransform, boolean showGenerateData, boolean showTools,
			boolean showAlgorithms, boolean showExperimentTools) throws Exception {
		this(showViewAndTransform, showGenerateData, showTools, showAlgorithms, showExperimentTools, true);
	}

	/**
	 * Builds the panel.
	 *
	 * @param showViewAndTransform include view-and-transform algorithms
	 * @param showGenerateData     include data-generation algorithms
	 * @param showTools            include tool algorithms
	 * @param showAlgorithms       include data-mining algorithms
	 * @param showExperimentTools  include experiment-tool algorithms
	 * @param showSpmfLogo         whether to display the SPMF logo
	 * @throws Exception if AlgorithmManager cannot be initialised
	 */
	public AlgorithmRunnerPanel(boolean showViewAndTransform, boolean showGenerateData, boolean showTools,
			boolean showAlgorithms, boolean showExperimentTools, boolean showSpmfLogo) throws Exception {

		this.showViewAndTransform = showViewAndTransform;
		this.showGenerateData = showGenerateData;
		this.showTools = showTools;
		this.showAlgorithms = showAlgorithms;
		this.showExperimentTools = showExperimentTools;
		this.showSpmfLogo = showSpmfLogo;

		Font base = UIManager.getFont("Label.font");
		if (base == null) {
			base = new Font(Font.DIALOG, Font.PLAIN, 12);
		}
		labelFont = base.deriveFont(Font.BOLD);

		setBorder(new EmptyBorder(12, 12, 12, 12));
		setLayout(new GridBagLayout());

		// --- Shared column sizes ----------------------------------------
		JButton protoSelect = new JButton("Select");
		JButton protoRecent = new JButton("Recent \u25BC");
		JButton protoView = new JButton("View");
		JButton protoDoc = new JButton("Guide");
		int btnH = Math.max(Math.max(protoSelect.getPreferredSize().height, protoRecent.getPreferredSize().height),
				Math.max(protoView.getPreferredSize().height, protoDoc.getPreferredSize().height));
		Dimension selectDim = new Dimension(protoSelect.getPreferredSize().width + 6, btnH);
		Dimension recentDim = new Dimension(protoRecent.getPreferredSize().width + 6, btnH);
		Dimension viewDocDim = new Dimension(
				Math.max(protoView.getPreferredSize().width, protoDoc.getPreferredSize().width) + 14, btnH);

		int labelW = 0, labelH = 0;
		for (String s : new String[] { "Algorithm:", "Input file:", "Output file:", "Parameters:", "Options:" }) {
			Dimension d = makeSectionLabel(s).getPreferredSize();
			labelW = Math.max(labelW, d.width);
			labelH = Math.max(labelH, d.height);
		}
		Dimension labelDim = new Dimension(labelW + 8, labelH);

		// --- Row 0: logo ------------------------------------------------
		if (showSpmfLogo) {
			URL logoUrl = AlgorithmRunnerPanel.class.getResource("spmf.png");
			if (logoUrl != null) {
				labelSPMF = new JLabel(new ImageIcon(logoUrl));
				labelSPMF.setToolTipText("Click to see information about SPMF");
				GridBagConstraints gbc = defaultGbc();
				gbc.gridx = 0;
				gbc.gridy = 0;
				gbc.gridwidth = 5;
				gbc.fill = GridBagConstraints.NONE;
				gbc.anchor = GridBagConstraints.NORTHWEST;
				add(labelSPMF, gbc);
			}
		}

		// --- Row 1: algorithm -------------------------------------------
		textFieldAlgorithm = new JTextField();
		textFieldAlgorithm.setEditable(false);
		textFieldAlgorithm.setToolTipText("Currently selected algorithm \u2014 click Select to change");

		buttonSelectAlgorithm = makeButton("Select", "Open the searchable algorithm browser", selectDim);
		buttonRecentAlgorithm = makeButton("Recent \u25BC", "Show recently used algorithms", recentDim);
		buttonExample = makeButton("Guide", "Open the documentation page of this algorithm", viewDocDim);
		buttonExample.setEnabled(false);

		placeRowSlot(new RowSlot(buildStandardRow(makeSectionLabel("Algorithm:"), textFieldAlgorithm,
				buttonSelectAlgorithm, buttonRecentAlgorithm, buttonExample, labelDim, selectDim, recentDim,
				viewDocDim, INSET_SECTION_TOP)), 1);

		// --- Row 2: input file ------------------------------------------
		textFieldInput = new JTextField();
		textFieldInput.setToolTipText("Type or paste the input file path, or use Select");

		buttonInput = makeButton("Select", "Select an input file", selectDim);
		buttonRecentInput = makeButton("Recent \u25BC", "Show recently used input files", recentDim);
		buttonViewInput = makeButton("View", "Open the input file in a viewer", viewDocDim);
		buttonViewInput.setEnabled(false);

		rowSlotInput = new RowSlot(buildStandardRow(makeSectionLabel("Input file:"), textFieldInput, buttonInput,
				buttonRecentInput, buttonViewInput, labelDim, selectDim, recentDim, viewDocDim, INSET_V));
		placeRowSlot(rowSlotInput, 2);

		// --- Row 3: parameters ------------------------------------------
		parameterPanel = new ParameterSelectionPanel(null);
		parameterPanel.update((DescriptionOfAlgorithm) null);
		final int lockedH = parameterPanel.getPreferredSize().height;

		JPanel paramWrapper = new JPanel(new BorderLayout()) {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(super.getPreferredSize().width, lockedH);
			}

			@Override
			public Dimension getMinimumSize() {
				return getPreferredSize();
			}

			@Override
			public Dimension getMaximumSize() {
				return new Dimension(Integer.MAX_VALUE, lockedH);
			}
		};
		paramWrapper.setOpaque(false);
		paramWrapper.add(parameterPanel, BorderLayout.NORTH);

		rowSlotParameters = new RowSlot(buildParameterRow(makeSectionLabel("Parameters:"), paramWrapper, labelDim));
		placeRowSlot(rowSlotParameters, 3);

		// --- Row 4: output file -----------------------------------------
		textFieldOutput = new JTextField();
		textFieldOutput.setToolTipText("Type or paste the output file path, or use Select");

		buttonOutput = makeButton("Select", "Select an output file location", selectDim);
		buttonRecentOutput = makeButton("Recent \u25BC", "Show recently used output files", recentDim);

		rowSlotOutput = new RowSlot(buildStandardRow(makeSectionLabel("Output file:"), textFieldOutput,
				buttonOutput, buttonRecentOutput, fixedSizePanel(viewDocDim), labelDim, selectDim, recentDim,
				viewDocDim, INSET_V));
		placeRowSlot(rowSlotOutput, 4);

		// --- Row 5: options ---------------------------------------------
		chckbxRunAsExternal = new JCheckBox("Run in a separate process");
		chckbxRunAsExternal.setToolTipText(
				"Run the algorithm in a new JVM (requires spmf.jar). Allows stopping it at any time.");
		chckbxRunAsExternal.setSelected(PreferencesManager.getInstance().getRunAsExternalProgram());

		chckbxBeepOnCompletion = new JCheckBox("Beep on completion");
		chckbxBeepOnCompletion.setToolTipText("Play a short beep when the algorithm finishes");
		chckbxBeepOnCompletion.setSelected(PreferencesManager.getInstance().getBeepOnCompletion());
		chckbxBeepOnCompletion.addActionListener(
				e -> PreferencesManager.getInstance().setBeepOnCompletion(chckbxBeepOnCompletion.isSelected()));

		chckbxMaxSeconds = new JCheckBox("Time limit (s):");
		chckbxMaxSeconds.setToolTipText("Stop the algorithm after the given number of seconds");

		spinnerMaxSeconds = new JSpinner(new SpinnerNumberModel(DEFAULT_MAX_SECONDS, 1, 7 * 24 * 3600, 10));
		spinnerMaxSeconds.setEditor(new JSpinner.NumberEditor(spinnerMaxSeconds, "#"));
		((JSpinner.DefaultEditor) spinnerMaxSeconds.getEditor()).getTextField().setColumns(5);
		spinnerMaxSeconds.setToolTipText("Maximum execution time in seconds");
		spinnerMaxSeconds.setEnabled(false);
		chckbxMaxSeconds.addActionListener(e -> setMaxSecondsFieldEnabled(chckbxMaxSeconds.isSelected()));

		optionsTimeLimitWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		optionsTimeLimitWrapper.setOpaque(false);
		optionsTimeLimitWrapper.add(chckbxMaxSeconds);
		optionsTimeLimitWrapper.add(spinnerMaxSeconds);
		// Lock the wrapper to its fully-visible size so hiding its widgets keeps the row height
		Dimension wrapperPref = optionsTimeLimitWrapper.getPreferredSize();
		chckbxMaxSeconds.setVisible(false);
		spinnerMaxSeconds.setVisible(false);
		optionsTimeLimitWrapper.setPreferredSize(wrapperPref);
		optionsTimeLimitWrapper.setMinimumSize(wrapperPref);
		optionsTimeLimitWrapper.setMaximumSize(wrapperPref);

		rowSlotOptions = new RowSlot(buildOptionsRow(makeSectionLabel("Options:"), chckbxRunAsExternal,
				chckbxBeepOnCompletion, optionsTimeLimitWrapper, labelDim));
		placeRowSlot(rowSlotOptions, 5);

		// --- Row 6: run button ------------------------------------------
		buttonRun = new JButton(RUN_TEXT);
		buttonRun.setFont(buttonRun.getFont().deriveFont(Font.BOLD));
		buttonRun.setEnabled(false);
		buttonRun.setToolTipText("Run the selected algorithm with the given parameters (F5)");
		Dimension runPref = buttonRun.getPreferredSize();
		buttonRun.setPreferredSize(new Dimension(runPref.width + 40, runPref.height + 8));

		JPanel runContent = new JPanel(new FlowLayout(FlowLayout.CENTER, INSET_H, INSET_V));
		runContent.setOpaque(false);
		runContent.add(buttonRun);
		rowSlotRun = new RowSlot(runContent);
		placeRowSlot(rowSlotRun, 6);

		// --- Row 7: progress bar ----------------------------------------
		progressBar = new AlgorithmProgressBar();
		progressBar.setToolTipText("Elapsed time of the current run");

		JPanel progressContent = new JPanel(new BorderLayout());
		progressContent.setOpaque(false);
		progressContent.setBorder(new EmptyBorder(INSET_V, INSET_H, INSET_V, INSET_H));
		progressContent.add(progressBar, BorderLayout.CENTER);
		rowSlotProgress = new RowSlot(progressContent);
		rowSlotProgress.setContentVisible(false);
		placeRowSlot(rowSlotProgress, 7);

		// --- Row 8: glue ------------------------------------------------
		GridBagConstraints gbc = defaultGbc();
		gbc.gridx = 0;
		gbc.gridy = 8;
		gbc.gridwidth = 5;
		gbc.fill = GridBagConstraints.VERTICAL;
		gbc.weighty = 0.001;
		gbc.insets = new Insets(0, 0, 0, 0);
		add(Box.createVerticalGlue(), gbc);

		// --- Row 9: tabs ------------------------------------------------
		consolePanel = new ConsolePanel(false);
		consolePanel.setBorder(new EmptyBorder(2, 2, 2, 2));
		consolePanel.setPreferredSize(new Dimension(620, 190));
		runHistoryPanel = new RunHistoryPanel();
		runHistoryPanel.setPreferredSize(new Dimension(620, 190));
		outputPanel = new OutputPanel();
		outputPanel.setPreferredSize(new Dimension(620, 190));

		bottomTabbedPane = new JTabbedPane(JTabbedPane.TOP);
		bottomTabbedPane.addTab("Console", consolePanel);
		bottomTabbedPane.addTab("Run history", runHistoryPanel);
		bottomTabbedPane.addTab("Output", outputPanel);
		bottomTabbedPane.setToolTipTextAt(TAB_CONSOLE, "Messages produced by algorithms");
		bottomTabbedPane.setToolTipTextAt(TAB_HISTORY, "Past runs \u2014 double-click a row to re-run");
		bottomTabbedPane.setToolTipTextAt(TAB_OUTPUT, "Preview of the most recent output file");

		gbc = defaultGbc();
		gbc.gridx = 0;
		gbc.gridy = 9;
		gbc.gridwidth = 5;
		gbc.gridheight = GridBagConstraints.REMAINDER;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 1;
		gbc.weighty = 1;
		add(bottomTabbedPane, gbc);

		installKeyBindings();
	}

	// =====================================================================
	// Builders
	// =====================================================================

	private JPanel buildStandardRow(JLabel label, JComponent field, Component btn2, Component btn3,
			Component btn4, Dimension labelDim, Dimension col2Dim, Dimension col3Dim, Dimension col4Dim,
			int topInset) {
		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		GridBagConstraints c = defaultGbc();
		c.insets = new Insets(topInset, INSET_H, INSET_V, INSET_H);

		c.gridx = 0;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.WEST;
		label.setPreferredSize(labelDim);
		row.add(label, c);

		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		row.add(field, c);

		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.CENTER;
		c.gridx = 2;
		btn2.setPreferredSize(col2Dim);
		row.add(btn2, c);
		c.gridx = 3;
		btn3.setPreferredSize(col3Dim);
		row.add(btn3, c);
		c.gridx = 4;
		btn4.setPreferredSize(col4Dim);
		row.add(btn4, c);
		return row;
	}

	private JPanel buildParameterRow(JLabel label, JPanel paramWrapper, Dimension labelDim) {
		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		GridBagConstraints c = defaultGbc();

		c.gridx = 0;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.NORTHWEST;
		label.setPreferredSize(labelDim);
		row.add(label, c);

		c.gridx = 1;
		c.gridwidth = 4;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		row.add(paramWrapper, c);
		return row;
	}

	/**
	 * Options row: label | checkboxes stacked vertically | time-limit wrapper.
	 * Stacking keeps column 1 narrow so the time-limit widgets always fit.
	 */
	private JPanel buildOptionsRow(JLabel label, JCheckBox cbExternal, JCheckBox cbBeep, JPanel timeLimitWrapper,
			Dimension labelDim) {
		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		GridBagConstraints c = defaultGbc();
		c.anchor = GridBagConstraints.NORTHWEST;

		c.gridx = 0;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		label.setPreferredSize(labelDim);
		row.add(label, c);

		JPanel checkboxes = new JPanel();
		checkboxes.setOpaque(false);
		checkboxes.setLayout(new BoxLayout(checkboxes, BoxLayout.Y_AXIS));
		cbExternal.setAlignmentX(Component.LEFT_ALIGNMENT);
		cbBeep.setAlignmentX(Component.LEFT_ALIGNMENT);
		checkboxes.add(cbExternal);
		checkboxes.add(cbBeep);

		c.gridx = 1;
		row.add(checkboxes, c);

		c.gridx = 2;
		c.gridwidth = 3;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		row.add(timeLimitWrapper, c);
		return row;
	}

	private void placeRowSlot(RowSlot slot, int gridRow) {
		GridBagConstraints c = defaultGbc();
		c.gridx = 0;
		c.gridy = gridRow;
		c.gridwidth = 5;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.NORTHWEST;
		c.insets = new Insets(0, 0, 0, 0);
		add(slot, c);
	}

	private void installKeyBindings() {
		InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "spmf.run");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK), "spmf.run");
		getActionMap().put("spmf.run", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!running && buttonRun.isEnabled() && rowSlotRun.isContentVisible()) {
					buttonRun.doClick();
				}
			}
		});
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	private static GridBagConstraints defaultGbc() {
		GridBagConstraints c = new GridBagConstraints();
		c.gridheight = 1;
		c.gridwidth = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.WEST;
		c.weightx = 0;
		c.weighty = 0;
		c.insets = new Insets(INSET_V, INSET_H, INSET_V, INSET_H);
		return c;
	}

	private static JPanel fixedSizePanel(Dimension size) {
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setPreferredSize(size);
		p.setMinimumSize(size);
		p.setMaximumSize(size);
		return p;
	}

	private static JButton makeButton(String text, String tooltip, Dimension size) {
		JButton b = new JButton(text);
		b.setToolTipText(tooltip);
		b.setPreferredSize(size);
		return b;
	}

	private JLabel makeSectionLabel(String text) {
		JLabel label = new JLabel(text);
		label.setFont(labelFont);
		label.setForeground(LABEL_COLOR);
		return label;
	}

	// =====================================================================
	// Elapsed time
	// =====================================================================

	/** Starts the elapsed-time display on the progress bar. */
	public void startElapsedTimer() {
		progressBar.startAlgorithm();
	}

	// =====================================================================
	// Listener registration
	// =====================================================================

	public void addRunButtonListener(ActionListener l)             { buttonRun.addActionListener(l); }
	public void addExampleButtonListener(ActionListener l)         { buttonExample.addActionListener(l); }
	public void addRecentAlgorithmButtonListener(ActionListener l) { buttonRecentAlgorithm.addActionListener(l); }
	public void addSelectAlgorithmButtonListener(ActionListener l) { buttonSelectAlgorithm.addActionListener(l); }
	public void addInputButtonListener(ActionListener l)           { buttonInput.addActionListener(l); }
	public void addOutputButtonListener(ActionListener l)          { buttonOutput.addActionListener(l); }
	public void addRecentInputButtonListener(ActionListener l)     { buttonRecentInput.addActionListener(l); }
	public void addRecentOutputButtonListener(ActionListener l)    { buttonRecentOutput.addActionListener(l); }
	public void addViewInputButtonListener(ActionListener l)       { buttonViewInput.addActionListener(l); }
	public void addRunAsExternalListener(ActionListener l)         { chckbxRunAsExternal.addActionListener(l); }
	public void addMaxSecondsCheckboxListener(ActionListener l)    { chckbxMaxSeconds.addActionListener(l); }
	public void addInputFileTextListener(DocumentListener l)       { textFieldInput.getDocument().addDocumentListener(l); }
	public void addOutputFileTextListener(DocumentListener l)      { textFieldOutput.getDocument().addDocumentListener(l); }
	public void addRerunHistoryListener(ActionListener l)          { runHistoryPanel.addRerunListener(l); }
	public void addClearHistoryListener(ActionListener l)          { runHistoryPanel.addClearHistoryListener(l); }
	public void addOutputTabViewButtonListener(ActionListener l)   { outputPanel.addOpenButtonListener(l); }

	/** Registers a listener on the logo (no-op if the logo is not displayed). */
	public void addLogoMouseListener(MouseListener l) {
		if (labelSPMF != null) {
			labelSPMF.addMouseListener(l);
		}
	}

	// =====================================================================
	// State readers
	// =====================================================================

	public String getSelectedAlgorithmName()          { return textFieldAlgorithm.getText(); }
	public String[] getParameterValues()              { return parameterPanel.getParameterValues(); }
	public int getParameterCount()                    { return parameterPanel.getParameterCount(); }
	public List<String> getMissingRequiredParameterNames() { return parameterPanel.getMissingRequiredParameterNames(); }
	public String getOutputTabSelectedViewerMethod()  { return outputPanel.getSelectedViewerMethod(); }
	public boolean isRunAsExternal()                  { return chckbxRunAsExternal.isSelected(); }
	public boolean isBeepOnCompletionSelected()       { return chckbxBeepOnCompletion.isSelected(); }
	public boolean isTimeLimitEnabled()               { return chckbxMaxSeconds.isSelected(); }
	public String getMaxSecondsText()                 { return String.valueOf(spinnerMaxSeconds.getValue()); }
	public boolean isOutputFileSectionVisible()       { return rowSlotOutput.isContentVisible(); }
	public String getInputFileText()                  { return textFieldInput.getText(); }
	public String getOutputFileText()                 { return textFieldOutput.getText(); }
	public RunHistoryEntry getSelectedHistoryEntry()  { return runHistoryPanel.getSelectedEntry(); }
	public JButton getRecentAlgorithmButton()         { return buttonRecentAlgorithm; }
	public JButton getRecentInputButton()             { return buttonRecentInput; }
	public JButton getRecentOutputButton()            { return buttonRecentOutput; }
	public boolean getShowViewAndTransform()          { return showViewAndTransform; }
	public boolean getShowGenerateData()              { return showGenerateData; }
	public boolean getShowTools()                     { return showTools; }
	public boolean getShowAlgorithms()                { return showAlgorithms; }
	public boolean getShowExperimentTools()           { return showExperimentTools; }
	public boolean getShowSpmfLogo()                  { return showSpmfLogo; }

	// =====================================================================
	// State writers
	// =====================================================================

	public void setInputFileName(String name)            { textFieldInput.setText(name); }
	public void setOutputFileName(String name)           { textFieldOutput.setText(name); }
	public void setViewInputButtonEnabled(boolean e)     { buttonViewInput.setEnabled(e); }
	public void setRunAsExternalSelected(boolean s)      { chckbxRunAsExternal.setSelected(s); }
	public void setParameterValues(String[] values)      { parameterPanel.setParameterValues(values); }
	public void addHistoryEntry(RunHistoryEntry entry)   { runHistoryPanel.addEntry(entry); }
	public void clearHistory()                           { runHistoryPanel.clearHistory(); }
	public void showConsoleTab()                         { bottomTabbedPane.setSelectedIndex(TAB_CONSOLE); }
	public void showHistoryTab()                         { bottomTabbedPane.setSelectedIndex(TAB_HISTORY); }
	public void showOutputTab()                          { bottomTabbedPane.setSelectedIndex(TAB_OUTPUT); }
	public void clearOutputTab()                         { outputPanel.updatePreview(null); }
	public void flashOutputPanel()                       { outputPanel.flashCompletion(); }

	public void setSelectedAlgorithm(String algorithmName) {
		textFieldAlgorithm.setText(algorithmName == null ? "" : algorithmName);
		textFieldAlgorithm.setCaretPosition(0);
	}

	/** Shows or hides the time-limit widgets without changing the row height. */
	public void setTimeLimitWidgetsVisible(boolean visible) {
		chckbxMaxSeconds.setVisible(visible);
		spinnerMaxSeconds.setVisible(visible);
		optionsTimeLimitWrapper.revalidate();
		optionsTimeLimitWrapper.repaint();
	}

	public void setMaxSecondsFieldEnabled(boolean enabled) {
		spinnerMaxSeconds.setEnabled(enabled);
	}

	/**
	 * Shows a preview of the given output file in the Output tab (null clears it).
	 */
	public void updateOutputTab(String outputFilePath) {
		outputPanel.updatePreview(outputFilePath);
		if (outputFilePath == null || outputFilePath.trim().isEmpty()) {
			return;
		}
		try {
			DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance()
					.getDescriptionOfAlgorithm(getSelectedAlgorithmName());
			if (algorithm != null && algorithm.getOutputFileTypes() != null) {
				outputPanel.setViewerOptions(AlgorithmViewerOptions.buildViewerOptionsForAlgorithm(algorithm));
			}
		} catch (Exception e) {
			outputPanel.setViewerOptions(new String[] { "System text editor", "SPMF text editor", "Pattern viewer" });
		}
		showOutputTab();
	}

	// =====================================================================
	// Algorithm-driven updates
	// =====================================================================

	/**
	 * Updates every section to reflect the selected algorithm (null hides all
	 * optional sections).
	 *
	 * @throws Exception if AlgorithmManager lookup fails
	 */
	public void updateUserInterfaceForAlgorithm(DescriptionOfAlgorithm algorithm) throws Exception {
		boolean selected = algorithm != null;

		buttonRun.setEnabled(selected);
		rowSlotRun.setContentVisible(selected);
		buttonExample.setEnabled(selected);

		// Always rebuild, so stale parameters of a previous algorithm are cleared
		parameterPanel.update(algorithm);
		DescriptionOfParameter[] params = selected ? algorithm.getParametersDescription() : null;
		rowSlotParameters.setContentVisible(params != null && params.length > 0);
		rowSlotParameters.revalidate();
		rowSlotParameters.repaint();

		rowSlotInput.setContentVisible(selected && algorithm.getInputFileTypes() != null);
		rowSlotOutput.setContentVisible(selected && algorithm.getOutputFileTypes() != null);

		updateButtonViewInput(algorithm);

		boolean options = selected && AlgorithmType.DATA_MINING.equals(algorithm.getAlgorithmType());
		rowSlotOptions.setContentVisible(options);
		if (options) {
			setTimeLimitWidgetsVisible(chckbxRunAsExternal.isSelected());
		}
	}

	/** Hides all optional components; call once before any algorithm is selected. */
	public void applyInitialVisibility() throws Exception {
		updateUserInterfaceForAlgorithm(null);
	}

	private void updateButtonViewInput(DescriptionOfAlgorithm algorithm) throws Exception {
		if (algorithm == null || algorithm.getInputFileTypes() == null
				|| algorithm.getAlgorithmType() == AlgorithmType.DATA_VIEWER) {
			buttonViewInput.setEnabled(false);
			return;
		}
		buttonViewInput.setEnabled(
				AlgorithmManager.getInstance().getViewerFor(algorithm.getInputFileTypes()) != null);
	}

	// =====================================================================
	// Run-state transitions
	// =====================================================================

	/** Switches to the running state: progress row shown, Run becomes Stop. */
	public void setUIToRunningState() {
		running = true;
		rowSlotProgress.setContentVisible(true);
		progressBar.setIndeterminate(true);
		progressBar.setString("Running\u2026");

		buttonRun.setText(STOP_TEXT);
		buttonRun.setToolTipText("Stop the running algorithm");
		setControlsEnabled(false);
		bottomTabbedPane.setSelectedIndex(TAB_CONSOLE);
	}

	/** Returns to the idle state after a run finishes or is stopped. */
	public void resetUIAfterThreadCompletion() {
		running = false;
		progressBar.endAlgorithm();
		rowSlotProgress.setContentVisible(false);

		buttonRun.setText(RUN_TEXT);
		buttonRun.setToolTipText("Run the selected algorithm with the given parameters (F5)");
		setControlsEnabled(true);
	}

	private void setControlsEnabled(boolean enabled) {
		buttonSelectAlgorithm.setEnabled(enabled);
		buttonRecentAlgorithm.setEnabled(enabled);
		chckbxRunAsExternal.setEnabled(enabled);
		chckbxBeepOnCompletion.setEnabled(enabled);
		chckbxMaxSeconds.setEnabled(enabled);
		spinnerMaxSeconds.setEnabled(enabled && chckbxMaxSeconds.isSelected());
	}

	// =====================================================================
	// Console delegation
	// =====================================================================

	public void postStatusMessage(String msg) { consolePanel.postStatusMessage(msg); }
	public void appendConsoleLine(String line) { consolePanel.appendLine(line); }
	public void clearConsole()                 { consolePanel.clearConsole(); }
	public void redirectOutputStream()         { consolePanel.redirectOutputStream(); }
	public void restoreOutputStream()          { consolePanel.restoreOutputStream(); }
}