
package ca.pfv.spmf.algorithms.episodes.emdo;

import java.util.ArrayList;
import java.util.List;

/*
 * This file is part of the SPMF DATA MINING SOFTWARE *
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the *
 * terms of the GNU General Public License as published by the Free Software *
 * Foundation, either version 3 of the License, or (at your option) any later *
 * version. SPMF is distributed in the hope that it will be useful, but WITHOUT
 * ANY * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Copyright Oualid Ouarem et al. 2025
 */

/**
 * This is a implementation of an episode as used by the EMDO algorithm
 * 
 * @author Oualid Ouarem et al. 2025
 */

public class Episode {
    
    /**
     * The list of event types that form the episode.
     */
    private ArrayList<String> events;

    /**
     * The number of times the episode has occurred in the data.
     */
    private int support;

    /**
     * The list of occurrences where this episode appears in the sequence.
     */
    private List<Occurrence> occurrences;

    /**
     * The expected support of the episode, used for probabilistic or statistical comparison.
     */
    private double expectedSupport;

    /**
     * Default constructor. Initializes an empty episode.
     */
    public Episode() {
        this.events = new ArrayList<>();
        this.support = 0;
        this.occurrences = new ArrayList<>();
    }

    /**
     * Constructor with predefined event list.
     *
     * @param _events the list of event types forming this episode
     */
    public Episode(ArrayList<String> _events) {
        this.events = _events;
        this.support = 0;
        this.occurrences = new ArrayList<>();
    }


    /**
     * Increases the support count of this episode by one.
     */
    public void increaseSupport() {
        this.support++;
    }

    /**
     * Sets the support count of this episode.
     *
     * @param support the support value to set
     */
    public void setSupport(int support) {
        this.support = support;
    }

//    /**
//     * Returns the support count of this episode.
//     *
//     * @return the support value
//     */
//    public int getSupport() {
//        return this.support;
//    }
    
    /**
     * Returns the support count of this episode.
     *
     * @param isExpectedSupport  if true, return the expected support, otherwise the support
     * @return the support value
     */
    public double getSupportAsDouble(boolean isExpectedSupport) {
    	if(isExpectedSupport)
    		return this.expectedSupport;
    	else
    		return this.support;
    }

    /**
     * Adds a new occurrence of this episode.
     *
     * @param _occurrences the occurrence to add
     */
    public void add(Occurrence _occurrences) {
        this.occurrences.add(_occurrences);
    }

    /**
     * Sets the full list of occurrences for this episode.
     *
     * @param _occurrences the list of occurrences to set
     */
    public void setOccurrences(List<Occurrence> _occurrences) {
        this.occurrences = _occurrences;
    }

    /**
     * Returns the list of occurrences of this episode.
     *
     * @return the list of occurrences
     */
    public List<Occurrence> getOccurrences() {
        return this.occurrences;
    }

    /**
     * Returns the list of events in this episode.
     *
     * @return the list of event types
     */
    public ArrayList<String> getEvents() {
        return this.events;
    }
    
    /**
     * Gets the event at a specific index.
     *
     * @param index the index of the event
     * @return the event name at the specified index
     */
    public String getEvent(int index) {
		return this.events.get(index);
    }


    /**
     * Checks if the given event exists in this episode.
     *
     * @param event the event name to check
     * @return true if the event exists, false otherwise
     */
    public boolean Contains(String event) {
    	return this.events.indexOf(event) >= 0;
    }

    /**
     * Returns the size (number of events) of this episode.
     *
     * @return the size of the episode
     */
    public int getSize() {
        return this.events.size();
    }

    /**
     * Checks whether this episode is equal to another, ignoring order of events.
     *
     * @param epi the other episode to compare with
     * @return true if both episodes contain the same events, false otherwise
     */
    public boolean Equals(Episode epi) {
    	int size = epi.getSize();
    	if (this.getSize() != size)
    		return false;
    	boolean stop = false;
    	int i=0;
    	while(i<epi.getSize() && !stop) {
    		if (epi.getEvents().indexOf(this.getEvent(i))<0) {
    			stop= true;
    		}
    		i++;
    	}
    	if (stop)
    		return false;
    	return true;
    }
    
    

    /**
     * Returns a string representation of the episode in angle brackets,
     * with events separated by commas.
     *
     * @return the string representation of the episode
     */
    @Override
    public String toString() {
        String string = "{";
        int key = 0;
        while(key < this.events.size()) {
        //for (String event : this.events) {
            string = string + this.events.get(key);
            if (key < (this.events.size() - 1)) {
                string = string + ",";
            } else {
                string = string + "}";
            }
            key = key + 1;
        }
        return string;
    }

    /**
     * Sets the expected support for this episode.
     *
     * @param expectedSupport the expected support value
     */
    public void setExpectedSupport(double expectedSupport){
        this.expectedSupport = expectedSupport;
    }

    /**
     * Gets the expected support for this episode.
     *
     * @return the expected support value
     */
    public double getExpectedSupport() {
        return expectedSupport;
    }

    /**
     * Checks if this episode is identical to another list of event types.
     * Used for comparing partially constructed episodes.
     *
     * @param event_types the list of event types to compare with
     * @return true if identical, false otherwise
     */
    public boolean isIdenticalWith(List<String> event_types){
        if (this.events.get(this.events.size()-1) == event_types.get(event_types.size()-1))
                return this.isIdenticalWith(event_types.subList(0,event_types.size()-1));
        return false;
    }
    
    /**
     * Outputs the episode in the SPMF episode format with support
     * 
     * Format: 1 2 3 -1  #SUP: 5
     * @param  isExpectedSupport if true, write expected support instead of support
     *
     * @return the episode string in SPMF format
     */
    public String toStringSPMF(boolean isExpectedSupport) {
        StringBuilder builder = new StringBuilder();
        for (String event : events) {
            builder.append(event).append(' ');
        }
        if(isExpectedSupport)
        	builder.append("-1 #SUP: ").append(expectedSupport);
        else
        	builder.append("-1 #SUP: ").append(support);	
        return builder.toString();
    }

}
