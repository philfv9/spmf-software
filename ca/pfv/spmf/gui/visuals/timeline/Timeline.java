package ca.pfv.spmf.gui.visuals.timeline;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.JPanel;

@SuppressWarnings("serial")
/*
 * 
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
 * The timeline panel used by the TimelineViewer of SPMF
 * 
 * @author Philippe Fournier-Viger
 */
class Timeline extends JPanel {
	/** The background color */
	private static Color BACKGROUND_COLOR = Color.WHITE;
	/** The text color */
	private static Color TEXT_COLOR = Color.BLACK;
	/** The text color */
	private static Color INTERVAL_BORDER_COLOR = Color.DARK_GRAY;
	/** The margin around the panel */
	private final int MARGIN = 50;
	/** The height of ticks in pixels */
	private int tickHeight = 15;
	/** The height of an interval */
	private int intervalHeight = 10;
	/** The vertical space between two intervals */
	private int intervalVerticalGap = 5;
	/** The vertical size given for an event */
	private int eventHeight = 20;
	/** The list of events */
	private final List<EventT> events;
	/** The list of intervals */
	private final List<IntervalT> intervals;
	/** The minimum timestamp to display */
	private long minTimestamp;
	/** The maximum timestamp to display */
	private long maxTimestamp;
	/** The interval of time between two ticks on the timeline */
	private int tickInterval = 100;
	/** The size of an event mark */
	private int eventMarkSize = 8;
	/** Indicate if interval names should be shown */
	private boolean showIntervalLabels = true;
	/** Indicate if event names should be shown */
	private boolean showEventLabels = true;
	/** The scale factor for the X axis */
	private double xScaleFactor = 1.0;
	/** The fond to be used for labels */
	private Font font = new Font("Arial", Font.PLAIN, 12);
	/** Colors for groups **/
	private Color[] groupColors = new Color[] {
			new Color(240, 128, 128), // Light Coral 
		    new Color(144, 238, 144), // Light Green
		    new Color(173, 216, 230), // Light Blue
		    Color.YELLOW, // Yellow 
		    new Color(224, 255, 255), // Light Cyan
		    new Color(255, 182, 193), // Light Pink 
		    Color.ORANGE, // Orange 
		    Color.PINK, // Pink 
		    Color.LIGHT_GRAY, // Light Gray 
		    new Color(255, 239, 213) // Papaya Whip 
		};

	/**
	 * The maximum number of layers for stacking events or intervals based on data
	 */
	private int maxIndex = 0;

	/**
	 * Constructor to display a list of events
	 * 
	 * @param events a list of events
	 */
	public Timeline(List<EventT> events, boolean showEventLabels) {
		this.showEventLabels = showEventLabels;
		this.events = events;
		this.intervals = null;
		this.minTimestamp = Long.MAX_VALUE;
		this.maxTimestamp = Long.MIN_VALUE;

		// Loop through the events and find the minimum and maximum timestamps
		events.sort(Comparator.comparingLong(EventT::getTime));
		minTimestamp = events.get(0).getTime();
		maxTimestamp = events.getLast().getTime();

		calculateDynamicTickSizeForEvents();

		maxTimestamp = roundToNearestMultiple(maxTimestamp, tickInterval) + (minTimestamp);
		System.out.println("MAX " + maxTimestamp);

		calculateEventsPositions();

		// Set the background color of the panel to the constant value
		this.setBackground(BACKGROUND_COLOR);

		validate();
		repaint();
	}

	/**
	 * Constructor to display a list of intervals
	 * 
	 * @param intervals a list of intervals
	 */
	public Timeline(boolean showIntervalLabels, List<IntervalT> intervals) {
		this.tickInterval = 50;
		this.events = null;
		this.intervals = intervals;
		this.minTimestamp = Integer.MAX_VALUE;
		this.maxTimestamp = Integer.MIN_VALUE;

		// Loop through the intervals and find the minimum and maximum timestamps
		intervals.sort(Comparator.comparingInt(IntervalT::getGroup).thenComparingLong(IntervalT::getStartTime));

		for (IntervalT interval : intervals) {
			if (interval.getStartTime() < minTimestamp) {
				minTimestamp = interval.getStartTime();
			}
			if (interval.getEndTime() > maxTimestamp) {
				maxTimestamp = interval.getEndTime();
			}
		}

		calculateDynamicTickSizeForIntervals();

//		minTimestamp = minTimestamp - tickInterval;
		maxTimestamp = roundToNearestMultiple(maxTimestamp, tickInterval) + minTimestamp;

		calculateIntervalsPositions();

		// Set the background color of the panel to the constant value
		this.setBackground(BACKGROUND_COLOR);

		validate();
		repaint();
	}
	
	public void calculateDynamicTickSizeForIntervals() {
	    // Determine the minimum duration
//	    long averageDuration = 0;
	    long minDuration = Long.MAX_VALUE;
//	    int count = 0;
	    int maxLabelWidth = 0;
	    FontMetrics metrics = getFontMetrics(font);

	    // Calculate the maximum label width
	    for (IntervalT interval : intervals) {
	        String label = interval.getName();
	        int labelWidth = metrics.stringWidth(label);
	        if (labelWidth > maxLabelWidth) {
	            maxLabelWidth = labelWidth;
	        } 
	    }

	    // Calculate the minimum duration
	    for (IntervalT interval : intervals) {
	        long duration = interval.getEndTime() - interval.getStartTime();
	        if (duration != 0) {
	            if(duration < minDuration) {
	            	minDuration = duration;
	            }
	        }
	    }    
	    
		// Adjust the tick interval to a rounded value for better readability
		long minTickInterval = 10; // This is an arbitrary value, adjust as needed
		tickInterval = (int) roundToNearestMultiple(minDuration, minTickInterval);

	    // Calculate the minimum interval width based on the maximum label width
	    int minIntervalWidth = maxLabelWidth + 10; // Add some padding
	    
	    // Calculate the scale factor based on the minimum interval width
	    double minScaleFactor = (double) minIntervalWidth / minDuration;

	    // Set the scale factor to the maximum of the calculated minScaleFactor and 1.0
	    xScaleFactor = Math.max(minScaleFactor, 1.0);

	    // Update the preferred size of the component
	    updatePreferredSize();
	}


	/**
	 * Calculate the positions of the intervals in terms of layers
	 */
	public void calculateIntervalsPositions() {
		if (intervals != null) {

			List<Long> layers = new ArrayList<>();
			// Keep track of the last layer index used for each group
			int lastLayerIndex = 0;
			// Keep track of the current group
			int currentGroup = intervals.get(0).getGroup();

			// Assign each interval to the first available layer
			for (IntervalT interval : intervals) {
				// Check if we have reached the next group
				if (interval.getGroup() != currentGroup) {
					// Update the current group
					currentGroup = interval.getGroup();
					// Reset the last layer index for the new group
					lastLayerIndex = layers.size();
				}

				boolean placed = false;
				for (int index = lastLayerIndex; index < layers.size(); index++) {
					if (interval.getStartTime() >= layers.get(index)) {
						// Place interval in this layer
						interval.layerIndex = index;
						layers.set(index, interval.getEndTime());
						placed = true;
						break;
					}
				}
				if (!placed) {
					// Create a new layer for this interval
					interval.layerIndex = layers.size();
					layers.add(interval.getEndTime());
				}
			}

			// Calculate the maximum layer index
			maxIndex = layers.size() - 1;

			// Update the preferred size of the Timeline panel
			int newHeight = (maxIndex * (intervalHeight + intervalVerticalGap)) + MARGIN * 2 + tickHeight;
			int newWidth = (int) (maxTimestamp - minTimestamp + 2 * MARGIN);
			setPreferredSize(new Dimension(newWidth, newHeight));
		}
	}


	/**
	 * Calculate tick size for events dynamically.
	 */
	public void calculateDynamicTickSizeForEvents() {

		// Determine the measure for choosing the tick intervals (either average or
		// minimum duration
		int measure = 0;
		int count = 0;

		// TAKE THE AVERAGE GAP
		for (int i = 0; i < events.size() - 1; i++) {
			int gap = (int) (events.get(i + 1).getTime() - events.get(i).getTime());
			if (gap != 0) {
				measure += events.get(i + 1).getTime() - events.get(i).getTime();
				count++;
			}
		}
		measure /= count;
		System.out.println("Event average gap:" + measure);

		// Adjust the tick interval to a rounded value for better readability
		int minTickInterval = 10; // This is an arbitrary value, adjust as needed
		tickInterval = roundToNearestMultiple(measure, minTickInterval);

		// Calculate the scale factor inversely proportional to the average gap
		// The smaller the gap, the larger the scale factor should be
		int minGapThreshold = 50; // This is an arbitrary threshold, adjust as needed
		if (measure < minGapThreshold) {
			xScaleFactor = (double) minGapThreshold / measure;
		}
		if (xScaleFactor < 1) {
			xScaleFactor = 1;
		}

		updatePreferredSize();
	}

	private int roundToNearestMultiple(int value, int multiple) {
		return ((value + multiple - 1) / multiple) * multiple;
	}

	private long roundToNearestMultiple(long value, long multiple) {
		return ((value + multiple - 1) / multiple) * multiple;
	}

	/**
	 * Set the preferred size
	 * 
	 * @param preferredSize the size as a Dimension
	 */
	@Override
	public void setPreferredSize(Dimension preferredSize) {
		int xwidth = (int) ((maxTimestamp - minTimestamp) * xScaleFactor + (2d * MARGIN));
		super.setPreferredSize(new Dimension(xwidth, preferredSize.height));
	}

	/**
	 * Method to change the font size and style of the text labels
	 * 
	 * @param font the font
	 */
	public void setFont(Font font) {
		this.font = font;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2d = (Graphics2D) g;

		// Set the font for the text labels
		g2d.setFont(font);

		// Calculate the reference Y position for the timeline
		int y = getHeight() - MARGIN;

		drawTimeline(g2d, y);
		drawIntervals(g2d);
		drawTicks(g2d, y);
		drawEvents(g2d);
	}

	/**
	 * Draw the timeline on a Graphic2D object
	 * 
	 * @param g2d   the Graphics2D object
	 * @param baseY the reference Y value for the timeline
	 */
	private void drawTimeline(Graphics2D g2d, int baseY) {
		g2d.setColor(Color.BLACK);
		// Draw the line from minTimestamp to maxTimestamp
		int startX = MARGIN;
		int endX = (int) ((maxTimestamp - minTimestamp) * xScaleFactor) + MARGIN;
		g2d.drawLine(startX, baseY, endX, baseY);
	}

	/**
	 * Draw the ticks of the timeline on a Graphic2D object
	 * 
	 * @param g2d   the Graphics2D object
	 * @param baseY the reference Y value for the timeline
	 */
	private void drawTicks(Graphics2D g2d, int baseY) {
		g2d.setColor(Color.BLACK);
		for (long i = minTimestamp; i <= maxTimestamp; i += tickInterval) {
			int x = (int) ((i - minTimestamp) * xScaleFactor) + MARGIN; // Offset by minTimestamp
			g2d.drawLine(x, baseY - tickHeight / 2, x, baseY + tickHeight / 2);
			g2d.drawString("" + i, x - 5, baseY + 20);
		}
	}

	/**
	 * Draw the intervals on a Graphic2D object
	 * 
	 * @param g2d   the Graphics2D object
	 * @param baseY the reference Y value for the timeline
	 */
	private void drawIntervals(Graphics2D g2d) {
		if (intervals != null && intervals.size() >= 1) {
			int previousGroup = -1;
			for (IntervalT interval : intervals) {
				int y = drawSingleInterval(g2d, interval);

				// If this interval is from a new group 
				if (interval.getGroup() != previousGroup) {
					drawGroupLineAndGroupName(g2d, y + (intervalVerticalGap /2), interval.getGroup());
					previousGroup = interval.getGroup();
				}
			}
		}
	}
	
	/**
	 * Draw the group lines on a Graphic2D object
	 * 
	 * @param g2d the Graphics2D object
	 */
	private void drawGroupLineAndGroupName(Graphics2D g2d, int layerY, int group) {
		g2d.setColor(Color.LIGHT_GRAY);
		// Draw the group line
		int startX = MARGIN;
		int endX = (int) ((maxTimestamp - minTimestamp) * xScaleFactor) + MARGIN;
		g2d.drawLine(startX, layerY, endX, layerY);
		
		// Draw the group name
		drawRotate(g2d, startX - (MARGIN/2), layerY, 270, "S" + group);
	}
	
	/**
	 * Draw rotated text
	 * @param g2d
	 * @param x
	 * @param y
	 * @param angle
	 * @param text
	 */
	public static void drawRotate(Graphics2D g2d, double x, double y, int angle, String text) {
		g2d.translate((float) x, (float) y);
		g2d.rotate(Math.toRadians(angle));
		g2d.drawString(text, 0, 0);
		g2d.rotate(-Math.toRadians(angle));
		g2d.translate(-(float) x, -(float) y);
	}

	/**
	 * Draw a single interval
	 * @param g2d a Graphic 2D object
	 * @param interval the interval to be drawn
	 * @return the y position of the interval
	 */
	private int drawSingleInterval(Graphics2D g2d, IntervalT interval) {
		int y = getHeight() - (interval.layerIndex * (intervalHeight + intervalVerticalGap)) - tickHeight - MARGIN;

		// Calculate the visible part of the interval
		long visibleStart = Math.max(interval.getStartTime(), minTimestamp);
		long visibleEnd = Math.min(interval.getEndTime(), maxTimestamp);

		// Convert timestamps to x-coordinates
		int startX = (int) ((visibleStart - minTimestamp) * xScaleFactor) + MARGIN;
		int endX = (int) ((visibleEnd - minTimestamp) * xScaleFactor) + MARGIN;
		int intervalWidth = endX - startX;

		// Assign a color based on the group
		Color color = groupColors[interval.getGroup() % groupColors.length];

		// Draw the interval if it's visible
		if (intervalWidth > 0) {
			g2d.setColor(color);
			g2d.fillRect(startX, y - intervalHeight, intervalWidth, intervalHeight);
			
	        // Draw border around the interval
	        g2d.setColor(INTERVAL_BORDER_COLOR); // Set border color
	        g2d.drawRect(startX, y - intervalHeight, intervalWidth, intervalHeight);
	        
	        
			g2d.setColor(TEXT_COLOR); // Reset color for drawing text
			if (showIntervalLabels) {
				String label = interval.getName();
				FontMetrics metrics = g2d.getFontMetrics(g2d.getFont());
				int labelHeight = metrics.getHeight();
				int textY = y - intervalHeight + (intervalHeight - labelHeight) / 2 + metrics.getAscent();
				g2d.drawString(label, startX + (intervalWidth - metrics.stringWidth(label)) / 2, textY);
			}
		}
		return y;
	}

	/**
	 * Draw the events on a Graphic2D object
	 * 
	 * @param g2d the Graphics2D object
	 */
	private void drawEvents(Graphics2D g2d) {
		if (events != null) {
			for (EventT event : events) {
				drawSingleEvent(g2d, event);
			}
		}
	}

	/**
	 * Draw an event on a Graphic2D object
	 * 
	 * @param g2d the Graphics2D object
	 */
	private void drawSingleEvent(Graphics2D g2d, EventT event) {
		int x = (int) ((event.getTime() - minTimestamp) * xScaleFactor) + MARGIN;
		int y = getHeight() - (event.layerIndex * eventHeight) - tickHeight - MARGIN;
		// if the event is visible
		if (x >= MARGIN) {
			int halfEventMarkSize = (eventMarkSize / 2);
			// Draw the event
			if (event.layerIndex < 1) {
				g2d.fillOval(x - halfEventMarkSize, getHeight() - MARGIN - halfEventMarkSize, eventMarkSize,
						eventMarkSize);
			}
			if (showEventLabels) {
				g2d.drawString(event.getName(), x - 5, y);
//				+ ":" + event.getTime()
			}
		}
	}

	/**
	 * Calculate the positions of events in terms of layers
	 */
	private void calculateEventsPositions() {
		if (events == null || events.isEmpty()) {
			return; // Skip recalculation if not needed
		}

		for (EventT event : events) {
			event.layerIndex = 0; // default layer

			// Check for overlapping events and adjust Y positions
			while (isOverlappingWithAny(event, events)) {
				event.layerIndex += 1;
			}
		}

		// Find the maximum
		maxIndex = 0;
		for (EventT event : events) {
			if (event.layerIndex > maxIndex) {
				maxIndex = event.layerIndex;
			}

		}

		// Update the preferred size of the Timeline panel
		int newHeight = (maxIndex * eventHeight) + MARGIN * 2 + tickHeight;
		int newWidth = (int) (maxTimestamp - minTimestamp + 2 * MARGIN);
		setPreferredSize(new Dimension(newWidth, newHeight));

	}

	/**
	 * Helper method to check if an event overlaps with any other event
	 * 
	 * @param event  an event
	 * @param events the other events
	 * @return true if there is an overlap
	 */
	private boolean isOverlappingWithAny(EventT event, List<EventT> events) {
		for (EventT otherEvent : events) {
			if (otherEvent != event && otherEvent.getTime() == event.getTime()
					&& otherEvent.layerIndex == event.layerIndex) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Retrieves the height of the tick.
	 * 
	 * @return the height of the tick
	 */
	public int getTickHeight() {
		return tickHeight;
	}

	/**
	 * Sets the height of the tick.
	 * 
	 * @param tickHeight the height to set for the tick
	 */
	public void setTickHeight(int tickHeight) {
		this.tickHeight = tickHeight;
		updatePreferredSize();
	}

	/**
	 * Retrieves the height of the interval.
	 * 
	 * @return the height of the interval
	 */
	public int getIntervalHeight() {
		return intervalHeight;
	}

	/**
	 * Sets the height of the interval and updates the preferred size.
	 * 
	 * @param intervalHeight the height to set for the interval
	 */
	public void setIntervalHeight(int intervalHeight) {
		this.intervalHeight = intervalHeight;
		updatePreferredSize();
	}

	/**
	 * Retrieves the vertical gap between intervals.
	 * 
	 * @return the vertical gap between intervals
	 */
	public int getIntervalVerticalGap() {
		return intervalVerticalGap;
	}

	/**
	 * Sets the vertical gap between intervals.
	 * 
	 * @param intervalVerticalGap the vertical gap to set between intervals
	 */
	public void setIntervalVerticalGap(int intervalVerticalGap) {
		this.intervalVerticalGap = intervalVerticalGap;
	}

	/**
	 * Toggles the visibility of interval labels.
	 */
	public void toggleShowIntervalLabels() {
		showIntervalLabels = !showIntervalLabels;
		repaint();
	}

	/**
	 * Toggles the visibility of event labels.
	 */
	public void toggleShowEventLabels() {
		showEventLabels = !showEventLabels;
		repaint();
	}

	/**
	 * Retrieves the scale factor for the X-axis.
	 * 
	 * @return the scale factor for the X-axis
	 */
	public double getXScaleFactor() {
		return xScaleFactor;
	}

	/**
	 * Sets the scale factor for the X-axis.
	 * 
	 * @param xScaleFactor the scale factor to set for the X-axis
	 */
	public void setXScaleFactor(double xScaleFactor) {
		this.xScaleFactor = xScaleFactor;
		updatePreferredSize();
	}

	/**
	 * Sets the minimum timestamp value.
	 * 
	 * @param minTimestamp the minimum timestamp to set
	 */
	public void setMinTimestamp(long minTimestamp) {
		this.minTimestamp = minTimestamp;
		updatePreferredSize();
	}

	/**
	 * Retrieves the minimum timestamp value.
	 * 
	 * @return the minimum timestamp value
	 */
	public long getMinTimestamp() {
		return minTimestamp;
	}

	/**
	 * Sets the maximum timestamp value.
	 * 
	 * @param maxTimestamp the maximum timestamp to set
	 */
	/**
	 * Sets the maximum timestamp value and updates the preferred size.
	 * 
	 * @param maxTimestamp the maximum timestamp to set
	 */
	public void setMaxTimestamp(long maxTimestamp) {
		this.maxTimestamp = maxTimestamp;
		updatePreferredSize();
	}

	/**
	 * Updates the preferred size of the Timeline panel based on the current min and
	 * max timestamps.
	 */
	/**
	 * Updates the preferred size of the Timeline panel based on the current layout
	 * properties.
	 */
	private void updatePreferredSize() {
		// Calculate the new width based on the current min and max timestamps
		int newWidth = (int) ((maxTimestamp - minTimestamp) * xScaleFactor + (2 * MARGIN));

		// Calculate the new height based on the current layout properties
		int newHeight = calculateHeightBasedOnLayout();

		setPreferredSize(new Dimension(newWidth, newHeight));
		revalidate(); // Inform the layout manager to recalculate the layout
		repaint(); // Repaint the component with the new dimensions
	}

	/**
	 * Calculates the height of the Timeline panel based on the current layout
	 * properties.
	 * 
	 * @return the calculated height
	 */
	private int calculateHeightBasedOnLayout() {
		int value = 1;
		if (events != null) {
			value = (maxIndex) * eventHeight + (2 * MARGIN) + tickHeight;
		} else {
			value = (maxIndex) * (intervalHeight + intervalVerticalGap) + (2 * MARGIN) + tickHeight;
		}
		return value;
	}

	/**
	 * Retrieves the maximum timestamp value.
	 * 
	 * @return the maximum timestamp value
	 */
	public long getMaxTimestamp() {
		return maxTimestamp;
	}

	/**
	 * Retrieves the interval between ticks.
	 * 
	 * @return the interval between ticks
	 */
	public int getTickInterval() {
		return tickInterval;
	}

	/**
	 * Sets the interval between ticks.
	 * 
	 * @param tickInterval the interval to set between ticks
	 */
	public void setTickInterval(int tickInterval) {
		this.tickInterval = tickInterval;
	}

	/**
	 * Retrieves the height allocated to an event
	 * 
	 * @return the height allocated to an event
	 */
	public int getEventHeight() {
		return eventHeight;
	}

	/**
	 * Sets the height allocated to an event
	 * 
	 * @param eventHeight the height allocated to an event
	 */
	public void setEventHeight(int eventHeight) {
		this.eventHeight = eventHeight;
		updatePreferredSize();
	}

}
