/* This file is copyright (c) 2021 Mourad Nouioua et al.
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
* 
* Do not remove the copyright and license information from this file.
*/
package ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.chuqiminer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.EnumCombination;
import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.QItemTrans;
import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.Qitem;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 *
 * @author MouRad
 */
public class CombineCHUQI_Miner {
/** minimum utility threshold */
        private long minUtil;
    
        /** combining method */
	private EnumCombination combiningMethod;
        
        /** coefficient */
	private int coefficient;  
        
        /**rangeHUQI */
        private int rangeHUQI;  
        public int constructComb=0;
        
    public CombineCHUQI_Miner(){
    
    }
    
    public CombineCHUQI_Miner(EnumCombination combiningMethod,int coefficient){
    this.combiningMethod=combiningMethod;
    this.coefficient=coefficient;
    }
    /**
     * @return the combiningMethod
     */
    public EnumCombination getCombiningMethod() {
        return combiningMethod;
    }

    /**
     * @param combiningMethod the combiningMethod to set
     */
    public void setCombiningMethod(EnumCombination combiningMethod) {
        this.combiningMethod = combiningMethod;
    }

    /**
     * @return the coefficient
     */
    public int getCoefficient() {
        return coefficient;
    }

    /**
     * @param coefficient the coefficient to set
     */
    public void setCoefficient(int coefficient) {
        this.coefficient = coefficient;
    }

    /**
     * @return the rangeHUQI
     */
    public int getRangeHUQI() {
        return rangeHUQI;
    }

    /**
     * @param rangeHUQI the rangeHUQI to set
     */
    public void setRangeHUQI(int rangeHUQI) {
        this.rangeHUQI = rangeHUQI;
    }
    
    public void setRangeHUQItoZero() {
        this.rangeHUQI = 0;
    }
    
    /**
     * @return the minUtil
     */
    public long getMinUtil() {
        return minUtil;
    }

    /**
     * @param minUtil the minUtil to set
     */
    public void setMinUtil(long minUtil) {
        this.minUtil = minUtil;
    }
    
    
  /**
	 * Method to construct the utility list of an itemset
	 * 
	 * @param ulQitem1 the utility list of a qitem
	 * @param ulQitem2 the utility list of another qitem
	 * @return the resulting utility list
	 */
        public QUtilityListCorr constructForCombine(QUtilityListCorr ulQitem1,QUtilityListCorr ulQitem2){
            constructComb++;
            QUtilityListCorr result=new QUtilityListCorr(new Qitem(ulQitem1.getSingleItemsetName().getItem(),ulQitem1.getSingleItemsetName().getQteMin(),ulQitem2.getSingleItemsetName().getQteMax()),0);
            
            BitSet  res=new BitSet();
            res=(BitSet) ulQitem1.getBitSet().clone();
            res.or(ulQitem2.getBitSet());
            
            int index_in_x=0;
            int index_in_y=0;
            
               
            for (int i = res.nextSetBit(0); i >= 0; i = res.nextSetBit(i+1)) {
                   if(ulQitem1.getBitSet().get(i)==true&&ulQitem2.getBitSet().get(i)==true){
                        result.addTrans(new QItemTrans(ulQitem1.getTransactions().get(index_in_x).getTid(),ulQitem1.getTransactions().get(index_in_x).getEu()+ulQitem2.getTransactions().get(index_in_y).getEu(),ulQitem1.getTransactions().get(index_in_x).getRu()+ulQitem2.getTransactions().get(index_in_y).getRu()));       
                        index_in_x++;
                        index_in_y++;
                   }
                    else if (ulQitem1.getBitSet().get(i)==true){
                        result.addTrans(new QItemTrans(ulQitem1.getTransactions().get(index_in_x).getTid(),ulQitem1.getTransactions().get(index_in_x).getEu(),ulQitem1.getTransactions().get(index_in_x).getRu()));    
                        index_in_x++;
                    }
                    else {
                       result.addTrans(new QItemTrans(ulQitem2.getTransactions().get(index_in_y).getTid(),ulQitem2.getTransactions().get(index_in_y).getEu(),ulQitem2.getTransactions().get(index_in_y).getRu()));
                       index_in_y++;
                    }
            }       
            MemoryLogger.getInstance().checkMemory();
   
    return result;
        }  
        
         
/**
	 * The combine all combination method
	 * 
	 * @param prefix               a prefix of an itemset
	 * @param prefixLength         the length of the prefix
	 * @param candidateList        a list of candidate qitems
	 * @param qItemNameList        another list of qitems
	 * @param mapItemToUtilityList a map of qitems to utility list
	 * @param hwQUI                another list of qitems
	 * @throws IOException if error while reading or writing to file
	 */
        public void combineAll(Qitem [] prefix,int prefixLength,ArrayList<Qitem> candidateList,ArrayList<Qitem> qItemNameList,Hashtable<Qitem, QUtilityListCorr> mapItemToUtilityList,ArrayList<Qitem> hwQUI,BufferedWriter writer_hqui) throws IOException{
            // delete non necessary candidate q-items
            int s=1;
            while (s<candidateList.size()-1){
                if(((candidateList.get(s).getQteMin()==candidateList.get(s-1).getQteMax()+1)&&(candidateList.get(s).getItem()==candidateList.get(s-1).getItem()))||((candidateList.get(s).getQteMax()==candidateList.get(s+1).getQteMin()-1)&&(candidateList.get(s).getItem()==candidateList.get(s+1).getItem())))
                    s++;
                else candidateList.remove(s);
            }
            if (candidateList.size()>2){
                if ((candidateList.get(candidateList.size()-1).getQteMin()!=candidateList.get(candidateList.size()-2).getQteMax()+1)||(candidateList.get(candidateList.size()-2).getItem()!=candidateList.get(candidateList.size()-1).getItem()))
                    candidateList.remove(candidateList.size()-1);
            }
            System.out.println("in Combine All candidateList size "+candidateList.size());
            // make the combination process
            Map <Qitem,QUtilityListCorr> mapRangeToUtilityList=new HashMap<Qitem,QUtilityListCorr>();

            int count;
            for (int i=0;i<candidateList.size();i++){
                int currentItem=candidateList.get(i).getItem();
                mapRangeToUtilityList.clear();
                count=1;
                for (int j=i+1;j<candidateList.size();j++){
                    int nextItem=candidateList.get(j).getItem();
                    if (currentItem!=nextItem)
                        break;
                    else{
                        QUtilityListCorr res;

                        if (j==i+1){

                            if (candidateList.get(j).getQteMin()!=candidateList.get(i).getQteMax()+1)
                                break;

                            res=constructForCombine(mapItemToUtilityList.get(candidateList.get(i)),mapItemToUtilityList.get(candidateList.get(j)));
                            count++;
                            if (count >coefficient)
                                break;

                            mapRangeToUtilityList.put(res.getSingleItemsetName(), res);    
                            if (res.getSumIutils()>getMinUtil()){
                                rangeHUQI++;
                                writeOut(prefix, prefixLength, res.getSingleItemsetName(), res.getSumIutils(),1, writer_hqui);                        
                                hwQUI.add(res.getSingleItemsetName());
                                mapItemToUtilityList.put(res.getSingleItemsetName(), res);
                                int site = qItemNameList.indexOf(candidateList.get(j));
                                qItemNameList.add(site, res.getSingleItemsetName());
                            }   
                        }
                        else{   
                            if (candidateList.get(j).getQteMin()!=candidateList.get(j-1).getQteMax()+1)
                                break;
                            Qitem qItem1=new Qitem(currentItem,candidateList.get(i).getQteMin(),candidateList.get(j-1).getQteMax());
                            QUtilityListCorr ulQitem1=mapRangeToUtilityList.get(qItem1);   
                            res=constructForCombine(ulQitem1,mapItemToUtilityList.get(candidateList.get(j)));   
                            count++;
                            if (count >coefficient) 
                                    break;
                            mapRangeToUtilityList.put(res.getSingleItemsetName(), res);
                            if (res.getSumIutils()>getMinUtil()){
                                rangeHUQI++;
                                writeOut(prefix, prefixLength, res.getSingleItemsetName(), res.getSumIutils(),1,writer_hqui);
                                hwQUI.add(res.getSingleItemsetName());
                                mapItemToUtilityList.put(res.getSingleItemsetName(), res);
                                int site = qItemNameList.indexOf(candidateList.get(j));
                                qItemNameList.add(site, res.getSingleItemsetName());
                            }    
                        }
                    }
                }
            }
            MemoryLogger.getInstance().checkMemory();
        }
        
        /**
	 * The combine min combination method
	 * 
	 * @param prefix               a prefix of an itemset
	 * @param prefixLength         the length of the prefix
	 * @param candidateList        a list of candidate qitems
	 * @param qItemNameList        another list of qitems
	 * @param mapItemToUtilityList a map of qitems to utility list
	 * @param hwQUI                another list of qitems
	 * @throws IOException if error while reading or writing to file
	 */
        public void combineMin(Qitem [] prefix,int prefixLength,ArrayList<Qitem> candidateList,ArrayList<Qitem> qItemNameList,Hashtable<Qitem, QUtilityListCorr> mapItemToUtilityList,ArrayList<Qitem> hwQUI,BufferedWriter writer_hqui) throws IOException{

            // delete non necessary candidate q-items
            int s=1;
            while (s<candidateList.size()-1){
                if(((candidateList.get(s).getQteMin()==candidateList.get(s-1).getQteMax()+1)&&(candidateList.get(s).getItem()==candidateList.get(s-1).getItem()))||((candidateList.get(s).getQteMax()==candidateList.get(s+1).getQteMin()-1)&&(candidateList.get(s).getItem()==candidateList.get(s+1).getItem())))
                    s++;
                else candidateList.remove(s);
            }
            if (candidateList.size()>2){
                if ((candidateList.get(candidateList.size()-1).getQteMin()!=candidateList.get(candidateList.size()-2).getQteMax()+1)||(candidateList.get(candidateList.size()-2).getItem()!=candidateList.get(candidateList.size()-1).getItem()))
                    candidateList.remove(candidateList.size()-1);
            }

            // make the combination process
            int count;
            ArrayList<Qitem> temporaryArrayList= new ArrayList<Qitem>();
            Map <Qitem,QUtilityListCorr> temporaryMap=new HashMap<Qitem,QUtilityListCorr>();
            Map <Qitem,QUtilityListCorr> mapRangeToUtilityList=new HashMap<Qitem,QUtilityListCorr>();

            for (int i=0;i<candidateList.size();i++){
                int currentItem=candidateList.get(i).getItem();
                mapRangeToUtilityList.clear();
                count=1;
                for (int j=i+1;j<candidateList.size();j++){
                    int nextItem=candidateList.get(j).getItem();
                    if (currentItem!=nextItem)
                        break;

                    else{
                        QUtilityListCorr res;
                        if (j==i+1){
                        if (candidateList.get(j).getQteMin()!=candidateList.get(i).getQteMax()+1)
                                break;
                            res=constructForCombine(mapItemToUtilityList.get(candidateList.get(i)),mapItemToUtilityList.get(candidateList.get(j)));
                            count++;
                            if (count >coefficient)
                                break;
                            mapRangeToUtilityList.put(res.getSingleItemsetName(), res);
                            if (res.getSumIutils()>getMinUtil()){
                                if ((temporaryArrayList.isEmpty())||(res.getSingleItemsetName().getItem()!=temporaryArrayList.get(temporaryArrayList.size()-1).getItem())||(res.getSingleItemsetName().getQteMax()>temporaryArrayList.get(temporaryArrayList.size()-1).getQteMax())){
                                        temporaryArrayList.add(res.getSingleItemsetName());
                                        temporaryMap.put(res.getSingleItemsetName(),res);
                                }else {
                                 temporaryMap.remove(temporaryArrayList.get(temporaryArrayList.size()-1));
                                        temporaryArrayList.remove(temporaryArrayList.size()-1);
                                        temporaryArrayList.add(res.getSingleItemsetName());
                                        temporaryMap.put(res.getSingleItemsetName(), res);
                                };
                                break;
                            }
                        }else{
                            if (candidateList.get(j).getQteMin()!=candidateList.get(j-1).getQteMax()+1)
                                break;
                            Qitem qItem1=new Qitem(currentItem,candidateList.get(i).getQteMin(),candidateList.get(j-1).getQteMax());
                            QUtilityListCorr ulQitem1=mapRangeToUtilityList.get(qItem1);
                            res=constructForCombine(ulQitem1,mapItemToUtilityList.get(candidateList.get(j)));
                            count++;
                            if (count >coefficient) 
                                    break;
                            mapRangeToUtilityList.put(res.getSingleItemsetName(), res);
                            if (res.getSumIutils()>getMinUtil()){
                                if ((temporaryArrayList.isEmpty())||(res.getSingleItemsetName().getItem()!=temporaryArrayList.get(temporaryArrayList.size()-1).getItem())||(res.getSingleItemsetName().getQteMax()>temporaryArrayList.get(temporaryArrayList.size()-1).getQteMax())){   
                                    temporaryArrayList.add(res.getSingleItemsetName());
                                    temporaryMap.put(res.getSingleItemsetName(),res);  
                                }else{
                                    temporaryMap.remove(temporaryArrayList.get(temporaryArrayList.size()-1));
                                    temporaryArrayList.remove(temporaryArrayList.size()-1);
                                    temporaryArrayList.add(res.getSingleItemsetName());
                                    temporaryMap.put(res.getSingleItemsetName(), res);

                                }

                                 break;
                             }
                        }
                    }
                }
            }
            for (int k=0;k<temporaryArrayList.size();k++){
                Qitem currentQitem=temporaryArrayList.get(k);
                mapItemToUtilityList.put(currentQitem,temporaryMap.get(currentQitem));
                rangeHUQI++;
                writeOut(prefix, prefixLength, currentQitem, temporaryMap.get(currentQitem).getSumIutils(),1,writer_hqui);
                hwQUI.add(currentQitem);
                Qitem q=new Qitem(currentQitem.getItem(),currentQitem.getQteMax());
                int site = qItemNameList.indexOf(q);
                qItemNameList.add(site, currentQitem);
            }        
            temporaryArrayList.clear();
            temporaryMap.clear();
            MemoryLogger.getInstance().checkMemory();
        }

        /**
	 * The combine max combination method
	 * 
	 * @param prefix               a prefix of an itemset
	 * @param prefixLength         the length of the prefix
	 * @param candidateList        a list of candidate qitems
	 * @param qItemNameList        another list of qitems
	 * @param mapItemToUtilityList a map of qitems to utility list
	 * @param hwQUI                another list of qitems
	 * @throws IOException if error while reading or writing to file
	 */
        public void combineMax( Qitem [] prefix,int prefixLength,ArrayList<Qitem> candidateList,ArrayList<Qitem> qItemNameList,Hashtable<Qitem, QUtilityListCorr> mapItemToUtilityList,ArrayList<Qitem> hwQUI,BufferedWriter writer_hqui) throws IOException{
            // delete non necessary candidate q-items
            int s=1;
            while (s<candidateList.size()-1){
                    if(((candidateList.get(s).getQteMin()==candidateList.get(s-1).getQteMax()+1)&&(candidateList.get(s).getItem()==candidateList.get(s-1).getItem()))||((candidateList.get(s).getQteMax()==candidateList.get(s+1).getQteMin()-1)&&(candidateList.get(s).getItem()==candidateList.get(s+1).getItem())))
                        s++;
                    else candidateList.remove(s);
                }
            if (candidateList.size()>2){
            if ((candidateList.get(candidateList.size()-1).getQteMin()!=candidateList.get(candidateList.size()-2).getQteMax()+1)||(candidateList.get(candidateList.size()-2).getItem()!=candidateList.get(candidateList.size()-1).getItem()))
                    candidateList.remove(candidateList.size()-1);
            }

            // make the combination process
            ArrayList<Qitem> temporaryArrayList= new ArrayList<Qitem>();
            Map <Qitem,QUtilityListCorr> temporaryMap=new HashMap<Qitem,QUtilityListCorr>();
            Map <Qitem,QUtilityListCorr> mapRangeToUtilityList=new HashMap<Qitem,QUtilityListCorr>();
            int count;
            for (int i=0;i<candidateList.size();i++){
                QUtilityListCorr res= new QUtilityListCorr(); 
                int currentItem=candidateList.get(i).getItem();
                mapRangeToUtilityList.clear();
                count=1;
                for (int j=i+1;j<candidateList.size();j++){   
                    int nextItem=candidateList.get(j).getItem();
                    // System.out.println("nextItem is "+nextItem);
                    if (currentItem!=nextItem)
                        break;
                    else{
                        if (j==i+1){
                            if (candidateList.get(j).getQteMin()!=candidateList.get(i).getQteMax()+1)
                                break;
                            res=constructForCombine(mapItemToUtilityList.get(candidateList.get(i)),mapItemToUtilityList.get(candidateList.get(j)));
                            count++;
                            //System.out.println("name is "+res.getItemsetName()+", count is "+count);
                            if (count >coefficient-1)
                                break;
                            mapRangeToUtilityList.put(res.getSingleItemsetName(), res);    
                        }
                        else{
                            if (candidateList.get(j).getQteMin()!=candidateList.get(j-1).getQteMax()+1)
                                break;
                            Qitem qItem1=new Qitem(currentItem,candidateList.get(i).getQteMin(),candidateList.get(j-1).getQteMax());
                            QUtilityListCorr ulQitem1=mapRangeToUtilityList.get(qItem1);
                            res=constructForCombine(ulQitem1,mapItemToUtilityList.get(candidateList.get(j)));
                            count++;
                            if (count >=coefficient) 
                                    break;
                            mapRangeToUtilityList.put(res.getSingleItemsetName(), res);    
                        }

                    }
                }
                if (res.getSumIutils()>getMinUtil()){
                   if ((temporaryMap.isEmpty())||(res.getSingleItemsetName().getItem()!=temporaryArrayList.get(temporaryArrayList.size()-1).getItem())||(res.getSingleItemsetName().getQteMax()>temporaryArrayList.get(temporaryArrayList.size()-1).getQteMax())){
                    temporaryMap.put(res.getSingleItemsetName(), res);
                    temporaryArrayList.add(res.getSingleItemsetName());   
                   }    
                }
            }
            for (int k=0;k<temporaryArrayList.size();k++){
                    Qitem currentQitem=temporaryArrayList.get(k);
                    mapItemToUtilityList.put(currentQitem,temporaryMap.get(currentQitem));
                    rangeHUQI++;
                    writeOut(prefix, prefixLength, currentQitem, temporaryMap.get(currentQitem).getSumIutils(),1,writer_hqui);
                    hwQUI.add(currentQitem);
                    Qitem q=new Qitem(currentQitem.getItem(),currentQitem.getQteMax());
                    int site = qItemNameList.indexOf(q);
                    qItemNameList.add(site, currentQitem);
            }

            temporaryArrayList.clear();
            temporaryMap.clear();
            MemoryLogger.getInstance().checkMemory();
        }

        /**
         * Combine method
         * 
         * @param prefix               a prefix
         * @param prefixLength         the length of the prefix
         * @param candidateList        the candidate list
         * @param qItemNameList        the qtiem list
         * @param mapItemToUtilityList a map of item to utility list
         * @param hwQUI                hwQUI
         * @return the result
         * @throws IOException if error while writing to file
         */
        public ArrayList<Qitem> combineMethod(Qitem [] prefix,int prefixLength,ArrayList<Qitem> candidateList,ArrayList<Qitem> qItemNameList,Hashtable<Qitem, QUtilityListCorr> mapItemToUtilityList,ArrayList<Qitem> hwQUI,BufferedWriter writer_hqui) throws IOException{
         // Sort the candidate Q-itemsets according to items than Qte-Min than Qte-Max
            if(candidateList.size()>2){          
                    Collections.sort(candidateList, new Comparator<Qitem>() {
                        public int compare(Qitem o1, Qitem o2) {				
                            return compareCandidateItems(o1, o2);
                        }
                    });      
            if (EnumCombination.COMBINEALL.equals(combiningMethod)) {
                                        combineAll(prefix, prefixLength, candidateList, qItemNameList, mapItemToUtilityList, hwQUI,writer_hqui);
                                } else if (EnumCombination.COMBINEMIN.equals(combiningMethod)) {
                                        combineMin(prefix, prefixLength, candidateList, qItemNameList, mapItemToUtilityList, hwQUI,writer_hqui);
                                } else if (EnumCombination.COMBINEMAX.equals(combiningMethod)) {
                                        combineMax(prefix, prefixLength, candidateList, qItemNameList, mapItemToUtilityList, hwQUI,writer_hqui);
                                }
                MemoryLogger.getInstance().checkMemory();
            }
        return qItemNameList;
        }
        
        /**
	 * Comparator to order candidate qItems
	 * 
	 * @param q1 a qitem
	 * @param q2 another qitem
	 * @return the comparison result
	 */
        private int compareCandidateItems(Qitem q1, Qitem q2) {
                int compare = q1.getItem() - q2.getItem();
                if (compare==0) compare=q1.getQteMin() - q2.getQteMin();
                if (compare==0) compare=q1.getQteMax() - q2.getQteMax();
                return compare;
        }
        
        
  private void writeOut(Qitem[] prefix, int prefixLength, Qitem x, long utility,double bound,BufferedWriter writer_hqui) throws IOException {
		
    //Create a string buffer
    StringBuilder buffer = new StringBuilder();
    // append the prefix
    for (int i = 0; i < prefixLength; i++) {
            buffer.append(prefix[i].toString());
            buffer.append(' ');
    }
    // append the last item
    buffer.append(x.toString()+" #UTIL: ");

    // append the utility value

    buffer.append(utility);
    buffer.append("\t#Bond:");
    buffer.append(bound);
    // write to file
    writer_hqui.write(buffer.toString());
    writer_hqui.newLine();
	
}
}
