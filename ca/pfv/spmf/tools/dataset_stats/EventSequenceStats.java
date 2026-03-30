package ca.pfv.spmf.tools.dataset_stats;

/* This file is copyright (c) 2008-2014 Philippe Fournier-Viger
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import ca.pfv.spmf.input.event_sequence.EventSequence;

/**
 * This class read an event sequence with time and calculates statistics
 * about this event sequence, then it prints the statistics to the console.
 * <br/><br/>
 * In this version this class reads the database into memory before calculating the
 * statistics. It could be optimized.

* @author Philippe Fournier-Viger
 */
public class EventSequenceStats {


	/**
	 * This method generates statistics for an event sequence (a file)
	 * @param path the path to the file
	 * @throws IOException  exception if there is a problem while reading the file.
	 */
	public void runAlgorithm(String path) throws IOException {

		/////////////////////////////////////
		//  (1) First we will read the transaction database into memory.
		// (actually, we don't really need to read it into memory because it
		//  just require a single pass, but the code is more simple like that
		//  - it could be optimized, if necessary).
		///////////////////////////////////	
		
		EventSequence database = new EventSequence();
		database.loadFile(path);

		/////////////////////////////////////
		//  We finished reading the database into memory.
		//  We will calculate statistics on this transaction database.
		///////////////////////////////////

		System.out.println("============  EVENT SEQUENCE STATS ==========");
		System.out.println("Number of events : " + database.size());
		System.out.println("Number of distinct event types : " + database.getUniqueEvents().size());
		System.out.println("Max item id : " + database.getMaxItemID());
		
		Set<Long> timestamps = new HashSet<Long>();
		for(int i = 0; i < database.size(); i++) {
			timestamps.add(database.get(i).getTimestamp());
		}
		

		System.out.println("Number of distinct timestamp: " + timestamps.size());
		System.out.println("Min timestamp : " + database.getMinTimestamp());
		System.out.println("Max timestamp: " + database.getMaxTimestamp());
		System.out.println("Avg. number of events per timestamp: " + (database.size() / (double) timestamps.size()));

	}

}
