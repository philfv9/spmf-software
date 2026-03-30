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
 * A core pattern as used by the AER Miner algorithm
 * @see AERMiner
 * @author Ganghuan He 2020
 */
public class CorePattern implements Cloneable{
    // pattern size
    private int size;
    // the resulted trend attribute
    private int childAttr;
    // causing attributes
    private List<Integer> parentAttr = new ArrayList<>();


    /**
     * This method construct core pattern using resulted trend attribute(in the next timestamp of causing)
     * @param attribute resulted trend attribute
     */
    public CorePattern(int attribute){
        childAttr = attribute;

        size = 1;
    }

    /**
     * This method is used for growing core pattern by try to adding causing attribute
     * @param attr causing attribute
     */
    public void growParentAttr(int attr){
        parentAttr.add(attr);
        size++;
    }


    /**
     * delete last attribute of parent list.
     */
    public void deleteLastAttr(){
        if(parentAttr.size()>0) {
            parentAttr.remove(parentAttr.size() - 1);
            size--;
        }
    }

    // return last attribute of parent list.
    public int getLastAttr(){
        return parentAttr.size()==0?0:parentAttr.get(parentAttr.size()-1);
    }
    public int getSize() {
        return size;
    }

    public int getChildAttr() {
        return childAttr;
    }

    public List<Integer> getParentAttr() {
        return parentAttr;
    }

    @Override
    public String toString() {
        return "CorePattern{" +
                " childAttr=" + childAttr +
                ", parentAttr=" + parentAttr +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CorePattern pattern = (CorePattern) o;
        return getSize() == pattern.getSize() &&
                getChildAttr() == pattern.getChildAttr() &&
                getParentAttr().containsAll(pattern.getParentAttr());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSize(), getChildAttr(), getParentAttr());
    }



    // deep clone for Core pattern
    public CorePattern clone(){
        CorePattern p = new CorePattern(this.getChildAttr());
        if (this.getParentAttr() == null) parentAttr = new ArrayList<>();
        for(int attr:this.getParentAttr()){
            p.growParentAttr(attr);
        }
        return p;
    }

}
