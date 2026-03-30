package ca.pfv.spmf.gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;

/*
 * Copyright (c) 2008-2023 Philippe Fournier-Viger
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
 * This class is for monitoring the memory usage using a JFrame window
 * 
 * @author Philippe Fournier-Viger, 2023
 */
public class MemoryViewer extends JPanel implements ActionListener, MouseMotionListener {
	/**
	 * generate serial VUID
	 */
	private static final long serialVersionUID = 278261238139167055L;

	/** The JFrame */
	static JFrame frame = null;

	/** Number of milliseconds before a refresh */
	private static final int REFRESH_RATE = 1000;

	/** The timer that updates the chart every second */
	private static Timer timer;

	/** The array that stores the memory usage values */
	private int[] memoryValues;

	/** The index of the current value in the array */
	private int position;

	/** The maximum number of values to display */
	private static final int VALUE_COUNT = 100;

	/** The maximum memory usage value to scale the chart */
	private static int maxMemoryYAxis;


	/** Color scheme for better visual appeal */
	private static final Color BACKGROUND_COLOR = new Color(250, 250, 250);
	private static final Color GRID_COLOR = new Color(220, 220, 220);
	private static final Color LINE_COLOR = new Color(66, 133, 244); // Modern blue
	private static final Color FILL_COLOR = new Color(66, 133, 244, 40); // Transparent blue
	private static final Color TEXT_COLOR = new Color(60, 60, 60);
	private static final Color AXIS_COLOR = new Color(180, 180, 180);

	/** The checkbox for toggling the grid lines */
	private JCheckBox gridCheckBox;

	/** The checkbox for toggling area fill */
	private JCheckBox fillCheckBox;

	/** The button for pausing and resuming the timer */
	private JButton pauseButton;

	/** The button for clearing memory */
	private JButton clearButton;

	/** The flag for indicating the timer state */
	private boolean isPaused;

	/** Labels for displaying memory statistics */
	private JLabel currentMemoryLabel;
	private JLabel maxMemoryLabel;
	private JLabel avgMemoryLabel;
	private JLabel totalMemoryLabel;

	/** Chart margins */
	private static final int MARGIN_LEFT = 60;
	private static final int MARGIN_RIGHT = 20;
	private static final int MARGIN_TOP = 40;
	private static final int MARGIN_BOTTOM = 30;

	/** Chart panel reference */
	private JPanel chartPanel;

	/** The constructor of the panel */
	public MemoryViewer() {
		// Initialize the array with zeros
		memoryValues = new int[VALUE_COUNT];
		Arrays.fill(memoryValues, 0);

		// Initialize the index with zero
		position = 0;

		// Set the background color of the panel
		setLayout(new BorderLayout(5, 5));
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		maxMemoryYAxis = (int) (Runtime.getRuntime().totalMemory() / (1024 * 1024));

		// Create the top control panel FIRST (so labels exist)
		JPanel topPanel = createTopPanel();
		add(topPanel, BorderLayout.NORTH);

		// Create the chart panel
		chartPanel = createChartPanel();
		add(chartPanel, BorderLayout.CENTER);

		// Create the bottom control panel
		JPanel bottomPanel = createBottomPanel();
		add(bottomPanel, BorderLayout.SOUTH);

		// NOW take initial reading (after UI components are created)
		updateMemoryReading();

		// Create and start the timer with one second delay
		timer = new Timer(REFRESH_RATE, this);
		timer.start();

		// Set the initial delay of the tooltip manager to zero
		ToolTipManager.sharedInstance().setInitialDelay(0);
	}

	/**
	 * Updates memory reading - extracted to method to avoid duplication
	 */
	private void updateMemoryReading() {
		// Get the runtime instance of the JVM
		Runtime runtime = Runtime.getRuntime();

		// Get the total, free, and used memory of the JVM in megabytes
		int jvmMemory = (int) (runtime.totalMemory() / (1024 * 1024));
		int freeMemory = (int) (runtime.freeMemory() / (1024 * 1024));
		int usedMemory = jvmMemory - freeMemory;

		// Store the used memory value in the array
		memoryValues[position] = usedMemory;

		// Find the max value (either the JVM max value or the max value that we
		// have seen until now (because it might be bigger than the JVM max value)
		maxMemoryYAxis = jvmMemory;
		for (int i = 0; i < memoryValues.length; i++) {
			if (memoryValues[i] > maxMemoryYAxis) {
				maxMemoryYAxis = memoryValues[i];
			}
		}

		// Increment the index and wrap around if necessary
		position = (position + 1) % VALUE_COUNT;

		// Update statistics labels (only if they exist)
		if (currentMemoryLabel != null) {
			updateStatistics(usedMemory, jvmMemory);
		}
	}

	/**
	 * Creates the top panel with statistics
	 */
	private JPanel createTopPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		panel.setBackground(BACKGROUND_COLOR);

		currentMemoryLabel = new JLabel("Current: 0 MB");
		maxMemoryLabel = new JLabel("Peak: 0 MB");
		avgMemoryLabel = new JLabel("Avg: 0 MB");
		totalMemoryLabel = new JLabel("Total JVM: 0 MB");


		currentMemoryLabel.setForeground(TEXT_COLOR);
		maxMemoryLabel.setForeground(TEXT_COLOR);
		avgMemoryLabel.setForeground(TEXT_COLOR);
		totalMemoryLabel.setForeground(TEXT_COLOR);

		panel.add(currentMemoryLabel);
		panel.add(Box.createHorizontalStrut(15));
		panel.add(maxMemoryLabel);
		panel.add(Box.createHorizontalStrut(15));
		panel.add(avgMemoryLabel);
		panel.add(Box.createHorizontalStrut(15));
		panel.add(totalMemoryLabel);
		panel.add(Box.createHorizontalGlue());

		return panel;
	}

	/**
	 * Creates the chart panel
	 */
	private JPanel createChartPanel() {
		JPanel panel = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				paintChart(g);
			}
		};
		panel.setBackground(Color.WHITE);
		panel.setBorder(BorderFactory.createLineBorder(GRID_COLOR, 1));
		panel.setPreferredSize(new Dimension(700, 400));
		panel.addMouseMotionListener(this);
		return panel;
	}

	/**
	 * Creates the bottom panel with controls
	 */
	private JPanel createBottomPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		// Control buttons panel
		JPanel controlPanel = new JPanel();
		controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.X_AXIS));
		controlPanel.setAlignmentX(LEFT_ALIGNMENT);

		// Create the checkbox for toggling the grid lines
		gridCheckBox = new JCheckBox("Grid");
		gridCheckBox.setSelected(true);
		gridCheckBox.addActionListener(this);
		gridCheckBox.setFocusPainted(false);

		// Create the checkbox for toggling area fill
		fillCheckBox = new JCheckBox("Fill");
		fillCheckBox.setSelected(true);
		fillCheckBox.addActionListener(this);
		fillCheckBox.setFocusPainted(false);

		// Create the button for pausing and resuming the timer
		pauseButton = new JButton("⏸ Pause");
		pauseButton.addActionListener(this);
		pauseButton.setFocusPainted(false);
		isPaused = false;

		// Create the button for clearing memory (garbage collection)
		clearButton = new JButton("🗑 Clear Memory");
		clearButton.addActionListener(this);
		clearButton.setFocusPainted(false);
		clearButton.setToolTipText("Run garbage collection");

		controlPanel.add(gridCheckBox);
		controlPanel.add(Box.createHorizontalStrut(10));
		controlPanel.add(fillCheckBox);
		controlPanel.add(Box.createHorizontalStrut(10));
		controlPanel.add(pauseButton);
		controlPanel.add(Box.createHorizontalStrut(10));
		controlPanel.add(clearButton);
		controlPanel.add(Box.createHorizontalGlue());

		// Slider panel
		JPanel sliderPanel = new JPanel();
		sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.X_AXIS));
		sliderPanel.setAlignmentX(LEFT_ALIGNMENT);

		JLabel sliderLabel = new JLabel("Refresh rate: ");
		sliderLabel.setForeground(TEXT_COLOR);

		JSlider slider = new JSlider(JSlider.HORIZONTAL, 100, 5000, REFRESH_RATE);
		slider.addChangeListener(e -> {
			if (!timer.isRunning() || timer.getDelay() != slider.getValue()) {
				timer.setDelay(slider.getValue());
				if (chartPanel != null) {
					chartPanel.repaint();
				}
			}
		});

		slider.setPaintTicks(true);
		slider.setPaintLabels(true);
		slider.setMajorTickSpacing(1000);
		slider.setMinorTickSpacing(250);
		slider.setSnapToTicks(false);

		sliderPanel.add(sliderLabel);
		sliderPanel.add(Box.createHorizontalStrut(5));
		sliderPanel.add(slider);

		panel.add(controlPanel);
		panel.add(Box.createVerticalStrut(5));
		panel.add(sliderPanel);

		return panel;
	}

	/** The method that handles the timer and checkbox events */
	public void actionPerformed(ActionEvent e) {
		// Get the source of the event
		Object source = e.getSource();

		// If the source is the timer
		if (source == timer) {
			updateMemoryReading();

			// Repaint the panel to update the chart and text
			repaint();
			if (isVisible() == false) {
				timer.stop();
			}
		}

		// If the source is the checkbox
		if (source == gridCheckBox || source == fillCheckBox) {
			// Repaint the panel to update the grid lines or fill
			repaint();
		}

		// If the source is the button
		if (source == pauseButton) {
			// Toggle the timer state
			isPaused = !isPaused;

			// If the timer is paused
			if (isPaused) {
				// Stop the timer and change the button text to "Resume"
				timer.stop();
				pauseButton.setText("▶ Resume");
			}
			// If the timer is resumed
			else {
				// Start the timer and change the button text to "Pause"
				timer.start();
				pauseButton.setText("⏸ Pause");
			}
		}

		// If the source is the clear button
		if (source == clearButton) {
			// Run garbage collection
			System.gc();
		}
	}

	/**
	 * Updates the statistics labels
	 */
	private void updateStatistics(int current, int total) {
		currentMemoryLabel.setText("Current: " + current + " MB");
		totalMemoryLabel.setText("Total JVM: " + total + " MB");

		// Calculate peak memory
		int peak = 0;
		int sum = 0;
		int count = 0;
		for (int i = 0; i < VALUE_COUNT; i++) {
			int value = memoryValues[i];
			if (value > peak) {
				peak = value;
			}
			if (value > 0) {
				sum += value;
				count++;
			}
		}

		maxMemoryLabel.setText("Peak: " + peak + " MB");
		if (count > 0) {
			avgMemoryLabel.setText("Avg: " + (sum / count) + " MB");
		}
	}

	/** The method that paints the chart */
	private void paintChart(Graphics g) {
		// Cast the graphics object to Graphics2D for better rendering
		Graphics2D g2d = (Graphics2D) g;

		// Enable anti-aliasing for smoother graphics
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		// Get the width and height of the component
		int width = chartPanel.getWidth();
		int height = chartPanel.getHeight();

		// Calculate chart area
		int chartWidth = width - MARGIN_LEFT - MARGIN_RIGHT;
		int chartHeight = height - MARGIN_TOP - MARGIN_BOTTOM;

		// Draw Y-axis labels and grid lines
		g2d.setColor(AXIS_COLOR);

		int numYLabels = 5;
		for (int i = 0; i <= numYLabels; i++) {
			int value = (maxMemoryYAxis * i) / numYLabels;
			int y = MARGIN_TOP + chartHeight - (i * chartHeight / numYLabels);

			// Draw Y-axis label
			String label = value + "";
			g2d.drawString(label, 5, y + 5);

			// Draw horizontal grid line if enabled
			if (gridCheckBox.isSelected()) {
				g2d.setColor(GRID_COLOR);
				g2d.drawLine(MARGIN_LEFT, y, MARGIN_LEFT + chartWidth, y);
				g2d.setColor(AXIS_COLOR);
			}
		}

		// Draw vertical grid lines if enabled
		if (gridCheckBox.isSelected()) {
			g2d.setColor(GRID_COLOR);
			int numXLabels = 10;
			for (int i = 0; i <= numXLabels; i++) {
				int x = MARGIN_LEFT + (i * chartWidth / numXLabels);
				g2d.drawLine(x, MARGIN_TOP, x, MARGIN_TOP + chartHeight);
			}
		}

		// Draw axes
		g2d.setColor(AXIS_COLOR);
		g2d.drawLine(MARGIN_LEFT, MARGIN_TOP, MARGIN_LEFT, MARGIN_TOP + chartHeight); // Y-axis
		g2d.drawLine(MARGIN_LEFT, MARGIN_TOP + chartHeight, MARGIN_LEFT + chartWidth, MARGIN_TOP + chartHeight); // X-axis

		// Calculate the scaling factor for the chart based on the maximum memory value
		double scale = (double) chartHeight / maxMemoryYAxis;

		// Create arrays for polygon points (for area fill)
		int[] xPoints = new int[VALUE_COUNT + 2];
		int[] yPoints = new int[VALUE_COUNT + 2];

		// First point at bottom-left
		xPoints[0] = MARGIN_LEFT;
		yPoints[0] = MARGIN_TOP + chartHeight;

		// Draw the chart line and build polygon
		g2d.setColor(LINE_COLOR);
		g2d.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		// Loop through the values array and draw a line segment for each pair of values
		for (int i = 0; i < VALUE_COUNT - 1; i++) {
			// Get the current and next values from the array
			int currentValue = memoryValues[(position + i) % VALUE_COUNT];
			int nextValue = memoryValues[(position + i + 1) % VALUE_COUNT];

			// Calculate the x and y coordinates for the current and next points on the chart
			int currentX = MARGIN_LEFT + (i * chartWidth / VALUE_COUNT);
			int currentY = MARGIN_TOP + chartHeight - (int) (currentValue * scale);
			int nextX = MARGIN_LEFT + ((i + 1) * chartWidth / VALUE_COUNT);
			int nextY = MARGIN_TOP + chartHeight - (int) (nextValue * scale);

			// Store polygon points
			xPoints[i + 1] = currentX;
			yPoints[i + 1] = currentY;

			// Draw a line segment between the current and next points on the chart
			g2d.drawLine(currentX, currentY, nextX, nextY);
		}

		// Last point for polygon
		xPoints[VALUE_COUNT] = MARGIN_LEFT + chartWidth;
		yPoints[VALUE_COUNT] = MARGIN_TOP + chartHeight - (int) (memoryValues[(position + VALUE_COUNT - 1) % VALUE_COUNT] * scale);

		// Bottom-right point to close polygon
		xPoints[VALUE_COUNT + 1] = MARGIN_LEFT + chartWidth;
		yPoints[VALUE_COUNT + 1] = MARGIN_TOP + chartHeight;

		// Fill area under the line if enabled
		if (fillCheckBox.isSelected()) {
			g2d.setColor(FILL_COLOR);
			g2d.fillPolygon(xPoints, yPoints, VALUE_COUNT + 2);
		}

		// Draw the title
		g2d.setColor(TEXT_COLOR);
		String title = "Memory Usage (MB)";
		g2d.drawString(title, MARGIN_LEFT, 20);

		// Draw refresh rate info
		g2d.drawString("Refresh: " + timer.getDelay() + " ms", width - 120, height - 10);
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		// Get the x and y coordinates of the mouse pointer
		int x = e.getX();
		int y = e.getY();

		// Get the width and height of the chart panel
		int width = chartPanel.getWidth();
		int height = chartPanel.getHeight();

		// Calculate chart area
		int chartWidth = width - MARGIN_LEFT - MARGIN_RIGHT;
		int chartHeight = height - MARGIN_TOP - MARGIN_BOTTOM;

		// Check if mouse is within chart area
		if (x < MARGIN_LEFT || x > MARGIN_LEFT + chartWidth || y < MARGIN_TOP || y > MARGIN_TOP + chartHeight) {
			chartPanel.setToolTipText(null);
			return;
		}

		// Calculate the scaling factor for the chart based on the maximum memory value
		double scale = (double) chartHeight / maxMemoryYAxis;

		// Calculate the index of the value corresponding to the mouse position
		int index = (x - MARGIN_LEFT) * VALUE_COUNT / chartWidth;

		// Bounds check
		if (index < 0 || index >= VALUE_COUNT) {
			chartPanel.setToolTipText(null);
			return;
		}

		// Get the value from the array
		int value = memoryValues[(position + index) % VALUE_COUNT];

		// Calculate the y coordinate of the value on the chart
		int valueY = MARGIN_TOP + chartHeight - (int) (value * scale);

		// If the mouse pointer is close enough to the chart line
		if (Math.abs(y - valueY) < 15) {
			// Set the tooltip text to show the memory value with more details
			int secondsAgo = VALUE_COUNT - index - 1;
			String timeInfo = secondsAgo == 0 ? "now" : secondsAgo + "s ago";
			chartPanel.setToolTipText("<html><b>" + value + " MB</b><br>" + timeInfo + "</html>");
		} else {
			// Set the tooltip text to null
			chartPanel.setToolTipText(null);
		}
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		// Not used
	}

	/** The main method that creates and displays the frame with the panel */
	public static void main(String[] args) {
		// Set the look and feel to the system default
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			e.printStackTrace();
		}
		displayMemoryChart();
	}

	/** main method for testing **/
	public static void displayMemoryChart() {
		if (frame != null) {
			frame.setVisible(true);
			frame.toFront();
			return;
		}
		// Create a new frame with the title "Memory Chart"
		frame = new JFrame("SPMF Memory Viewer (JVM)");

		// Set the size and location of the frame
		frame.setSize(750, 600);
		frame.setMinimumSize(new Dimension(600, 400)); // ADD THIS LINE
		frame.setLocationRelativeTo(null);

		// Set the default close operation of the frame
		frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

		// Create a new instance of the panel and add it to the frame
		MemoryViewer panel = new MemoryViewer();
		frame.add(panel);

		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				panel.setVisible(false);
				frame = null;
			}
		});

		// Make the frame visible
		frame.setVisible(true);
	}
}