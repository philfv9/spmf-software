package ca.pfv.spmf.algorithms.frequentpatterns.HUIM_ACO;

/*
 * Copyright (c) 2020 Wei Song, Jiakai Nan
 * 
 * This file is part of the SPMF DATA MINING SOFTWARE *
 * (http://www.philippe-fournier-viger.com/spmf).
 * 
 * 
 * SPMF is free software: you can redistribute it and/or modify it under the *
 * terms of the GNU General Public License as published by the Free Software *
 * Foundation, either version 3 of the License, or (at your option) any later *
 * version. *
 * 
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * *
 * 
 * You should have received a copy of the GNU General Public License along with
 * * SPMF. If not, see <http://www.gnu.org/licenses/>..
 * 
 */
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
/**
 * This class represents an ant as used by the HUIM-ACO algorithm.
 * @author Jiakai Nan, Wei Song
 * @see AlgoHUIMMACO
 */
public class Ant implements Comparable<Ant> {

    private BitSet bitSet;
    private int foodSize;
    private int foodTWU;
    private int currentFood;
    private int passFood;
    private List<Integer> existedFoods;
    private List<Integer> noExistedFoods;
    private static List<Integer> existPheromones = new ArrayList<Integer>();
    private static List<Double> decisionTable = new ArrayList<Double>();

    public void setFoodSize(int foodSize) {
        this.foodSize = foodSize;
    }

    /**
     * Constructor
     */
    public Ant(){
        bitSet = new BitSet();
    }

    /**
     * Constructor
     * @param length length of the ant
     */
    public Ant(int length){
        this.bitSet = new BitSet(length);
        this.existedFoods = new ArrayList<Integer>();
        this.noExistedFoods = new ArrayList<Integer>();

        for(int i = 0; i < length; i++) {
            noExistedFoods.add(i);
        }

    }

    /**
     * Set the food (TWU)
     * @param foodTWU the food (TWU)
     */
    public void setFoodTWU(int foodTWU) {
        this.foodTWU = foodTWU;
    }

    /** Set the existing food
     * 
     * @param existedFoods the existing food as a list of integers
     */
    public void setExistedFoods(List<Integer> existedFoods) {
        this.existedFoods = existedFoods;
    }

    /** Get the food (TWU)
     * 
     * @return the food (TWU) as an integer
     */
    public int getFoodTWU() {
        return foodTWU;
    }

    /**
     * Get the existing pheromones
     * @return a list of integers
     */
    public static List<Integer> getExistPheromones() {
        return existPheromones;
    }

    /**
     * Get the decision table
     * @return a list of double values
     */
    public static List<Double> getDecisionTable() {
        return decisionTable;
    }

    /** Get the existing foods
     * 
     * @return the existing foods as a list of integers
     */
    public List<Integer> getExistedFoods() {
        return existedFoods;
    }

    /**
     * Get the non existing food
     * @return a list of integers
     */
    public List<Integer> getNoExistedFoods() {
        return noExistedFoods;
    }
    public int getNoFood(int index){
        return this.noExistedFoods.get(index);
    }

    /** Set the existed food
     * 
     * @param index an index
     */
    public void setExistedFoods(int index) {
        if(!this.existedFoods.contains(index)){
            this.existedFoods.add(index);
            if(this.noExistedFoods.indexOf(index)==-1)
            {
                System.out.println("index"+index);
            }

            this.noExistedFoods.remove(this.noExistedFoods.indexOf(index));
        }
    }

    /** Set the non existing food
     * 
     * @param noExistedFoods a list of integers
     */
    public void setNoExistedFoods(List<Integer> noExistedFoods) {
        this.noExistedFoods = noExistedFoods;
    }

    /** Get the food size
     * 
     * @return the food size
     */
    public int getFoodSize() {
        return foodSize;
    }

    /** Get the bitset of this ant 
     * 
     * @return a BitSet object
     */
    public BitSet getBitSet(){
        return this.bitSet;
    }

    /**
     * Set the biset of this ant
     * @param bitSet a BitSet
     */
    public void setBitSet(BitSet bitSet) {
        this.bitSet = bitSet;
    }

    public Boolean isOwnFood(int index){

        return this.getBitSet().get(index);
    }

    public Ant setFood(int bitIndex){
        this.bitSet.set(bitIndex);
        return this;
    }


    public void setCurrentFood(int currentItem){
        this.currentFood = currentItem;
    }

    public void setPassFood(int passItem){
        this.passFood = passItem;
    }

    public int getPassFood(){
        return this.passFood;
    }

    public int getCurrentFood(){
        return  this.currentFood;
    }
int otherToFood=0;
    public int searchFood(List<Integer> HTWUs_1,HUIS huis,Roulette roulette){
        int toItem=0;

        do{
            if(this.getNoExistedFoods().size()!=0){
                do{
                    if(roulette.getChanged()){
                        toItem=huis.getBestItemIndex().get(roulette.whirlRoulette());
                    }else{
                        toItem=roulette.whirlRoulette();
                    }

                } while(!this.getNoExistedFoods().contains((Integer) toItem));
               // System.out.println(this.getNoExistedFoods());
               // System.out.println(toItem);
                //toItem = this.getNoExistedFoods().get((int)(Math.random() * this.getNoExistedFoods().size()));
            }else{
               // System.out.println("可选节点列表为空");
                return -2;
            }
        }while(this.isOwnFood(toItem));
        return toItem;
    }

    public int searchFood(){
        int toItem=0;
        do{
            if(this.getNoExistedFoods().size()!=0){
                toItem = this.getNoExistedFoods().get((int)(Math.random() * this.getNoExistedFoods().size()));
            }else{
                // System.out.println("可选节点列表为空");
                return -2;
            }
        }while(this.isOwnFood(toItem));
        return toItem;
    }


    public int makedecision(List<Double> decisionTable){

        double randNum = Math.random();
        int selectedPheromones = -1;

        if(decisionTable.isEmpty()){
          //  System.out.println("决策表为空");
            return this.searchFood();
        }

        if(this.existedFoods.isEmpty()){
          //  System.out.println("存在的信息素为空");
        }

        for(int i = 0; i < decisionTable.size(); i++){
            if(i==0){
                if ((randNum >= 0 ) && randNum < decisionTable.get(0)){
                    selectedPheromones =this.existPheromones.get(0);
                    break;
                }
            }else if ((randNum > decisionTable.get(i-1)) && randNum <= decisionTable.get(i))
            {
                selectedPheromones = this.existPheromones.get(i);
                break;
            }
        }

        if(selectedPheromones==-1){
            return this.searchFood();
        }

        if(selectedPheromones==-1){
            System.out.println("选择下一个节点异常");
        }
        return selectedPheromones;
    }

    public int selectedMaximumPheromones(double [][] globalPheromones){
        int maximumPheromonesIndex = -1;
        double maximumPheromones = 0;
        for(int i = 0; i < this.getNoExistedFoods().size(); i++){
            if(this.currentFood<this.getNoFood(i)){
                if(maximumPheromones <= globalPheromones[this.currentFood][this.getNoFood(i)]){
                    maximumPheromones = globalPheromones[this.currentFood][this.getNoFood(i)];
                    maximumPheromonesIndex = this.getNoFood(i);
                }
            }else {
                if(maximumPheromones  <= globalPheromones[this.getNoFood(i)][this.currentFood]){
                    maximumPheromones = globalPheromones[this.getNoFood(i)][this.currentFood];
                    maximumPheromonesIndex =this.getNoFood(i);
                }
            }
        }
        if(maximumPheromones == -1){
            maximumPheromones=this.searchFood();
        }

        return maximumPheromonesIndex;
    }

    public void judgeFoodSize(List<Integer> tansLists,List<Integer> itemList, Integer[][] database, List<Integer> HTWUs_1,Integer [] transactionUtilityList){
        int foodSize = 0;
        int foodTWU = 0;
        for(int y = 0; y < tansLists.size(); y++){
            for(int x = 0;x < itemList.size();x++){

 //               System.out.println("y:"+tansLists.get(y)+"  "+"x:"+itemList.get(x)+" "+database[tansLists.get(y)][itemList.get(x)]);
                foodSize+=database[tansLists.get(y)][itemList.get(x)];
            }
            foodTWU+=transactionUtilityList[tansLists.get(y)];
        }
        this.foodTWU=foodTWU;
        this.foodSize =foodSize;
    }
    public void judgeFoodSize(List<Integer> tansLists,List<Integer> itemList, Integer[][] database, List<Integer> HTWUs_1){
        int foodSize = 0;
        for(int y = 0; y < tansLists.size(); y++){
            for(int x = 0;x < itemList.size();x++){

                //               System.out.println("y:"+tansLists.get(y)+"  "+"x:"+itemList.get(x)+" "+database[tansLists.get(y)][itemList.get(x)]);
                foodSize+=database[tansLists.get(y)][itemList.get(x)];
            }
        }
        this.foodSize =foodSize;
    }
    public boolean isPurchasedItemset(List<Integer> transactionLists,List<Integer> itemsIndex,Integer[][] database,List<ItemBitmap> itemsBitmap){

        List<Integer> otherItemsIndex = new ArrayList<>();
        //find the place of items in the ant tour
        for(int i=0; i<this.bitSet.length();i++) {
            if(this.bitSet.get(i))
            {
                itemsIndex.add(i);
                otherItemsIndex.add(i);
            }
        }

//        System.out.println("食物索引："+itemsIndex);

        BitSet judgedAnt = new BitSet(database.length);    //each itemBitmap in the ant tour
        BitSet midValue = new BitSet(database.length);

        judgedAnt = (BitSet)itemsBitmap.get(itemsIndex.get(0).intValue()).getItemBitmap().clone();

//        System.out.println(itemsBitmap.get(itemsIndex.get(0).intValue()).getItem());
//        System.out.println(judgedAnt);

        midValue = (BitSet)judgedAnt.clone();

//        System.out.println(itemsBitmap.get(itemsIndex.get(0).intValue()).getItem());
//        System.out.println(itemsBitmap.get(itemsIndex.get(0).intValue()).getItemBitmap());

        for(int i = 1; i < otherItemsIndex.size(); ++i){
            judgedAnt.and(itemsBitmap.get(otherItemsIndex.get(i).intValue()).getItemBitmap());

//            System.out.println(itemsBitmap.get(itemsIndex.get(i).intValue()).getItem());
//            System.out.println(itemsBitmap.get(itemsIndex.get(i).intValue()).getItemBitmap());
//            System.out.println(judgedAnt);
            //处理结尾为空的情况
            if(i == itemsBitmap.size()-1){
                if(judgedAnt.cardinality()==0){
                    this.bitSet.clear(otherItemsIndex.get(i).intValue());
                    itemsIndex.remove(otherItemsIndex.get(i));
                }
                break;
            }
            if(judgedAnt.cardinality()!=0){
                midValue = (BitSet)judgedAnt.clone();
            }else{
                    judgedAnt = (BitSet)midValue.clone();
                    this.bitSet.clear(otherItemsIndex.get(i).intValue());
                itemsIndex.remove( otherItemsIndex.get(i));
            }
        }

//        System.out.println("清除后的索引："+this.bitSet);

//        System.out.println(judgedAnt);
        if(judgedAnt.cardinality()==0){
//            System.out.println("transactionLists"+transactionLists);
              return false;
        }else{
            for(int m = 0; m < judgedAnt.length();++m){
                if(judgedAnt.get(m)){
                    transactionLists.add(m);
                }
            }

//            System.out.println("transactionLists"+transactionLists);
            return true;
        }
    }


    public void releasePheromones(AntColony antColony,Integer minUtility){
       // System.out.println("当前节点："+this.getCurrentFood()+"  "+this.getPassFood());
        if(this.getCurrentFood()<this.getPassFood()){
            antColony.setGlobalPheromones(this.getCurrentFood(),this.getPassFood(),(this.getFoodSize()/minUtility.doubleValue()));
        }else{
            antColony.setGlobalPheromones(this.getPassFood(),this.getCurrentFood(),(this.getFoodSize()/minUtility.doubleValue()));
        }
    }
    public void releasePheromones(AntColony antColony){
        // System.out.println("当前节点："+this.getCurrentFood()+"  "+this.getPassFood());
        if(this.getCurrentFood()<this.getPassFood()){
            antColony.setGlobalPheromones(this.getCurrentFood(),this.getPassFood(),0.0);
        }else{
            antColony.setGlobalPheromones(this.getPassFood(),this.getCurrentFood(),0.0);
        }
    }
    public void initializeDecisionTable(double [][] globalPheromones){
        //the sum of pheromones value of each eage no searched
        double PheromonesSum = 0;
        //the sum of pheromones frome first pheromones to the pheromones
        double eachPheromonesSum = 0;
        //the proportion of the HTWUSum in twuSum
        double proportion = 0;

        //caculate the allHtwuSum
        for(int i = 0; i < this.getNoExistedFoods().size(); i++){


            if(this.currentFood<this.getNoFood(i)){
                PheromonesSum += globalPheromones[this.currentFood][this.getNoFood(i)];
            }else{
                PheromonesSum += globalPheromones[this.getNoFood(i)][this.currentFood];
            }

        }
        //caculate the eachHtwuSum
        for(int i = 0; i < this.getNoExistedFoods().size(); i++){

            if(this.currentFood<this.getNoFood(i)){
                if(globalPheromones[this.currentFood][this.getNoFood(i)] != 0)
                {
                    eachPheromonesSum += globalPheromones[this.currentFood][this.getNoFood(i)];
                    proportion = eachPheromonesSum/PheromonesSum;
                    decisionTable.add(proportion);
                    existPheromones.add(this.getNoExistedFoods().get(i));
                }

            }else{
                if(globalPheromones[this.getNoFood(i)][this.currentFood] != 0) {
                    eachPheromonesSum += globalPheromones[this.getNoFood(i)][this.currentFood];
                    proportion = eachPheromonesSum/PheromonesSum;
                    decisionTable.add(proportion);
                    existPheromones.add(this.getNoExistedFoods().get(i));
                }

            }
        }
    }

    public void copyTo(Ant ant){
        ant.setBitSet((BitSet) this.bitSet.clone());
        ant.setFoodSize(this.foodSize);
        ant.setFoodTWU(this.foodTWU);
        ant.setCurrentFood(this.currentFood);
        ant.setPassFood(this.passFood);
        ant.getExistedFoods().clear();
        ant.getNoExistedFoods().clear();
        for(Integer itemIndex: this.getExistedFoods()){
            ant.getExistedFoods().add(itemIndex);
        }
        for(Integer itemIndex: this.getNoExistedFoods()){
            ant.getNoExistedFoods().add(itemIndex);
        }

    }


    @Override
    public int compareTo(Ant o) {
        return o.getFoodSize()-this.getFoodSize();
    }
}