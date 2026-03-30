package ca.pfv.spmf.algorithms.sequentialpatterns.lapin;
/***
 * This is an implementation of an Item-is-exist table used by the LAPIN-SPAM
 * algorithm.
 * 
 * The LAPIN-SPAM algorithm was originally described in this paper:
 * 
 * Zhenlu Yang and Masrau Kitsuregawa. LAPIN-SPAM: An improved algorithm for
 * mining sequential pattern In Proc. of Int'l Special Workshop on Databases For
 * Next Generation Researchers (SWOD'05) in conjunction with ICDE'05, pp. 8-11,
 * Tokyo, Japan, Apr. 2005.
 *
 * Copyright (c) 2008-2013 Philippe Fournier-Viger
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
 * A class to store a position (a sequence id + an itemset id). This will be use
 * to represent the border of a prefix.
 */
class Position {

	/** a sequence id */
	int sid;

	/** an itemset position in the sequence */
	short position;

	/**
	 * Default constructor
	 * 
	 * @param sid      the sequence id
	 * @param position the position as a short (itemset number)
	 */
	public Position(int sid, short position) {
		this.sid = sid;
		this.position = position;
	}

    @Override
    public String toString() {
        return sid + " " + position;
    }
}