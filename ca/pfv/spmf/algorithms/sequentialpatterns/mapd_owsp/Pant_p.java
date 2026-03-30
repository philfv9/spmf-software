package ca.pfv.spmf.algorithms.sequentialpatterns.mapd_owsp;
/* This file is copyright (c) 2021 Youxi Wu et al.
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
import java.util.LinkedList;
import java.util.Queue;

public class Pant_p {
	public int name; // The corresponding position of node in sequence
	// queue<int> que_pan;
	public Queue<Integer> que_pan = new LinkedList();
}
