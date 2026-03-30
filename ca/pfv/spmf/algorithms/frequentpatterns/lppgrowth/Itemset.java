package ca.pfv.spmf.algorithms.frequentpatterns.lppgrowth;


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
 * Copyright Peng Yang  2019
 */
/**
 * This class represents an itemset.
 * 
 * @author Peng yang
 * @see AlgoLPPGrowth
 */
public class Itemset {
    int[] items;
    List<int[]> timeIntervals;


    /**
     *  add m-itemset into memory ( m>1 )
     * @param items
     * @param timeIntervals
     */
	Itemset(int[] items, List<int[]> timeIntervals){
        this.items = items;
        this.timeIntervals = timeIntervals;
    }

    /**
     * Constructor
     * @param itemset an itemset
     * @param timeIntervals a list of time intervals
     */
    Itemset(int itemset,ArrayList<int[]> timeIntervals){
        this.items = new int[]{itemset};
        this.timeIntervals = timeIntervals;
    }


    /**
     * Get the list of time intervals
     * @return the list of time intervals
     */
    public List<int[]> getTimeIntervals() {
        return timeIntervals;
    }


    @Override
    /**
     * Get a string representation of this itemset
     * @return a string
     */
    public String toString() {
        String s = "";
        for(int item:items){
            s = s + item+ " ,";
        }
        s = s.substring(0,s.length()-1);
        s+=" : ";
        for(int[] interval:timeIntervals){
            s = s  +  "[ "+interval[0]+" , "+interval[1]+" ] ";
        }
        return s;
    }
}
