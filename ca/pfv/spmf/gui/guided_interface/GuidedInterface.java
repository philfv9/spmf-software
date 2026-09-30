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

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.MainWindow;

/**
 * The guided interface of spmf, which is a wizard-like
 * sequence of three tabs:
 * <ol>
 * <li><b>Select data</b> — choose, generate, and preview the input file.</li>
 * <li><b>Select &amp; run algorithm</b> — choose an algorithm, configure its
 * parameters, choose an output file, and run it.</li>
 * <li><b>Visualize results</b> — preview / open the produced output file with
 * an appropriate viewer.</li>
 * </ol>
 *
 * <p>
 * Tabs 2 and 3 start out disabled: tab 2 only becomes reachable once an
 * input file has been chosen in tab 1, and tab 3 only becomes reachable once
 * an algorithm has been run successfully and produced an output file. This
 * is enforced by {@link GuidedInterfaceController}, which enables/disables
 * them dynamically (via {@code tabbedPane.setEnabledAt(...)}) as those
 * prerequisites are met. "Next" buttons in tabs 1 and 2 mirror this same
 * gating so users are guided step by step instead of having to discover the
 * workflow order on their own.
 * </p>
 *
 * <p>
 * In addition to the tabs, a menu bar provides access to standalone
 * "Tools" ({@link ca.pfv.spmf.algorithmmanager.AlgorithmType#OTHER_TOOL} and
 * {@link ca.pfv.spmf.algorithmmanager.AlgorithmType#OTHER_GUI_TOOL}),
 * "Experiments" ({@link ca.pfv.spmf.algorithmmanager.AlgorithmType#EXPERIMENT_TOOL}),
 * and a "Help" menu with the About window. These algorithm types do not fit
 * the tabbed select-data/select-algorithm/visualize-results workflow, so they
 * are exposed separately via the menu bar instead.
 * </p>
 *
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class GuidedInterface extends JFrame {

	/** Tab index of the data-selection tab. */
	public static final int TAB_DATA = 0;

	/** Tab index of the algorithm-selection/run tab. */
	public static final int TAB_ALGORITHM = 1;

	/** Tab index of the results-visualization tab. */
	public static final int TAB_RESULTS = 2;

	/** URL of the SPMF documentation page */
	private static final String URL_DOCUMENTATION =
			"http://philippe-fournier-viger.com/spmf/index.php?link=documentation.php";

	private JTabbedPane tabbedPane;

	private DataSelectionPanel dataPanel;
	private AlgorithmSelectionPanel algorithmPanel;
	private ResultsPanel resultsPanel;

	private GuidedInterfaceController controller;

	/**
	 * Builds the  window. Call {@link #setVisible(boolean)} to show it.
	 *
	 * @throws Exception if some error occurs while initializing the sub-panels
	 */
	public GuidedInterface() throws Exception {

		setTitle("SPMF v" + Main.SPMF_VERSION + " - Step-by-step analysis");

		try {
			setIconImage(Toolkit.getDefaultToolkit()
					.getImage(MainWindow.class.getResource("/ca/pfv/spmf/gui/spmf.png")));
		} catch (Exception e) {
			// Icon is purely cosmetic — ignore if the resource cannot be found.
		}

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setResizable(true);

		// ---- Menu bar: Tools / Experiments / Help ----------------------
		ToolMenuController toolMenuController = new ToolMenuController(this);
		JMenuBar menuBar = new JMenuBar();
		menuBar.add(toolMenuController.buildToolsMenu());
//		menuBar.add(toolMenuController.buildExperimentsMenu());

		JMenu helpMenu = toolMenuController.buildHelpMenu();
		JMenuItem documentationMenuItem = new JMenuItem("Online documentation");
		documentationMenuItem.setToolTipText("Open the SPMF documentation page in your browser");
		documentationMenuItem.addActionListener(e -> openWebPage(URL_DOCUMENTATION));
		helpMenu.add(documentationMenuItem);
		menuBar.add(helpMenu);

		setJMenuBar(menuBar);

		// ---- Tabs --------------------------------------------------------
		dataPanel = new DataSelectionPanel();
		dataPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

		algorithmPanel = new AlgorithmSelectionPanel();
		algorithmPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

		resultsPanel = new ResultsPanel();
		resultsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

		tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		tabbedPane.addTab("1. Select data", dataPanel);
		tabbedPane.addTab("2. Select & run algorithm", algorithmPanel);
		tabbedPane.addTab("3. Visualize results", resultsPanel);

		tabbedPane.setToolTipTextAt(TAB_DATA, "Choose, generate, and preview the input data file");
		tabbedPane.setToolTipTextAt(TAB_ALGORITHM, "Choose an algorithm, set its parameters, and run it");
		tabbedPane.setToolTipTextAt(TAB_RESULTS, "Preview and open the output produced by the algorithm");

		// Tabs 2 and 3 depend on prerequisites from earlier steps —
		// selecting an input file (tab 1), and successfully running an
		// algorithm that produces an output file (tab 2) — so they start
		// out disabled. GuidedInterfaceController enables/disables them
		// dynamically as those prerequisites become (or stop being) true.
		tabbedPane.setEnabledAt(TAB_ALGORITHM, false);
		tabbedPane.setEnabledAt(TAB_RESULTS, false);

		setContentPane(tabbedPane);

		controller = new GuidedInterfaceController(dataPanel, algorithmPanel, resultsPanel, tabbedPane, this);
		controller.initializeUIEventHandling();

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				System.exit(0);
			}
		});

		setPreferredSize(new Dimension(1000, 720));
		pack();
		setMinimumSize(new Dimension(800, 600));
		setLocationRelativeTo(null);
	}

	/**
	 * Opens the given URL in the system default web browser.
	 *
	 * @param url the URL to open
	 */
	private void openWebPage(String url) {
		try {
			if (!Desktop.isDesktopSupported()
					|| !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				JOptionPane.showMessageDialog(this,
						"Opening a web browser is not supported on this system.\n"
								+ "Please open the following address manually:\n" + url,
						"Cannot open browser", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			Desktop.getDesktop().browse(URI.create(url));
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this,
					"Could not open the web page:\n" + url + "\n\nERROR MESSAGE = " + e.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Standalone entry point for testing this guided interface independently of the
	 * regular SPMF launcher.
	 *
	 * @param args ignored
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try {
				JFrame.setDefaultLookAndFeelDecorated(false);
				GuidedInterface window = new GuidedInterface();
				window.setVisible(true);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}
}