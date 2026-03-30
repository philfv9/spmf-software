package ca.pfv.spmf.gui.visuals.timeline;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.texteditor.SPMFTextEditor;

@SuppressWarnings("serial")
/*
 * Copyright (c) 2022 Philippe Fournier-Viger
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
 * The TimelineViewer tool of SPMF
 * 
 * @author Philippe Fournier-Viger
 */
public class TimelineViewer extends JFrame {

	/** The time line panel */
	private Timeline timeline;
	
	/** DEBUG MODE WITH MORE FEATURES */
	private boolean DEBUG = false;

	/**
	 * The constructor
	 * 
	 * @param runAsStandaloneProgram set to true if this is run as a standalone
	 *                               program
	 * @param events                 a set of events to be displayed
	 * @param intervals              a set of intervals to be displayed
	 */
	public TimelineViewer(boolean runAsStandaloneProgram, List<EventT> events, List<IntervalT> intervals) {
		super("SPMF Timeline Viewer" + Main.SPMF_VERSION);
		
		setIconImage(Toolkit.getDefaultToolkit().getImage(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/timeline20b.png")));
		if(events != null) {
			this.timeline = new Timeline(events, true);
		}else {
			this.timeline = new Timeline(true, intervals);
		}
		
		if(runAsStandaloneProgram) {
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}

		// Create the menu bar
		JMenuBar menuBar = new JMenuBar();
		JMenu settingsMenu = new JMenu("Settings");

		// Create menu itemss
		JMenuItem eventGapItem = new JMenuItem("Set vertical gap between events");
		JMenuItem intervalHeightItem = new JMenuItem("Set interval height");
		JMenuItem intervalGapItem = new JMenuItem("Set vertical gap between intervals");
		JMenuItem tickIntervalItem = new JMenuItem("Set tick interval");
		JMenuItem tickHeightItem = new JMenuItem("Set tick height");

		// Add action listeners
		eventGapItem.addActionListener(e -> setEventVerticalGap());
		intervalHeightItem.addActionListener(e -> setTimelineIntervalHeight());
		intervalGapItem.addActionListener(e -> setTimelineIntervalGap());
		tickHeightItem.addActionListener(e -> setTimelineTickHeight());
		tickIntervalItem.addActionListener(e -> setTickInterval());

		// Add a new menu for label visibility
		if (intervals != null && intervals.size() > 0) {
			JCheckBoxMenuItem showIntervalLabelsItem = new JCheckBoxMenuItem("Show interval Labels", true);
			showIntervalLabelsItem.addActionListener(e -> timeline.toggleShowIntervalLabels());
			settingsMenu.add(showIntervalLabelsItem);
		}
		if (events != null && events.size() > 0) {
			JCheckBoxMenuItem showEventLabelsItem = new JCheckBoxMenuItem("Show event Labels", true);
			showEventLabelsItem.addActionListener(e -> timeline.toggleShowEventLabels());
			settingsMenu.add(showEventLabelsItem);
		}

		settingsMenu.addSeparator();

		// Add menu items to the menu
		if (DEBUG && events != null && events.size() > 0) {
			settingsMenu.add(eventGapItem);
		}

		// Add menu items to the menu
		if (DEBUG && intervals != null && intervals.size() > 0) {
			settingsMenu.add(intervalHeightItem);
			settingsMenu.add(intervalGapItem);
			settingsMenu.addSeparator();
		}
		if(DEBUG) {
			settingsMenu.add(tickHeightItem);
		}
		settingsMenu.add(tickIntervalItem);
		settingsMenu.addSeparator();

		// Add a new menu item for setting the scale factor
		JMenuItem scaleFactorItem = new JMenuItem("Set X axis scale factor");
		scaleFactorItem.addActionListener(e -> setTimelineScaleFactor());
		settingsMenu.add(scaleFactorItem);

		settingsMenu.addSeparator();

		JMenuItem minTimestampMenuItem = new JMenuItem("Set min. timestamp");
		minTimestampMenuItem.addActionListener(e -> setTimelineMinTimestamp());
		settingsMenu.add(minTimestampMenuItem);

		JMenuItem maxTimestampMenuItem = new JMenuItem("Set max. timestamp");
		maxTimestampMenuItem.addActionListener(e -> setTimelineMaxTimestamp());
		settingsMenu.add(maxTimestampMenuItem);

		// Export Menu
		JMenu exportMenu = new JMenu("Export");
		JMenuItem exportImageItem = new JMenuItem("Export as Image");
		exportImageItem.setIcon(new ImageIcon(SPMFTextEditor.class.getResource("/ca/pfv/spmf/gui/icons/save.gif")));
		exportImageItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				exportTimelineasImage();
			}

		});
		exportMenu.add(exportImageItem);

		// Add the menu to the menu bar and set the menu bar
		menuBar.add(settingsMenu);
		menuBar.add(exportMenu);
		setJMenuBar(menuBar);

		JScrollPane scrollPane = new JScrollPane(timeline);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setPreferredSize(new Dimension(800, 200));
		// After the frame is visible, adjust the scrollbar position
		SwingUtilities.invokeLater(() -> {
			JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
			verticalScrollBar.setValue(verticalScrollBar.getMaximum());
		});
		
		   // Add the slider panel at the bottom
        JPanel sliderPanel = new JPanel(new BorderLayout());
        JLabel sliderLabel = new JLabel("X axis scale factor:");
        JSlider scaleSlider = new JSlider(1, 100, (int) timeline.getXScaleFactor());
        scaleSlider.addChangeListener(e -> {
            JSlider source = (JSlider) e.getSource();
            if (!source.getValueIsAdjusting()) {
                double newScaleFactor = source.getValue();
                timeline.setXScaleFactor(newScaleFactor);
                timeline.repaint();
            }
        });

        sliderPanel.add(sliderLabel, BorderLayout.WEST);
        sliderPanel.add(scaleSlider, BorderLayout.CENTER);
        add(sliderPanel, BorderLayout.SOUTH);


		setLocationRelativeTo(null);
		add(scrollPane);
		pack();
		setVisible(true);
	}

	/**
	 * Ask the user to set the event vertical gap
	 */
	private void setEventVerticalGap() {
		String input = JOptionPane.showInputDialog(this, "Enter new event vertical gap:", timeline.getEventHeight());
		if (input != null && !input.isEmpty()) {
			try {
				int newGap = Integer.parseInt(input);
				timeline.setEventHeight(newGap);
				timeline.repaint();
			} catch (NumberFormatException ex) {
				JOptionPane.showMessageDialog(this, "Please enter a valid number.");
			}
		}
	}

	/**
	 * Ask the user to set the time interval height
	 */
	private void setTimelineIntervalHeight() {
		String input = JOptionPane.showInputDialog(this, "Enter new interval height:", timeline.getIntervalHeight());
		if (input != null && !input.isEmpty()) {
			try {
				int newHeight = Integer.parseInt(input);
				timeline.setIntervalHeight(newHeight);
				timeline.repaint();
			} catch (NumberFormatException ex) {
				JOptionPane.showMessageDialog(this, "Please enter a valid number.");
			}
		}
	}

	/**
	 * Ask the user to set the interval gap
	 */
	private void setTimelineIntervalGap() {
		String input = JOptionPane.showInputDialog(this, "Enter new interval vertical gap:", timeline.getIntervalVerticalGap());
		if (input != null && !input.isEmpty()) {
			try {
				int newGap = Integer.parseInt(input);
				timeline.setIntervalVerticalGap(newGap);
				timeline.repaint();
			} catch (NumberFormatException ex) {
				JOptionPane.showMessageDialog(this, "Please enter a valid number.");
			}
		}
	}

	/**
	 * Ask the user to set the tick height
	 */
	private void setTimelineTickHeight() {
		String input = JOptionPane.showInputDialog(this, "Enter new tick height:", timeline.getTickHeight());
		if (input != null && !input.isEmpty()) {
			try {
				int newTickHeight = Integer.parseInt(input);
				timeline.setTickHeight(newTickHeight);
				timeline.repaint();
			} catch (NumberFormatException ex) {
				JOptionPane.showMessageDialog(this, "Please enter a valid number.");
			}
		}
	}

	/**
	 * Ask the user to set the tick interval
	 */
	private void setTickInterval() {
		String input = JOptionPane.showInputDialog(this, "Enter new tick interval length (integer):", timeline.getTickInterval());
		if (input != null && !input.isEmpty()) {
			try {
				int newTickIntervalLength = Integer.parseInt(input);
				timeline.setTickInterval(newTickIntervalLength);
				timeline.repaint();
			} catch (NumberFormatException ex) {
				JOptionPane.showMessageDialog(this, "Please enter a valid number.");
			}
		}
	}

	/**
	 * Export the timeline as a picture
	 */
	private void exportTimelineasImage() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Specify a file to save");
		int userSelection = fileChooser.showSaveDialog(this);

		if (userSelection == JFileChooser.APPROVE_OPTION) {
			File fileToSave = fileChooser.getSelectedFile();
			// Ensure the file has the correct extension
			if (!fileToSave.getName().toLowerCase().endsWith(".png")) {
				fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".png");
			}
			saveComponentAsImage(timeline, fileToSave);
		}
	}

	/**
	 * Ask the user to set the scale factor for the timeline
	 */
	private void setTimelineScaleFactor() {
		String input = JOptionPane.showInputDialog(this, "Enter new scale factor:", timeline.getXScaleFactor());
		if (input != null && !input.isEmpty()) {
			try {
				double newScaleFactor = Double.parseDouble(input);
				timeline.setXScaleFactor(newScaleFactor);
				timeline.repaint();
			} catch (NumberFormatException ex) {
				JOptionPane.showMessageDialog(this, "Please enter a valid number.");
			}
		}
	}

	/**
	 * Save a component as a picture
	 */
	private void saveComponentAsImage(Component comp, File file) {
		BufferedImage image = new BufferedImage(comp.getWidth(), comp.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = image.createGraphics();
		comp.paint(g2d);
		g2d.dispose();
		try {
			ImageIO.write(image, "png", file);
			JOptionPane.showMessageDialog(this, "Timeline image saved successfully!", "Image Export",
					JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this, "Error saving image: " + ex.getMessage(), "Export Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Ask the user to set the minimum timestamp
	 */
	private void setTimelineMinTimestamp() {
		String input = JOptionPane.showInputDialog(this, "Enter new minimum timestamp:", timeline.getMinTimestamp());
		if (input != null && !input.isEmpty()) {
			try {
				int newMinTimestamp = Integer.parseInt(input);
				timeline.setMinTimestamp(newMinTimestamp);
				timeline.repaint();
			} catch (NumberFormatException ex) {
				JOptionPane.showMessageDialog(this, "Please enter a valid number.");
			}
		}
	}

	/**
	 * Ask the user to set the maximum timestamp
	 */
	private void setTimelineMaxTimestamp() {
		String input = JOptionPane.showInputDialog(this, "Enter new maximum timestamp:", timeline.getMaxTimestamp());
		if (input != null && !input.isEmpty()) {
			try {
				int newMaxTimestamp = Integer.parseInt(input);
				timeline.setMaxTimestamp(newMaxTimestamp);
				timeline.repaint();
			} catch (NumberFormatException ex) {
				JOptionPane.showMessageDialog(this, "Please enter a valid number.");
			}
		}
	}
}