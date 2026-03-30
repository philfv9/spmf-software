package ca.pfv.spmf.algorithms.sequentialpatterns.fasttirp;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger, Yuechun Li
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
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
import java.util.ArrayList;
import java.util.List;

/**
 * A line from the table of the vertical database
 * 
 * @author Philippe Fournier-Viger, Yuechun Li, 2023
 */
public class Line {
	/** sequence id */
	final int sid;

	/** interval id */
	final int eid;

	/** start time */
	final int start;

	/** end time */
	final int end;

	/** the temporal relation */
	final List<Relation> relations;

	/** source intervals */
	final List<TimeInterval> sourceIntervals;

	/**
	 * Constructor
	 * 
	 * @param sid            sequence id
	 * @param eid            position in the sequence
	 * @param start          the start time
	 * @param end            the end time
	 * @param sourceInterval a time interval
	 */
	public Line(int sid, int eid, int start, int end, TimeInterval sourceInterval) {
		this.sid = sid;
		this.eid = eid;
		this.start = start;
		this.end = end;
		relations = new ArrayList<>();
		sourceIntervals = new ArrayList<>();
		sourceIntervals.add(sourceInterval);
	}

	/**
	 * Constructor
	 * 
	 * @param sid   sequence id
	 * @param eid   position in the sequence
	 * @param start the start time
	 * @param end   the end time
	 */
	public Line(int sid, int eid, int start, int end) {
		this.sid = sid;
		this.eid = eid;
		this.start = start;
		this.end = end;
		relations = new ArrayList<>();
		sourceIntervals = new ArrayList<>();
	}

	/**
	 * Get a string representation of this object
	 * 
	 * @return a string
	 */
	public String toString() {
		StringBuffer buffer = new StringBuffer(100);
		buffer.append(" #SID: ").append(sid).append(" \t #EID: ").append(eid).append(" \t #START: ").append(start)
				.append(" \t #END: ").append(end).append(" \t #SOURCEINTERVALS: ").append(toStringForSourceIntervals())
				.append(" \t #RELATIONS: ").append(toStringForRelations()).append(" \t #DURATION: ")
				.append(end - start);
		return buffer.toString();
	}

	/**
	 * Get a string representations of the source intervals in this line
	 * 
	 * @return a string
	 */
	private String toStringForSourceIntervals() {
		StringBuffer buffer = new StringBuffer();

		// For each time interval
		for (TimeInterval ti : sourceIntervals) {
			buffer.append(ti.toString());
		}
		return buffer.toString();
	}

	/**
	 * Get a string representations of the relations in this line
	 * 
	 * @return a string
	 */
	private String toStringForRelations() {
		StringBuffer buffer = new StringBuffer();

		// For each relation
		for (Relation relation : relations) {
			buffer.append(relation);
			buffer.append(" ");
		}
		return buffer.toString();
	}
}
