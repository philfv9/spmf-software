package ca.pfv.spmf.algorithms.frequentpatterns.tm;
/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger
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
/**
 * This class represents an interval [start, end] used in the TM algorithm.
 * An interval represents a contiguous range of mapped transaction IDs.
 * 
 * @author Philippe Fournier-Viger
 */
public class Interval implements Comparable<Interval> {
    
    /** The start of the interval */
    int start;
    
    /** The end of the interval */
    int end;
    
    /**
     * Constructor
     * @param start the start id
     * @param end the end id
     */
    public Interval(int start, int end) {
        this.start = start;
        this.end = end;
    }
    
    /**
     * Get the start of the interval
     * @return start
     */
    public int getStart() {
        return start;
    }
    
    /**
     * Set the start of the interval
     * @param start the start value
     */
    public void setStart(int start) {
        this.start = start;
    }
    
    /**
     * Get the end of the interval
     * @return end
     */
    public int getEnd() {
        return end;
    }
    
    /**
     * Set the end of the interval
     * @param end the end value
     */
    public void setEnd(int end) {
        this.end = end;
    }
    
    /**
     * Get the length of this interval (number of transactions)
     * @return the length
     */
    public int length() {
        return end - start + 1;
    }
    
    /**
     * Check if this interval overlaps with another
     * @param other the other interval
     * @return true if overlapping
     */
    public boolean overlaps(Interval other) {
        return !(this.end < other.start || other.end < this.start);
    }
    
    /**
     * Check if this interval is contiguous with another (adjacent or overlapping)
     * @param other the other interval
     * @return true if contiguous
     */
    public boolean isContiguousWith(Interval other) {
        return !(this.end + 1 < other.start || other.end + 1 < this.start);
    }
    
    /**
     * Copy constructor
     * @return a copy of this interval
     */
    public Interval copy() {
        return new Interval(this.start, this.end);
    }
    
    @Override
    public int compareTo(Interval other) {
        int cmp = Integer.compare(this.start, other.start);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(this.end, other.end);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Interval other = (Interval) obj;
        return start == other.start && end == other.end;
    }
    
    @Override
    public int hashCode() {
        return 31 * start + end;
    }
    
    @Override
    public String toString() {
        return "[" + start + "," + end + "]";
    }
}