package ca.pfv.spmf.algorithms.sequentialpatterns.fasttirp;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger, Yuechun Li
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
 * The temporal relations (b = before, m = meet, o = overlap, f = finishes, s =
 * start, l = left contain, c = contains, e = equals,
 * 
 * We also add a new relationship: N = no relationship (for example because of
 * the maxGap)
 * 
 * @author Philippe Fournier-Viger, Yuechun Li, 2023
 */
public enum Relation {
	B, M, O, F, S, L, C, E, N
}
