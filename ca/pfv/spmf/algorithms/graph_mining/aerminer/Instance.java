package ca.pfv.spmf.algorithms.graph_mining.aerminer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
 * An instance
 * @see AERMiner
 * @author Ganghuan He 2020
 */
public class Instance {

    private SupportPoint childPoint;
    private List<Integer> parentVIds = new ArrayList<>();

    public Instance(SupportPoint childPoint){
        this.childPoint = childPoint;
    }

    public void growParent(int vId){
        this.parentVIds.add(vId);
    }

    public List<Integer> getParentVIds() {
        return parentVIds;
    }

    public SupportPoint getChildPoint() {
        return childPoint;
    }

//        @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//        Instance instance = (Instance) o;
//        return getChildPoint().equals(instance.getChildPoint());
//    }



    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Instance instance = (Instance) o;
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        for(int i = 0; i < this.getParentVIds().size(); i++){
            sb1.append(this.getParentVIds().get(i));
            sb2.append(instance.parentVIds.get(i));
        }
        return getChildPoint().equals(instance.getChildPoint()) &&
                sb1.toString().equals(sb2.toString());
    }


    @Override
    public int hashCode() {
        return Objects.hash(getChildPoint(), getParentVIds());
    }

    @Override
    public String toString() {
        return "Instance{" +
                "childPoint=" + childPoint +
                ", parentVIds=" + parentVIds +
                '}';
    }

    @Override
    public Instance clone() {
        SupportPoint csp = this.getChildPoint();
        Instance cloneInstance = new Instance(new SupportPoint(csp.getTimestamp(),csp.getvId()));
        for(int vId : this.getParentVIds()){
            cloneInstance.growParent(vId);
        }
        return cloneInstance;
    }
}
