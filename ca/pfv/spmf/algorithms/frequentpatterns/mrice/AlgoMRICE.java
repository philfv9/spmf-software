package ca.pfv.spmf.algorithms.frequentpatterns.mrice;
/* This file is copyright (c) 2008-2016 Philippe Fournier-Viger
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
*/
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

/**
 * This is an implementation of the "MRI-CE algorithm" for Minimal Infrequence Itemsets Mining
 * @author Song. et al. (2024)
 */

public class AlgoMRICE {
    // variable for statistics
    long startTimestamp = 0; // the time the algorithm started
    long endTimestamp = 0; // the time the algorithm terminated
    int pop_size = 1000; // the sample numbers
    final int iterations = 200; // the iterations of algorithm
    boolean compressSpace = false;
    boolean crossMutation = false;
    int consecutiveTerminations = 1;
    int transactionCount = 0; // the size of transactions
    int min_sup=0;
    int minimalSupport=0;
    int total_items = 0;
    int actual_iterations = 0;
    PriorityQueue<Map.Entry<Integer, Integer>> priorityQueue;
    final float alpha = (float) 0.999; // the mutation parameter
    final float beta = (float) 0.01;
    Map<Integer, Integer> mapItemToF; // create a map to store the frequency of each item
    List<Integer> FPattern;// the items which has support more than min_sup
    BufferedWriter writer = null;
    float[] p; // probability vector

    // this class represent an item in a transaction
    static class Pair {
        int item = 0;
    }

    // this class represent the sample
    class Particle {
        BitSet X; // the sample
        int fitness; // fitness value of sample(support)

        public Particle() {
            X = new BitSet(FPattern.size());
        }

        public Particle(int length) {
            X = new BitSet(length);
        }

        public void copyParticle(Particle particle1) {
            this.X = (BitSet) particle1.X.clone();
            this.fitness = particle1.fitness;
        }

        @Override
        public boolean equals(Object obj) {
            if(this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Particle particle = (Particle) obj;
            return this.X.equals(particle.X);
        }

        @Override
        public int hashCode() {
            return this.X.hashCode();
        }
    }

    static class FI {
        String itemset;
        int fitness;

        public FI(String itemset, int fitness) {
            super();
            this.itemset = itemset;
            this.fitness = fitness;
        }

    }

    class Item {
        int item;
        BitSet TIDS;

        public Item(int item) {
            TIDS = new BitSet(transactionCount);
            this.item = item;
        }
    }

    List<Particle> population = new ArrayList<>();// samples

    //List<Particle> populationMutation = new ArrayList<>();//sample to mutation
    //List<Particle> nextPopulationMutation = new ArrayList<>();
    //List<Particle> populationNew = new ArrayList<>();// new sample
    List<FI> fiSets = new ArrayList<>();

    // Create a list to store database
    List<List<Pair>> database = new ArrayList<>();
    List<Item> Items;
    //List<Particle> KFIList = new ArrayList<Particle>(); // Frequent item_sets

    Set<BitSet> MifItemset;
    List<Integer> NonSelectedFPattern;
    Set<Integer> NonSelectedFPatternOpt;
    List<Integer> selectedFPattern;
    Set<Particle> FItemset;
    Set<Particle> iterationPopulationSet = new HashSet<>();
    List<Particle> MRI = new ArrayList<>();
    List<String> MRIInit = new ArrayList<>();
    Particle[] pt;
    //Refine performance by hashcode
    Set<BitSet> FItemsetHC;
    // To construct Oscillation Factor queue
    Queue<Integer> slideContainer;

    //int newPopSize;
    double factor0 = 0.0;

    /**
     * Default constructor
     */
    public AlgoMRICE() {
    }

    /**
     * Run the algorithm
     *
     * @param input  the input file path
     * @param output the output file path
     * @throws IOException exception if error while writing the file
     */

    public void runAlgorithm(String input, String output,double min_sup_percentage) throws IOException {

        FPattern = new ArrayList<>();
        writer = new BufferedWriter(new FileWriter(output));
        // create a map to store the frequency of each item
        mapItemToF = new HashMap<>();
        startTimestamp = System.currentTimeMillis();
        // scan the database to calculate the support of each item.
        BufferedReader myInput = null;
        String thisLine;
        try {
            // prepare the object for reading the file
            myInput = new BufferedReader(new InputStreamReader(new FileInputStream(input)));
            // for each line (transaction) until the end of file
            while ((thisLine = myInput.readLine()) != null) {
                // if the line is a comment, is empty or is a kind of metadata
                if (thisLine.isEmpty() || thisLine.charAt(0) == '#'
                        || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@') {
                    continue;
                }
                ++transactionCount;//Count the number of transactions in the database
                // split the transaction according to the " " separator
                String[] items = thisLine.split(" ");
                // for each item, we add the frequency
                for (String s : items) {
                    // convert item to integer
                    Integer item = Integer.parseInt(s);
                    // get the current frequency of that item
                    Integer f = mapItemToF.get(item);
                    // add the frequency of the item in the current transaction
                    f = (f == null) ? 1 : (f + 1);
                    mapItemToF.put(item, f);
                }
            }
        } catch (Exception e) {
            // catches exception if error while reading the input file
            e.printStackTrace();
        } finally {
            if (myInput != null) {
                myInput.close();
            }
        }
        //the method for calculate the min_sup and FPattern
        min_sup = (int) Math.ceil(min_sup_percentage*transactionCount);
        MifItemset = new HashSet<BitSet>();
        raisingMinSup(mapItemToF);
        Collections.sort(FPattern);
        // the probability vector
        p = new float[FPattern.size()];
        //Collections.sort(FPattern);
        //CONSTRUCT THE DATABASE
        // OF 1-ITEMSETS HAVING frequency >= sup (promising items)
        try {
            // prepare object for reading the file
            myInput = new BufferedReader(new InputStreamReader(new FileInputStream(input)));
            // variable to count the number of transaction
            // for each line (transaction) until the end of file
            while ((thisLine = myInput.readLine()) != null) {
                // if the line is a comment, is empty or is a kind of metadata
                if (thisLine.isEmpty() || thisLine.charAt(0) == '#'
                        || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@') {
                    continue;
                }
                // get the list of items
                String[] items = thisLine.split(" ");
                total_items += items.length;
                // Create a list to store items and its support
                List<Pair> revisedTransaction = new ArrayList<>();
                // for each item
                for (String item : items) {
                    Integer current_item = Integer.parseInt(item);
                    if ((mapItemToF.get(current_item)) >= min_sup) {
                        // / convert values to integers
                        Pair pair = new Pair();
                        pair.item = current_item;
                        revisedTransaction.add(pair);
                    }
                }
                database.add(revisedTransaction);
            }
        } catch (Exception e) {
            // to catch error while reading the input file
            e.printStackTrace();
        } finally {
            if (myInput != null) {
                myInput.close();
            }
        }

        Items = new ArrayList<>();
        //prepare for constructing the bitmap
        for (Integer tempItem : FPattern) {
            Items.add(new Item(tempItem));
        }

        //bitmap cover representation
        for (int i = 0; i < database.size(); ++i) {
            for (Item item : Items) {
                for (int k = 0; k < database.get(i).size(); ++k) {
                    if (item.item == database.get(i).get(k).item) {
                        item.TIDS.set(i);
                    }
                }
            }
        }

        // Mine the database
        if (FPattern.size() > 0) {

            FItemsetHC = new HashSet<>();

            initFItemsetHC();

            // initial population
            generatePop();

            //The total amount of mining so far
            int sum = MifItemset.size();
            int countUnChanged = 0;

            int prior = MifItemset.size();

            //int prePopSize = pop_size;

            slideContainer = new ArrayDeque<Integer>();
            slideContainer.offer(MifItemset.size());
            //Iteration
            for (int i = 0; i < iterations; i++) {

                //Store the valid population for this iteration
                pt = iterationPopulationSet.toArray(new Particle[iterationPopulationSet.size()]);
                //The life cycle of the iterationPopulationSet has ended and the iterationPopulationset is transferred to pt
                iterationPopulationSet.clear();
                // update population and KFIList

                //Record the total amount mined before the iteration officially began
                prior = MifItemset.size();

                update();

                if (pt.length > 1){
                    //random cross mutation
                    random_cross_mutation();
                }

                //iteration complete
                if (sum == MifItemset.size()){
                    countUnChanged ++;
                }else if ( countUnChanged > 0){
                    sum = MifItemset.size();
                    countUnChanged --;
                }else {
                    sum = MifItemset.size();
                }
                if (countUnChanged == consecutiveTerminations){
                    break;
                }

                //To compress
                compressionExecutor(i,prior);

                actual_iterations++;
            }

            endTimestamp = System.currentTimeMillis();

        }
        writeOut();
        // close output file
        writer.close();

    }



    public double computeFactor0(Queue<Integer> slideContainer){
        double sum = 0.0;
        double slideSum = 0.0;
        for (Integer integer : slideContainer){
            sum += integer;
        }
        double mean = sum / slideContainer.size();
        for(Integer integer : slideContainer){
            slideSum += Math.abs(integer - mean);
        }
        return Math.sqrt(1.0 + slideSum);
    }

    /**
     * @param i iteration
     * @param prior
     */
    public void compressionExecutor(int i,int prior){
        slideContainer.offer(MifItemset.size() - prior);
        //gap
        if (slideContainer.size() > 3){
            slideContainer.poll();
            pop_size = compressPopulationImprove(pop_size,7,3,i,slideContainer,factor0);
        } else if (slideContainer.size() == 3) {
            //count factor0
            factor0 = computeFactor0(slideContainer);
        }
    }

    /**
     * Compressed population size
     * @param prePopSize
     * @param k
     * @param q
     * @param i
     * @param slideContainer
     * @param factor0
     * @return
     */
    public int compressPopulationImprove(int prePopSize,int k,double q,int i,Queue<Integer> slideContainer,double factor0){
        //Integer[] slideElements = slideContainer.toArray(new Integer[3]);
        double sum = 0.0;
        double slideSum = 0.0;
        for (Integer integer : slideContainer){
            sum += integer;
        }
        double mean = sum / slideContainer.size();
        for(Integer integer : slideContainer){
            slideSum += Math.abs(integer - mean);
        }
        double factor = Math.sqrt(1.0 + slideSum);
        //double mean = (prior1 + prior2 + prior3)/3;
        //double factor = Math.sqrt(1.0 + (Math.abs(prior1 - mean) + Math.abs(prior2 - mean) + Math.abs(prior3 - mean))/3);
        //double test2 = (1.0/2.0)*(factor/factor0)+(1.0/2.0)*Math.log(1 + i)/i;
        //double test = Math.pow(Math.E,-k*((1/2)*(factor/factor0)+(1/2)*Math.log(1 + i)/i));

        return (int) (prePopSize*Math.pow(1 - (Math.pow(Math.E,-k*((1.0/3.0)*(factor/factor0)+(2.0/3.0)*Math.log(1 + i)/i))),1.0/q));
    }




    /**
     * termination
     * @throws IOException
     */
    public void termination() {

        try {
            writeOut();
            writer.close();
        }catch (IOException e){
            e.printStackTrace();
        }
        printStats();
        System.exit(0);
    }


    public int comparebit(BitSet a,BitSet b){
        int x = 0,y = 0;
        if (a.cardinality() != b.cardinality()){
            return a.cardinality() - b.cardinality();
        } else if (a.length() != b.length()) {
            return a.length() - b.length();
        }else {
            while (a.nextSetBit(x) == b.nextSetBit(y) && a.nextSetBit(x) < a.length() - 1){
                x = a.nextSetBit(x + 1);
                y = b.nextSetBit(y + 1);
            }
            return a.nextSetBit(x) - b.nextSetBit(y);
        }
    }

    /**
     * Add the basic elements to the FItemset
     */
    public void initFItemset(){
        for (int i = 0; i < FPattern.size(); i++) {
            Particle pt = new Particle(FPattern.size());
            pt.X.set(i);
            FItemset.add(pt);
        }
    }

    /**
     * Initialize FItemsetHC to boost performance
     */
    public void initFItemsetHC(){
        for (int i = 0; i < FPattern.size(); i++) {
            BitSet bitSet = new BitSet(FPattern.size());
            bitSet.set(i);
            FItemsetHC.add(bitSet);
        }
    }

    /**
     * Initializes frequent items that are not selected for combination
     * @param FPattern
     */
    public void initNonSelectedFPattern(List<Integer> FPattern){
        NonSelectedFPattern = new LinkedList<Integer>();

        for (int i = 0; i < FPattern.size(); i++) {
            NonSelectedFPattern.add(i);
        }

    }

    /**
     * This method is to calculate min_sup and FPattern
     * @param map whith the item and frequency
     */
    public void raisingMinSup(Map<Integer, Integer> map) {
        if (map == null)
            return;

        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            if (entry.getValue() >= min_sup){
                FPattern.add(entry.getKey());
            }else {
                MRIInit.add(entry.getKey() + " " + "#SUP:" + entry.getValue());
            }
        }

    }

    /**
     * This is the method to initialization for generate population
     */
    private void generatePop() {
        int i, j, k, temp;
        for (i = 0; i < pop_size; ++i) {
            //System.out.println("pop : "+i);
            Particle tempParticle = new Particle(FPattern.size());
            j = 0;
            // k is the count of 1 in probability vector

            k = (int) (Math.random() * FPattern.size() + 1);

            //k = 1;
            //tempParticle.X.set(3);

            while (j < k) {
                // roulette select the position of 1 in population
                temp = (int) (Math.random() * FPattern.size());
                if (!tempParticle.X.get(temp)) {
                    j++;
                    tempParticle.X.set(temp);
                }
            }

            tempParticle.fitness = isRBAIndividual(tempParticle);

            // insert itemset into itemsets collection of current iteration
            population.add(tempParticle);
            // insert into sets of MifI
            insertIndividuals(tempParticle);
        }
    }


    /**
     * Cross to generate new sample
     */
    private void random_cross_mutation() {

        int t;

        int mutation_size = (int) Math.min(pt.length, pop_size*(1-alpha));
        //This is a population of cross
        for (int i = 0; i < mutation_size; i += 2) {

            int pos1 = (int) (Math.random() * pt.length);

            int pos2 = (int) (Math.random() * pt.length);
            while (pos1 == pos2) {

                pos2 = (int) (Math.random() * pt.length);
            }

            Particle temp1 = new Particle();
            temp1.copyParticle(pt[pos1]);

            Particle temp2 = new Particle();
            temp2.copyParticle(pt[pos2]);

            int k = (int) (Math.random() * FPattern.size() + 1);
            int j = 0;
            while (j < k) {
                // roulette select the position of 1 in population
                t = (int) (Math.random() * FPattern.size());
                boolean t_bit = temp1.X.get(t);
                temp1.X.set(t, temp2.X.get(t));
                temp2.X.set(t, t_bit);
                j++;
            }

            if (temp1.X.cardinality()!= 0){
                temp1.fitness = isRBAIndividual(temp1);
                population.add(temp1);
                insertIndividuals(temp1);
            }
            if (temp2.X.cardinality()!=0){
                temp2.fitness = isRBAIndividual(temp2);
                population.add(temp2);
                insertIndividuals(temp2);
            }

        }

    }

    /**
     * This is the method to update the set of top-k FIs KH using the new sample
     */
    private void update() {
        int[] num = new int[FPattern.size()];
        for (int i = 0; i < population.size(); ++i) {
            for (int j = 0; j < FPattern.size(); ++j) {
                if (population.get(i).X.get(j)) {
                    num[j] += 1;
                }
            }
        }
        for (int i = 0; i < FPattern.size(); ++i) {
            p[i] = (float) (num[i] / (population.size() + 0.0));
        }

        //Save the next generation sample at this time
        population = new ArrayList<>(pop_size);

        //Release the previous generation population collection space

        for (int i = 0; i < (int) (pop_size*alpha); ++i) {
            Particle tempParticle = new Particle(FPattern.size());
            update_Particle(tempParticle);

            if(tempParticle.X.cardinality() == 0){
                continue;
            }

            /*System.out.println("pops: "+i);
            System.out.println(tempParticle.X);*/
            tempParticle.fitness = isRBAIndividual(tempParticle);
            //if(tempParticle.fitness>=min_sup)
            {
                population.add(tempParticle);

                insertIndividuals(tempParticle);
            }
        }

    }

    private void update_Particle(Particle temp) {
        for (int i = 0; i < FPattern.size(); ++i) {
            if (Math.random() < p[i]) {
                temp.X.set(i);
            }
        }
    }


    private void insertIndividuals(Particle tmp){
        iterationPopulationSet.add(tmp);
        if (MifItemset.add(tmp.X)){
            MRI.add(tmp);
        }

    }


    /**
     * It is used to get the collection of transactions in which the itemset resides.
     * If the itemset itself is unreasonable, it is automatically fine-tuned during the calculation.
     * Make it reasonable and get the set of transactions in which it is located
     */
    public int isRBAIndividual(Particle tempBAIndividual) {
        //Prepare for a search for a MifI
        initNonSelectedFPattern(FPattern);
        List<Integer> tempList = new ArrayList<>();

        List<Integer> tempList2 = new ArrayList<>();

        for (int i = tempBAIndividual.X.nextSetBit(0);i != -1;i = tempBAIndividual.X.nextSetBit(i + 1)){
            tempList.add(i);
        }

        BitSet tempBitSet = (BitSet) Items.get(tempList.get(0)).TIDS.clone();

        BitSet midBitSet = null;

        Particle tempFItemset = new Particle(FPattern.size());

        Particle inFItemset = new Particle(FPattern.size());

        tempFItemset.X.set(tempList.get(0));

        NonSelectedFPattern.remove(tempList.get(0));
        for (int i = 1; i < tempList.size(); ++i) {
            midBitSet = (BitSet) tempBitSet.clone();
            tempBitSet.and(Items.get(tempList.get(i)).TIDS);
            if (tempBitSet.cardinality() == midBitSet.cardinality()){
                NonSelectedFPattern.remove(tempList.get(i));
                tempBitSet = midBitSet;
                continue;
            }

            if (tempBitSet.cardinality() >= min_sup) {

                //Update the frequent collection of successful combinations
                tempFItemset.X.set(tempList.get(i));

                //Select frequent
                NonSelectedFPattern.remove(tempList.get(i));

                //Performance enhancement
                FItemsetHC.add((BitSet) tempFItemset.X.clone());
                //else if(tempBitSet.cardinality() != 0 )
            } else {

                // tempBitSet.cardinality() < min_sup
                NonSelectedFPattern.remove(tempList.get(i));

                //Update, the current first I has been selected
                tempFItemset.X.set(tempList.get(i));

                tempFItemset.fitness = tempBitSet.cardinality();
                //selectedFPattern.add(tempList.get(i));

                //tempFItemset.X.set(tempList.get(i));

                while (!checkMinInf(tempFItemset,tempList2,inFItemset)){
                    //The check failed, and the infrequent subset is continued to check
                    tempFItemset = inFItemset;
                    inFItemset = new Particle(FPattern.size());
                }
                tempBAIndividual.X = (BitSet) tempFItemset.X.clone();
                tempBAIndividual.fitness = tempFItemset.fitness;
                //tempBitSet = null;
                return tempBAIndividual.fitness;

            }
        }

        return searchOutOfTempList(tempFItemset,tempList2,tempBitSet,tempBAIndividual,inFItemset);
    }

    /**
     * try to combine with his frequent items in the outer layer of the random generated set
     * @param tempFItemset
     * @param tempList2
     * @param tempBitSet
     * @param tempBAIndividual
     */
    public int searchOutOfTempList( Particle tempFItemset,
                                    List<Integer> tempList2,
                                    BitSet tempBitSet,
                                    Particle tempBAIndividual,
                                    Particle inFItemset
                                    ){

        BitSet midBitSetOut = null;

        while (NonSelectedFPattern.size() > 0){
            int j = (int) (Math.random()*NonSelectedFPattern.size());
            //test
            //j = 0;
            midBitSetOut = (BitSet) tempBitSet.clone();
            tempBitSet.and(Items.get(NonSelectedFPattern.get(j)).TIDS);
            if (tempBitSet.cardinality() == midBitSetOut.cardinality()){
                NonSelectedFPattern.remove(j);
                tempBitSet = midBitSetOut;
                continue;
            }
            tempFItemset.X.set(NonSelectedFPattern.get(j));
            tempFItemset.fitness = tempBitSet.cardinality();

            NonSelectedFPattern.remove(j);

            if (tempBitSet.cardinality() >= min_sup){
                //Performance enhancement
                FItemsetHC.add((BitSet) tempFItemset.X.clone());
            } else {

                while (!checkMinInf(tempFItemset,tempList2,inFItemset)){
                    tempFItemset = inFItemset;
                }

                tempBAIndividual.X = (BitSet) tempFItemset.X.clone();
                tempBAIndividual.fitness = tempFItemset.fitness;

                return tempBAIndividual.fitness;
            }
        }

        System.out.println("error,This data set does not meet the requirements, and there is no minimum number of items in the whole domain");
        System.out.println(FPattern);
        System.out.println(tempFItemset.X);
        System.exit(0);
        return 0;
    }

    /**
     *
     * @param i
     * @param tempList
     * @param tempBitSet
     * @param midBitSet
     */
    public void searchAgin(int i,List<Integer> tempList,BitSet tempBitSet,BitSet midBitSet){
        if (i >= tempList.size() - 1){
            //At this point, there is no time to try and need to expand the scope of the check
            if (NonSelectedFPattern.size() > 0){
                //
            }else {
                System.out.println("There is no minf，Discovery anomaly");

                System.exit(0);
            }
        }else {
            //Explain the random generated items and can be tried, and the operation is withdrawn
            tempBitSet = (BitSet) midBitSet.clone();
        }
    }

    /**
     * Whether the subset of the check item is MifI
     * @param tempFItemset
     * @param tempList2
     * @return
     */
    public boolean checkMinInf(Particle tempFItemset,
                                  List<Integer> tempList2,
                                  Particle inFItemset
                                  ){
        //initialize
        tempList2.clear();

        BitSet tempBitSet2 = null;
        //inFItemset = null;

        for (int i = tempFItemset.X.nextSetBit(0);i != -1;i = tempFItemset.X.nextSetBit(i + 1)){
            tempList2.add(i);
        }


        /**
         *
         * To break down this non-frequent set of items when not in the whole domain,
         * when you search for the minimum Infrequent items
         */
        for (Integer integer : tempList2) {
            tempFItemset.X.clear(integer);
            //System.out.println("check it : " + tempFItemset.X);
            if (!FItemsetHC.contains(tempFItemset.X)) {
                //if (!FItemset.contains(tempFItemset)) {

                //check
                tempBitSet2 = (BitSet) Items.get(tempFItemset.X.nextSetBit(0)).TIDS.clone();

                for (int j = tempFItemset.X.nextSetBit(tempFItemset.X.nextSetBit(0) + 1);
                     j != -1;
                     j = tempFItemset.X.nextSetBit(j + 1)) {

                    tempBitSet2.and(Items.get(j).TIDS);
                    //Check failure
                    if (tempBitSet2.cardinality() < min_sup) {
                        //inFItemset = new Particle(FPattern.size());
                        //System.out.println("preinf");
                        inFItemset.X = (BitSet) tempFItemset.X.clone();
                        inFItemset.fitness = tempBitSet2.cardinality();
                        //Infrequent itemset encountered in check
                        if (tempFItemset.X.nextSetBit(j + 1) != -1) {
                            inFItemset.X.clear(tempFItemset.X.nextSetBit(j + 1), tempFItemset.X.length());
                        }

                        return false;
                    }
                }

                //Performance enhancement
                FItemsetHC.add((BitSet) tempFItemset.X.clone());
            }

            //A set of prechecks in the subset is successful and ended, and the original state is restored
            tempFItemset.X.set(integer);
        }


        //There is unnecessary to calculate the support of the minimum infrequent
        return true;
    }



    /**
     * Method to inseret tempItemset to huiSets
     * @param tempParticle the itemset to be inserted
     */
    private void insert(Particle tempParticle) {
        int i;
        StringBuilder temp = new StringBuilder();
        for (i = 0; i < FPattern.size(); i++) {
            if (tempParticle.X.get(i)) {
                temp.append(FPattern.get(i));
                temp.append(' ');
            }
        }
        // huiSets is null
        if (fiSets.size() == 0) {
            fiSets.add(new FI(temp.toString(), tempParticle.fitness));
        } else {
            // huiSets is not null, judge whether exist an itemset in huiSets
            // same with tempParticle
            for (i = 0; i < fiSets.size(); i++) {
                if (temp.toString().equals(fiSets.get(i).itemset)) {
                    break;
                }
            }
            // if not exist same itemset in huiSets with tempParticle,insert it
            // into huiSets
            if (i == fiSets.size())
                fiSets.add(new FI(temp.toString(), tempParticle.fitness));
        }
    }

    /**
     * Method to write a MifI to the output file.
     *
     * @throws IOException for write out
     */
    private void writeOut() throws IOException {

        for (String string : MRIInit){
            writer.write(string);
            writer.newLine();
        }
        for (Particle particle : MRI){

            StringBuilder temp = new StringBuilder();
            for (int i = 0; i < FPattern.size(); i++) {
                if (particle.X.get(i)) {
                    temp.append(FPattern.get(i));
                    temp.append(' ');
                }
            }
            temp.append("#SUP:" +
                    particle.fitness);
            writer.write(temp.toString());
            writer.newLine();
        }

    }


    /**
     * Print statistics about the latest execution to System.out.
     */
    public void printStats() {
        System.out.println("-----------------The MRI-CE Algorithm Status------------------");
        System.out.println(" Mining process completed !");
        System.out.println(" MifI count ~ " + (MifItemset.size() + MRIInit.size()));
        System.out.println(" The total number iterations ~ " + actual_iterations);
        System.out.println(" Time ~ " + (endTimestamp - startTimestamp) + "ms");
        System.out.println("--------------------------------------------------------------");
    }
}
