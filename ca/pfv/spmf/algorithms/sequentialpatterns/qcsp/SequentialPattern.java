package ca.pfv.spmf.algorithms.sequentialpatterns.qcsp;

import java.util.ArrayList;
import java.util.List;
/* This file is part of the SPMF DATA MINING SOFTWARE
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
 *
 * Copyright (c) Len Feremans
 *
 * Do not remove copyright and license information.
 */
public class SequentialPattern{
	public List<Integer> pattern;
	public SequentialPattern() {
		this.pattern = new ArrayList<Integer>();
	}
	public SequentialPattern(SequentialPattern prefix, Integer item){
		this.pattern = new ArrayList<Integer>(prefix.length() + 1);
		for(int i=0; i<prefix.length();i++) {
			this.pattern.add(prefix.pattern.get(i));
		}
		this.pattern.add(item);
	}
	final public int length() {
		return pattern.size();
	}
}
