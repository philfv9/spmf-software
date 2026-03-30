package ca.pfv.spmf.algorithms.graph_mining.aerminer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
/* This file is copyright (c) 2020 by Ganghuan He
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
 * Several support points.
 * @see AERMiner
 * @author Ganghuan He 2020
 */
public class SupportPoints {
    private Map<Integer, Set<Integer>> supportPoints;
    private int size;

    public SupportPoints(){
        this.supportPoints = new HashMap<>();
        size = 0;
    }


    public void addPoint(int timestamp,int vId){
        Set<Integer> vIdSet = supportPoints.get(timestamp);
        if (vIdSet == null) {
            vIdSet = new HashSet<>();
            supportPoints.put(timestamp, vIdSet);
        }
        vIdSet.add(vId);
        size++;
    }


    public Map<Integer, Set<Integer>> getSupportPoints() {
        return supportPoints;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("#SUP:").append(size);
        sb.append("\nsupporting points:\n");
        for (Entry<Integer, Set<Integer>> entry : supportPoints.entrySet()) {
        	Integer timestamp = entry.getKey();
        	
            sb.append("[").append(timestamp).append("] {");
            for (int vId : supportPoints.get(timestamp)) {
                sb.append(vId).append(",");
            }
            sb.deleteCharAt(sb.length()-1);
            sb.append("}").append("\n");
        }
        return sb.toString();
    }
    public int getSize() {
        return size;
    }

}






