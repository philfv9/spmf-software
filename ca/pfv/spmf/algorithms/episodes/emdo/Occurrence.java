package ca.pfv.spmf.algorithms.episodes.emdo;

import java.util.ArrayList;
import java.util.Collections;
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
 * This class represents an occurrence of an episode pattern in a sequence, 
 * used by the EMDO algorithm. An occurrence is defined by its list of events, 
 * their timestamps, associated probabilities, and the total probability.
 * 
 * Each occurrence has a start time, end time, and provides utility methods 
 * to query, update, and manipulate the underlying event and probability data.
 * 
 * @author Oualid Ouarem et al. 2025
 */
public class Occurrence {

    /**
     * The starting timestamp of the occurrence (earliest timestamp among events).
     */
    private long start;

    /**
     * The ending timestamp of the occurrence (latest timestamp among events).
     */
    private long end;

    /**
     * The total probability of the occurrence (usually the product of individual event probabilities).
     */
    private double probability;

    /**
     * The list of individual probabilities associated with each event in the occurrence.
     */
    private List<Double> probabilities;

    /**
     * The list of timestamps corresponding to each event in the occurrence.
     */
    private List<Integer> timestamps;

    /**
     * The list of event names (or types) in the occurrence.
     */
    private List<String> events;

    /**
     * Constructor. Initializes an empty occurrence with probability 1.
     */
    public Occurrence() {
        this.start = 0;
        this.end = 0;
        this.probability = 1d;//the product

        this.events = new ArrayList<>();
        this.timestamps = new ArrayList<>();
        this.probabilities = new ArrayList<>();
    }

    /**
     * Computes the product of a list of probabilities.
     *
     * @param p the list of probabilities
     * @return the product of all probabilities in the list
     */
    public double product(List<Double> p) {
        double _product = 1;
        for (int i = 0; i < p.size(); i++) {
            _product = _product * p.get(i);
        }
        return _product;
    }

    /**
     * Returns the timestamp corresponding to a given event.
     *
     * @param _event the event name
     * @return the timestamp of the event
     */
    public int getTimeStamp(String _event) {

        return this.timestamps.get(events.indexOf(_event));
    }

    /**
     * Gets the probability at a given index.
     *
     * @param index the index in the probability list
     * @return the probability if the index is valid, otherwise -1
     */
    public double getProbability(int index) {
        if (index >= this.probabilities.size()) {
            return -1;
        }
        return this.probabilities.get(index);
    }

    /**
     * Returns all timestamps of this occurrence.
     *
     * @return list of all timestamps
     */
    public List<Integer> allTimeStamps() {
        return this.timestamps;
    }

    /**
     * Returns the number of events in this occurrence.
     *
     * @return the number of events
     */
    public int length() {
        return this.events.size();
    }

    /**
     * Adds a timestamp to the list of timestamps.
     *
     * @param t the timestamp to add
     */
    public void insertTimeStamp(int t) {
        this.timestamps.add(t);
    }

    /**
     * Adds an event to the list of events.
     *
     * @param event_type the name of the event
     */
    public void addEvent(String event_type) {
        this.events.add(event_type);
    }

    /**
     * Gets the list of event names.
     *
     * @return the list of events
     */
    public List<String> getEvents() {
        return this.events;
    }

    /**
     * Sets the list of event names.
     *
     * @param _events the list of event names
     */
    public void setEvents(List<String> _events) {
        this.events = _events;
    }

    /**
     * Sets the list of timestamps.
     *
     * @param _timeStamps the list of timestamps
     */
    public void setTimeStamps(List<Integer> _timeStamps) {

        this.timestamps = _timeStamps;
    }

    /**
     * Tests whether two occurrences are distinct or not
     *
     * @param _occurrence the occurrence to be tested with the one who call the
     * method
     * @return true if the two occurrences are distinct, false otherwise
     */
    public boolean isDistinct(List<Integer> _occurrence) {
        List<Integer> original = new ArrayList<>(this.timestamps);
        Collections.copy(original, this.timestamps);
        List<Integer> selection = new ArrayList<>(_occurrence);
        Collections.copy(selection, _occurrence);
        selection.removeAll (original);
        return selection.size() == _occurrence.size();
    }

    /**
     * Given a timestamp, this method tests if it exists in the vector of
     * timestamps of this occurrence
     *
     * @param timestamp the time stamp of an event
     * @return boolean whether that timestamp already exists in the timestamps
     * vector or not
     */
    public boolean Contains(int timestamp) {
        return this.timestamps.contains(timestamp);
    }

    /**
     * Sets the start time.
     *
     * @param start the start time
     */
    public void setStart(long start) {
        this.start = start;
    }

    /**
     * Gets the earliest timestamp in this occurrence and updates the start time.
     *
     * @return the earliest timestamp
     */
    public long getStart() {
        long min = this.timestamps.get(0);
        for (int index = 1; index < this.timestamps.size(); index++) {
            if (this.timestamps.get(index) <= min) {
                min = this.timestamps.get(index);
            }
        }
        this.start = min;
        return min;
    }

    /**
     * Sets the end time.
     *
     * @param end the end time
     */
    public void setEnd(long end) {
        this.end = end;
    }

    /**
     * Multiplies the current total probability with a given value.
     *
     * @param prob the probability value to multiply with
     */
    public void setProbabilityWith(double prob) {
        this.probability = this.probability * prob;
    }
    
    
    /**
     * Sets the total probability of this occurrence.
     *
     * @param prob the probability value to set
     */
    public void setProbability(double prob){
        this.probability = prob;
    }

    /**
     * Returns the probability associated with a given event.
     *
     * @param event the event name
     * @return the probability of the event
     */
    public double getProbability(String event) {
        return this.probabilities.get(this.events.indexOf(event));
    }

    /**
     * Gets the total probability of this occurrence.
     *
     * @return the total probability
     */
    public double getProbability() {
        return this.probability;
    }

    /**
     *
     * @param prob a new probability to add into the set of events of the
     * occurrence
     */
    public void insertProb(double prob) {
        this.probabilities.add(prob);
        this.setProbability(prob);
    }

    /**
     * Sets the list of individual event probabilities and updates total probability.
     *
     * @param probs the list of probabilities to insert
     */
    public void setProbabilities(List<Double> probs) {
        this.probabilities = probs;
        this.probability = product(probs);
    }

    /**
     * Gets the list of individual event probabilities.
     *
     * @return the list of probabilities
     */
    public List<Double> getProbabilities() {
        return this.probabilities;
    }

    /**
     * Gets the latest timestamp in this occurrence and updates the end time.
     *
     * @return the latest timestamp
     */
    public long getEnd() {
        long max = this.timestamps.get(0);
        for (int index = 1; index < this.timestamps.size(); index++) {
            if (this.timestamps.get(index) >= max) {
                max = this.timestamps.get(index);
            }
        }
        this.end = max;
        return max;
    }

    /**
     * Returns a string representation of the occurrence,
     * listing event names and their timestamps, followed by the total probability.
     *
     * @return the string representation of the occurrence
     */
    @Override
    public String toString() {
        String str = "";
        for (int i = 0; i < events.size(); ++i) {
            str = str + "(" + this.events.get(i) + " , " + String.valueOf(this.timestamps.get(i)) + ")";
        }
        str = str + " #PROB " + String.valueOf(this.probability);

        return str;
    }
}
