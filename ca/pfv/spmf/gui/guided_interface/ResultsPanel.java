package ca.pfv.spmf.gui.guided_interface;

/*
 * Copyright (c) 2008-2022 Philippe Fournier-Viger
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
 * Do not remove copyright and license.
 */


import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.gui.AlgorithmRunnerPanel;
import ca.pfv.spmf.gui.OutputPanel;

/**
 * Tab 3 of {@link GuidedInterface}: shows a preview of the produced output file
 * and lets the user open it with an appropriate viewer, reusing
 * {@link OutputPanel} from the regular SPMF interface.
 *
 * <p>
 * A "Run summary" header is shown above the output preview so that, unlike
 * before, this tab is self-explanatory on its own: it states which
 * algorithm produced the file, from which input file, and when — without
 * requiring the user to switch to the "Run history" tab (in tab 2) to find
 * out.
 * </p>
 *
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class ResultsPanel extends JPanel {

	private static final Font LABEL_FONT_BOLD = new Font(Font.DIALOG, Font.BOLD, 12);
	private static final DateTimeFormatter TIMESTAMP_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static final String VIEWER_SYSTEM_TEXT_EDITOR = "System text editor";
	private static final String VIEWER_SPMF_TEXT_EDITOR = "SPMF text editor";
	private static final String VIEWER_PATTERN_VIEWER = "Pattern viewer";
	private static final String VIEWER_TIME_SERIES = "Time series viewer";
	private static final String VIEWER_CLUSTER = "Cluster viewer";
	private static final String VIEWER_GRAPH = "Graph viewer";

	private OutputPanel outputPanel;

	// ---- "Run summary" header ------------------------------------------
	private JPanel summaryPanel;
	private JLabel labelAlgorithmValue;
	private JLabel labelInputFileValue;
	private JLabel labelRunAtValue;

	public ResultsPanel() {
		setLayout(new BorderLayout());

		summaryPanel = buildSummaryPanel();
		add(summaryPanel, BorderLayout.NORTH);

		outputPanel = new OutputPanel();
		add(outputPanel, BorderLayout.CENTER);

		clearSummary();
	}

	/**
	 * Builds the "Run summary" header: a small titled panel with three
	 * read-only rows (algorithm, input file, run date/time + duration).
	 */
	private JPanel buildSummaryPanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		TitledBorder border = BorderFactory.createTitledBorder("Run summary");
		border.setTitleFont(new Font(Font.DIALOG, Font.BOLD, 13));
		panel.setBorder(border);

		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 6, 2, 6);
		c.anchor = GridBagConstraints.WEST;

		c.gridx = 0; c.gridy = 0;
		panel.add(sectionLabel("Algorithm:"), c);
		c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
		labelAlgorithmValue = new JLabel(" ");
		panel.add(labelAlgorithmValue, c);

		c.gridx = 0; c.gridy = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
		panel.add(sectionLabel("Input file:"), c);
		c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
		labelInputFileValue = new JLabel(" ");
		panel.add(labelInputFileValue, c);

		c.gridx = 0; c.gridy = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
		panel.add(sectionLabel("Run at:"), c);
		c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
		labelRunAtValue = new JLabel(" ");
		panel.add(labelRunAtValue, c);

		return panel;
	}

	private static JLabel sectionLabel(String text) {
		JLabel label = new JLabel(text);
		label.setFont(LABEL_FONT_BOLD);
		return label;
	}

	public void addOpenButtonListener(ActionListener l) {
		outputPanel.addOpenButtonListener(l);
	}

	public String getSelectedViewerMethod() {
		return outputPanel.getSelectedViewerMethod();
	}

	/** Clears the preview, e.g. right before a new run starts. */
	public void clearPreview() {
		outputPanel.updatePreview(null);
		clearSummary();
	}

	/** Briefly flashes the panel to signal that a run has just completed. */
	public void flash() {
		outputPanel.flashCompletion();
	}

	/**
	 * Updates the preview with the given output file, tailoring the list of
	 * available viewers to the algorithm that produced it.
	 * <p>
	 * This overload does not know the input file or run timing, so the
	 * "Run summary" header only shows the algorithm name. Prefer
	 * {@link #updatePreview(String, String, String, LocalDateTime, long)}
	 * whenever that information is available.
	 *
	 * @param outputFilePath path of the produced output file, or null to clear
	 * @param algorithmName  name of the algorithm that produced the file, used to
	 *                       determine which viewers are relevant
	 */
	public void updatePreview(String outputFilePath, String algorithmName) {
		updatePreview(outputFilePath, algorithmName, null, null, 0L);
	}

	/**
	 * Updates the preview with the given output file, tailoring the list of
	 * available viewers to the algorithm that produced it, and updates the
	 * "Run summary" header above the preview so this tab is self-explanatory
	 * (which algorithm produced this file, from which input, and when),
	 * without requiring the user to check the "Run history" tab in tab 2.
	 *
	 * @param outputFilePath path of the produced output file, or null to clear
	 * @param algorithmName  name of the algorithm that produced the file, used to
	 *                       determine which viewers are relevant, and shown in
	 *                       the summary header
	 * @param inputFilePath  path of the input file that was used, shown in the
	 *                       summary header, or null/empty if unknown
	 * @param completionTime the date/time at which the run completed, shown in
	 *                       the summary header, or null if unknown
	 * @param durationMillis how long the run took, shown alongside
	 *                       {@code completionTime} in the summary header
	 */
	public void updatePreview(String outputFilePath, String algorithmName, String inputFilePath,
			LocalDateTime completionTime, long durationMillis) {
		outputPanel.updatePreview(outputFilePath);

		if (outputFilePath == null || outputFilePath.trim().isEmpty()) {
			clearSummary();
			return;
		}

		updateSummary(algorithmName, inputFilePath, completionTime, durationMillis);

		if (algorithmName == null || algorithmName.trim().isEmpty()) {
			return;
		}

		try {
			DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
			if (algorithm != null && algorithm.getOutputFileTypes() != null) {
				outputPanel.setViewerOptions(buildViewerOptionsForAlgorithm(algorithm));
			}
		} catch (Exception e) {
			outputPanel.setViewerOptions(new String[] { VIEWER_SYSTEM_TEXT_EDITOR, VIEWER_SPMF_TEXT_EDITOR,
					VIEWER_PATTERN_VIEWER });
		}
	}

	/**
	 * Refreshes the "Run summary" header's three rows. Any piece of
	 * information that is unavailable ({@code null}/empty) is shown as
	 * "(unknown)" rather than being left blank, so the header never looks
	 * broken or half-loaded.
	 */
	private void updateSummary(String algorithmName, String inputFilePath, LocalDateTime completionTime,
			long durationMillis) {
		labelAlgorithmValue.setText(
				(algorithmName == null || algorithmName.trim().isEmpty()) ? "(unknown)" : algorithmName);

		labelInputFileValue.setText(
				(inputFilePath == null || inputFilePath.trim().isEmpty()) ? "(unknown)" : inputFilePath);

		if (completionTime == null) {
			labelRunAtValue.setText("(unknown)");
		} else {
			labelRunAtValue.setText(
					completionTime.format(TIMESTAMP_FORMAT) + "  (duration: " + formatDuration(durationMillis) + ")");
		}
	}

	/** Resets the "Run summary" header to its empty/placeholder state. */
	private void clearSummary() {
		labelAlgorithmValue.setText(" ");
		labelInputFileValue.setText(" ");
		labelRunAtValue.setText(" ");
	}

	/**
	 * Formats a duration in milliseconds as a short, human-readable string:
	 * e.g. "850 ms" for sub-second durations, or "3.42 s" otherwise.
	 */
	private static String formatDuration(long millis) {
		if (millis < 1000) {
			return millis + " ms";
		}
		return String.format("%.2f s", millis / 1000.0);
	}

	/**
	 * Builds the list of viewer options offered for the given algorithm's output
	 * file types. Logic mirrored from {@link AlgorithmRunnerPanel} so that both
	 * interfaces offer the same set of viewers for a given output type.
	 */
	private String[] buildViewerOptionsForAlgorithm(DescriptionOfAlgorithm algorithm) {
		String[] types = algorithm.getOutputFileTypes();
		List<String> opts = new ArrayList<>();

		if (types == null || types.length == 0) {
			opts.add(VIEWER_SYSTEM_TEXT_EDITOR);
			opts.add(VIEWER_SPMF_TEXT_EDITOR);
			return opts.toArray(new String[0]);
		}

		AlgorithmType at = algorithm.getAlgorithmType();

		// The generic pattern viewer is offered for regular pattern-mining
		// algorithms, unless a more specific viewer applies (see below).
		boolean includePatternViewer = at != AlgorithmType.OTHER_TOOL
				&& at != AlgorithmType.OTHER_GUI_TOOL
				&& at != AlgorithmType.DATA_GENERATOR;

		if (at != AlgorithmType.DATA_VIEWER) {
			try {
				DescriptionOfAlgorithm viewer = AlgorithmManager.getInstance().getViewerFor(types);
				if (viewer != null && viewer.getName() != null) {
					addIfAbsent(opts, viewer.getName());
				}
			} catch (Exception ignored) {
			}
		}

		for (String t : types) {
			if (t != null && t.contains("itemset")) {
				addIfAbsent(opts, "Visualize_With_ItemItemset_Matrix");
				break;
			}
		}
		for (String t : types) {
			if (t != null && t.contains("sequential patterns")) {
				addIfAbsent(opts, "Visualize_sequential_patterns_with_transition_graph_viewer");
				break;
			}
		}
		if ("Time series database".equals(types[0])) {
			includePatternViewer = false;
			addIfAbsent(opts, VIEWER_TIME_SERIES);
		}
		if ("Clusters".equals(types[0])) {
			addIfAbsent(opts, VIEWER_CLUSTER);
		}

		String lastType = types[types.length - 1];
		if ("Top-k Frequent subgraphs".equals(lastType) || "Frequent subgraphs".equals(lastType)) {
			includePatternViewer = false;
			addIfAbsent(opts, VIEWER_GRAPH);
		}

		addIfAbsent(opts, VIEWER_SYSTEM_TEXT_EDITOR);
		addIfAbsent(opts, VIEWER_SPMF_TEXT_EDITOR);
		if (includePatternViewer) {
			addIfAbsent(opts, VIEWER_PATTERN_VIEWER);
		}

		return opts.toArray(new String[0]);
	}

	private static void addIfAbsent(List<String> list, String value) {
		if (value != null && !list.contains(value)) {
			list.add(value);
		}
	}
}