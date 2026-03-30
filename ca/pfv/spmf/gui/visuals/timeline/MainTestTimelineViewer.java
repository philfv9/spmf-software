package ca.pfv.spmf.gui.visuals.timeline;

import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

/**
 * Class to test the TimelineViewer of SPMF
 * @author Philippe Fournier-Viger
 */
public class MainTestTimelineViewer {
	public static void main(String[] args) {
		testEvent1();
		testInterval1();
		testInterval2();
	}

	private static void testEvent1() {
		// Sample events and intervals for the timeline
		List<EventT> events = new ArrayList<>();
		events.add(new EventT("Event 1", 50));
		events.add(new EventT("Event 2", 150));
		events.add(new EventT("Event 3", 250));
		events.add(new EventT("Event 4", 250));
		events.add(new EventT("Event 5", 250));
		events.add(new EventT("Event 6", 300));
		events.add(new EventT("Event 7", 250));
		events.add(new EventT("Event 8", 250));
		events.add(new EventT("Event 3", 250));
		events.add(new EventT("Event 4", 250));
		events.add(new EventT("Event 5", 250));
		events.add(new EventT("Event 6", 300));
		events.add(new EventT("Event 7", 250));
		events.add(new EventT("Event 8", 250));
		events.add(new EventT("Event 9", 300));
		events.add(new EventT("Event 9", 300));
		events.add(new EventT("Event 3", 250));
		events.add(new EventT("Event 4", 250));
		events.add(new EventT("Event 5", 250));
		events.add(new EventT("Event 6", 300));
		events.add(new EventT("Event 7", 250));
		events.add(new EventT("Event 8", 250));
		events.add(new EventT("Event 9", 300));
		events.add(new EventT("Event 34", 1300));
		
		// Run the timeline viewer
		SwingUtilities.invokeLater(
				() -> new TimelineViewer(true, events, null));
	}

	private static void testInterval1() {

		List<IntervalT> intervals = new ArrayList<>();
		intervals.add(new IntervalT("0-A", 100, 200, 0));
		intervals.add(new IntervalT("0-B", 210, 230, 0));
		intervals.add(new IntervalT("0-B", 220, 350, 0));
		intervals.add(new IntervalT("1-B", 90, 350, 1));
		intervals.add(new IntervalT("1-B", 40, 150, 1));
		intervals.add(new IntervalT("1-B", 240, 320, 1));
		intervals.add(new IntervalT("2-B", 10, 800, 2));
		intervals.add(new IntervalT("2-B", 10, 800, 2));
		intervals.add(new IntervalT("2-B", 10, 800, 2));
		intervals.add(new IntervalT("3-B", 10, 800, 3));
		intervals.add(new IntervalT("3-A", 100, 200, 3));
		intervals.add(new IntervalT("3-B", 210, 230, 3));
		intervals.add(new IntervalT("3-B", 220, 350, 3));

		// Run the timeline viewer
		SwingUtilities.invokeLater(
				() -> new TimelineViewer(true, null, intervals));
	}
	
	private static void testInterval2() {

		List<IntervalT> intervals = new ArrayList<>();
		intervals.add(new IntervalT("A", 100, 200, 0));
		intervals.add(new IntervalT("B", 210, 230, 0));
		intervals.add(new IntervalT("B", 220, 350, 0));
		intervals.add(new IntervalT("B", 90, 350, 0));
		intervals.add(new IntervalT("B", 40, 150, 0));
		intervals.add(new IntervalT("CDE", 152, 160, 0));
		intervals.add(new IntervalT("B", 240, 320, 0));
		intervals.add(new IntervalT("B", 10, 800, 0));
		intervals.add(new IntervalT("B", 10, 800, 0));

		// Run the timeline viewer
		SwingUtilities.invokeLater(
				() -> new TimelineViewer(true, null, intervals));
	}
}