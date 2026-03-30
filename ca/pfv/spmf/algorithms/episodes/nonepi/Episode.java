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
 * Copyright Oualid Ouarem et al.
 */
package ca.pfv.spmf.algorithms.episodes.nonepi;

import java.util.ArrayList;
import java.util.List;

/**
 * An episode as defined by the NONEPI algorithm
 * @see AlgoNONEPI
 * @author oualid
 */
public class Episode {

    private List<String> events;
    private double support;
    private List<Occurrence> occurrences;

    public Episode() {
        this.events = new ArrayList<>();
        this.support = 0;
        this.occurrences = new ArrayList<>();
    }

    public Episode(List<String> _events) {
        this.events = _events;
        this.support = 0;
        this.occurrences = new ArrayList<>();
    }

    public void increaseSupport() {
        this.support++;
    }

    public void setSupport(double support) {
        this.support = support;
    }

    public double getSupport() {
        return this.support;
    }

    public void add(Occurrence _occurrences) {
        this.occurrences.add(_occurrences);
    }

    public void setOccurrences(List<Occurrence> _occurrences) {
        this.occurrences = _occurrences;
    }

    public List<Occurrence> getOccurrences() {
        return this.occurrences;
    }

    public List<String> getEvents() {
        return this.events;
    }

    public boolean isSubEpisode(Episode episode) {
        int counter = 0;
        List<String> otherEpisode = episode.getEvents();
        if (this.events.size() > episode.getEvents().size()) {
            return false;
        }
        for (String event : this.events) {
            if (event.equals(otherEpisode.get(counter))) {
                counter++;
            }
            if (counter == this.events.size()) {
                return true;
            }
        }
        return false;
    }

    public boolean isPrefix(Episode episode) {
        int counter = 0;
        List<String> otherEpisode = episode.getEvents();
        if (this.isSubEpisode(episode)) {
            for (String e : this.events) {
                if (!e.equals(otherEpisode.get(counter))) {
                    return false;
                }
                counter++;
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean isSuffix(Episode episode) {
        int counter;
        int i;
        List<String> otherEpisode = episode.getEvents();
        if (this.isSubEpisode(episode)) {
            counter = 0;
            i = otherEpisode.size() - this.events.size();
            while (i < this.events.size()) {
                if (!(this.events.get(i).equals(otherEpisode.get(counter)))) {
                    return false;
                }
                i++;
                counter++;
            }
            return true;
        } else {
            return false;
        }
    }

    public int getSize() {
        return this.events.size();
    }
    
    public boolean Equals(Episode epi) {
    	int size = epi.getSize();
    	if (this.getSize() != size)
    		return false;
    	boolean stop = false;
    	int i=0;
    	while(i<epi.getSize() && !stop) {
    		if (!epi.getEvents().get(i).equals(this.getEvents().get(i))) {
    			stop= true;
    		}
    		i++;
    	}
    	if (stop)
    		return false;
    	return true;
    }
    

    @Override
    public String toString() {
        String string = "<";
        int key = 0;
        for (String event : this.events) {
            string = string + event;
            if (key < (this.events.size() - 1)) {
                string = string + "->";
            } else {
                string = string + ">";
            }
            key = key + 1;
        }
        return string;
    }
    
    public String toSPMFString() {
        StringBuffer buffer = new StringBuffer();
        for (String event : this.events) {
        	buffer.append('{');
        	buffer.append(event);
        	buffer.append('}');

        }
        return buffer.toString();
    }
}
