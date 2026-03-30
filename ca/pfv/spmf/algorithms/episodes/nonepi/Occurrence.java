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

/**
 * An occurrence of an episode as defined by the NONEPI algorithm
 * @see AlgoNONEPI
 * @author oualid
 */
public class Occurrence {

    protected long start;
    protected long end;
    protected double prob;
    public Occurrence(long _start, long _end) {
        this.start = _start;
        this.end = _end;
        this.prob = 1;
    }

    public long getStart() {
        return this.start;
    }

    public long getEnd() {
        return this.end;
    }

    public void setProb(double _p){
        this.prob *= _p;
    }

    public double getProb(){
        return this.prob;
    }
    @Override
    public String toString() {
        return "[" + this.start + "," + this.end + "] , "+this.prob;
    }

}
