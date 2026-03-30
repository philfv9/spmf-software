package ca.pfv.spmf.algorithms.frequentpatterns.hui_miner;
import java.io.Serializable;
/* This file is copyright (c) 2008-2015 Srikumar Krishnamoorty
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
public class ItemTHUI implements Serializable{
	long twu = 0L;
	int utility = 0;
	 
	public String toString(){
		return String.valueOf(utility);
	}
}