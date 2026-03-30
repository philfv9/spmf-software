package ca.pfv.spmf.algorithms.frequentpatterns.sfu_ce;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

/*This file is copyright (c) 2021 Wei Song et al.
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
 * This is an implementation of the "SFU-CE algorithm" for mining skyline
 * frequent utility itemsets Mining as described in the conference paper :
 * <p>
 * SFU-CE: Skyline Frequent-Utility Itemset Discovery Using the Cross-Entropy
 * Method
 *
 * @author Wei Song,Chuanlong Zheng
 */
public class AlgoSFU_CE {

    // variable for statistics
    /**
     * the maximum memory usage
     */
    double maxMemory = 0;

    /**
     * the time the algorithm started
     */
    long startTimestamp = 0;

    /**
     * the time the algorithm terminated
     */
    long endTimestamp = 0;

    /**
     * the sample numbers
     */
    int popSize = 2000;

    /**
     * pro_size is the number of based on probability
     */
    int proSize = 0;

    /**
     * the maximum iterations of algorithm
     */
    final int iter = 2000;

    /**
     * the pro_size of transactions
     */
    int transCount = 0;

    /**
     * the item whose utility is CUS
     */
    Integer cusItem;

    /**
     * the actual iterations for statistics
     */
    int acIter = 0;

    /**
     * the critical utility of SFUIs
     */
    int CUS = 0;

    /**
     * the maximal frequency of 1-itemset
     */
    int fMax = 0;

    /**
     * the quantile parameter
     */
    final double alpha = 0.2;

    /**
     * the mutation parameter
     */
    final double beta = 0.3;

    /**
     * a map that stores the utility of each item
     */
    Map<Integer, Integer> mapItemToU;

    /**
     * create a map to store the TWU of each item
     */
    Map<Integer, Integer> mapItemToTWU;

    /**
     * create a map to store the frequency of each item
     */
    Map<Integer, Integer> mapItemToF;

    /**
     * the items which has twu value more than CUS
     */
    List<Integer> twuPattern;

    /**
     * writer to write the output file
     */
    BufferedWriter writer = null;

    /**
     * probability vector
     */
    double[] PV;

    /**
     * this inner class represent an item ,its frequency and its utility in a
     * transaction
     */
    static class Pair {
        /**
         * an item
         */
        int item = 0;
        /**
         * a utility value
         */
        int utility = 0;
        /**
         * a frequency value
         */
        int frequency = 0;
    }

    /**
     * this class represent the sample particle
     */
    class Particle {

        /**
         * the sample vector
         */
        BitSet IV;

        /**
         * fitness value of sample
         */
        int frequentFitness;

        /**
         * fitness frequency of sample
         */
        int utilityFitness;

        /**
         * Constructor
         *
         * @param length the particle length
         */
        public Particle(int length) {
            IV = new BitSet(length);
        }

        /**
         * Particle constructors for initialization
         *
         * @param IV              a vector
         * @param frequentFitness frequent fitness
         * @param utilityFitness  utility fitness
         */
        Particle(BitSet IV, int frequentFitness, int utilityFitness) {
            this.IV = IV;
            this.frequentFitness = frequentFitness;
            this.utilityFitness = utilityFitness;
        }

        /**
         * calculate the frequency and utility of the sample particle
         *
         * @param k        the length of itemset
         * @param templist a list for storing the particle
         */
        public void calculateFitness(int k, List<Integer> templist) {
            if (k == 0)
                return;
            int i, p, q, temp, m;
            int sum, u_fitness = 0, f_fitness = 0;
            for (m = 0; m < templist.size(); m++) {
                p = templist.get(m);
                i = 0;
                q = 0;
                temp = 0;
                sum = 0;
                while (q < database.get(p).size() && i < twuPattern.size()) {
                    if (this.IV.get(i)) {
                        //using a loop for solving the unordered datasets
                        for (int t = 0; t < database.get(p).size(); t++) {
                            if (database.get(p).get(t).item == twuPattern.get(i)) {
                                sum = sum + database.get(p).get(t).utility;
                                ++i;
                                ++temp;
                                break;
                            }
                        }
                    } else {
                        ++i;
                    }
                }
                if (temp == k) {
                    u_fitness = u_fitness + sum;
                    f_fitness = f_fitness + 1;
                }
            }
            this.utilityFitness = u_fitness;
            this.frequentFitness = f_fitness;
        }
    }

    /**
     * this class is used to record the SFUIs
     */
    static class SFUI {
        /**
         * an itemset
         */
        String itemset;
        /**
         * the utility fitness
         */
        int U_fitness;
        /**
         * the frequent fitness
         */
        int F_fitness;

        /**
         * the constructor for SFUI
         *
         * @param itemset   an itemset
         * @param U_fitness the utility fitness
         * @param F_fitness the frequent fitness
         */
        public SFUI(String itemset, int U_fitness, int F_fitness) {
            super();
            this.itemset = itemset;
            this.U_fitness = U_fitness;
            this.F_fitness = F_fitness;

        }

    }

    /**
     * Bitmap Item Information Representation structure
     */
    class Item {
        /**
         * the item of transaction
         */
        int item;
        /**
         * to store the transaction
         **/
        BitSet TIDS;

        /**
         * Constructor
         *
         * @param item the item
         */
        public Item(int item) {
            TIDS = new BitSet(transCount);
            this.item = item;
        }
    }

    /**
     * samples
     **/
    List<Particle> population = new ArrayList<>();

    /**
     * A list to store database
     */
    List<List<Pair>> database = new ArrayList<>();

    /**
     * A list to store a transaction
     */
    List<Item> Items;

    /**
     * A list to store in CSFUIList
     */
    List<Particle> CSFUIList = new ArrayList<>();

    /**
     * A list to store SFUIs
     */
    List<SFUI> SFUIList = new ArrayList<>();// the list of SFUIs

    /**
     * Default constructor
     */
    public AlgoSFU_CE() {
    }

    /**
     * Run the algorithm
     *
     * @param input  the input file path
     * @param output the output file path
     * @throws IOException exception if error while writing the file
     */

    public void runAlgorithm(String input, String output) throws IOException {
        // reset
        maxMemory = 0;
        // get SysTime
        startTimestamp = System.currentTimeMillis();
        // initialize writer
        writer = new BufferedWriter(new FileWriter(output));
        // create a map to store the Utility of each item
        mapItemToU = new HashMap<>();
        // create a map to store the TWU of each item
        mapItemToTWU = new HashMap<>();
        // create a map to store the Frequency of each item
        mapItemToF = new HashMap<>();
        // scan the database a first time to calculate the TWU of each item.
        BufferedReader myInput = null;
        String thisLine;
        try {
            // prepare the object for reading the file
            myInput = new BufferedReader(new InputStreamReader(new FileInputStream(input)));
            // for each line (transaction) until the end of file
            while ((thisLine = myInput.readLine()) != null) {
                // if the line is a comment, is empty or is a kind of metadata
                if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@') {
                    continue;
                }
                ++transCount;// Count the number of transactions in the database
                // split the transaction according to the : separator
                String[] split = thisLine.split(":");
                // the first part is the list of items
                String[] items = split[0].split(" ");
                // the second part is the transaction utility
                int transactionUtility = Integer.parseInt(split[1]);
                // the third part is the list of utilities
                String[] utilities = split[2].split(" ");

                // for each item, we count its utility, frequency and TWU
                for (int i = 0; i < items.length; i++) {
                    // convert item to integer
                    Integer item = Integer.parseInt(items[i]);
                    Integer utility = Integer.parseInt(utilities[i]);
                    Integer u = mapItemToU.get(item);
                    Integer f = mapItemToF.get(item);
                    // add the utility of the item
                    u = (u == null) ? utility : u + utility;
                    mapItemToU.put(item, u);
                    f = (f == null) ? 1 : f + 1;
                    mapItemToF.put(item, f);
                    // get the current TWU of that item
                    Integer twu = mapItemToTWU.get(item);
                    // add the utility of the item in the current transaction to
                    // its twu
                    twu = (twu == null) ? transactionUtility : twu + transactionUtility;
                    mapItemToTWU.put(item, twu);
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
        // to calculate CUS
        calculateCUS(mapItemToU, mapItemToF);
        // create a list to store the item whose TWU is no less than CUS
        twuPattern = new ArrayList<>();
        // store the item
        for (Entry<Integer, Integer> vo : mapItemToTWU.entrySet()) {
            Integer item = vo.getKey();
            if (mapItemToTWU.get(item) >= CUS) {
                twuPattern.add(item);
            }
        }
        for (int i = 0; i < twuPattern.size(); i++) {
            if (twuPattern.get(i).equals(cusItem)) {
                BitSet X = new BitSet(twuPattern.size());
                X.set(i);
                Particle sfui = new Particle(X, fMax, CUS);
                // add it to population
                population.add(sfui);
                // insert the tempParticle into SFUIs list
                judge(sfui);
                break;
            }
        }
        // the probability vector
        PV = new double[twuPattern.size()];

        // SECOND DATABASE PASS TO CONSTRUCT THE DATABASE
        // OF 1-ITEMSETS HAVING TWU >= CUS (promising items)
        try {
            // prepare object for reading the file
            myInput = new BufferedReader(new InputStreamReader(new FileInputStream(input)));
            // for each line (transaction) until the end of file
            while ((thisLine = myInput.readLine()) != null) {
                // if the line is a comment, is empty or is a kind of metadata
                if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@') {
                    continue;
                }
                // split the line according to the separator
                String[] split = thisLine.split(":");
                // get the list of items
                String[] items = split[0].split(" ");
                // get the list of utility values corresponding to each item
                // for that transaction
                String[] utilityValues = split[2].split(" ");
                // Create a list to store items and its utility
                List<Pair> revisedTransaction = new ArrayList<>();
                // for each item
                for (int i = 0; i < items.length; i++) {
                    // convert values to integers
                    Integer item = Integer.parseInt(items[i]);
                    // if the item is contained
                    if (mapItemToTWU.get(item) >= CUS) {
                        Pair pair = new Pair();
                        pair.item = Integer.parseInt(items[i]);
                        pair.utility = Integer.parseInt(utilityValues[i]);
                        pair.frequency = 1;
                        // add it
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
        // add item to Items list
        for (Integer tempitem : twuPattern) {
            Items.add(new Item(tempitem));
        }

        // build bitmap
        for (int i = 0; i < database.size(); ++i) {
            for (Item item : Items) {
                for (int k = 0; k < database.get(i).size(); ++k) {
                    if (item.item == database.get(i).get(k).item) {
                        item.TIDS.set(i);
                    }
                }
            }
        }
        // check the memory usage
        checkMemory();
        /* Mine the database iteratively */
        // initialize population
        generatePop();
        // this parameter is to record the difference between the maximum utility and
        // the minimum utility
        int max_min;
        // start iterating
        for (int i = 0; i < iter; i++) {
            // all samples are sorted in descending order of utility
            population.sort((itemset1, itemset2) -> -(itemset1.utilityFitness - itemset2.utilityFitness));
            // max_min is the difference of the maximum and the minimum in the population
            // based on probability
            max_min = population.get(0).utilityFitness
                    - population.get((int) ((1 - beta) * population.size()) - 1).utilityFitness;
            // stopping criterion
            if (max_min == 0) {
                break;
            }
            // increase 1 to iterations
            acIter++;
            // update population and sfuiSet
            update();
        }

        /* output SFUIs in descending order of utility */
        CSFUIList.sort((itemset1, itemset2) -> -(itemset1.utilityFitness - itemset2.utilityFitness));

        for (Particle particle : CSFUIList) {
            insert(particle);
        }
        // record the end time
        endTimestamp = System.currentTimeMillis();
        // check the memory usage again and close the file.
        checkMemory();
        writeOut();
        // close output file
        writer.close();
    }

    /**
     * Method to alculate the CUS
     *
     * @param mapToU the utility of each item
     * @param mapToF the frequency of each item
     */
    public void calculateCUS(Map<Integer, Integer> mapToU, Map<Integer, Integer> mapToF) {
        if (mapToU == null || mapToF == null)
            return;

        // calculate the fMax
        for (Integer item : mapToF.keySet()) {
            if (mapToF.get(item) > fMax) {
                fMax = mapToF.get(item);
            }
        }

        // get CUS of item whose frequency is equal to max_f
        for (Integer item : mapToF.keySet()) {
            if ((mapToF.get(item) == fMax) && CUS < mapToU.get(item)) {
                CUS = mapToU.get(item);
                cusItem = item;
            }
        }
    }

    /**
     * Method to generate the initial population
     */
    private void generatePop() {
        int i, j, k, temp;
        List<Integer> transList;
        for (i = 0; i < popSize - 1; ++i) {
            Particle tempParticle = new Particle(twuPattern.size());
            j = 0;
            k = (int) (Math.random() * twuPattern.size() + 1);
            while (j < k) {
                // roulette select the position of 1 in population
                temp = (int) (Math.random() * twuPattern.size());
                // if the value of this position is 0 then set it
                if (!tempParticle.IV.get(temp)) {
                    j++;
                    tempParticle.IV.set(temp);
                }
            }
            // create a list to record the collection of transactions in which the itemset
            // resides
            transList = new ArrayList<>();
            // Stores the collection of transactions in which the itemset resides
            isRBAIndividual(tempParticle, transList);
            // calculate utility
            tempParticle.calculateFitness(k, transList);
            // insert itemset into itemsets collection of current iteration
            population.add(tempParticle);
            // insert into sets of SFUIs sample particle
            judge(tempParticle);
        }

    }

    /**
     * Method to update the probability vector and generate the new sample
     */
    private void update() {
        List<Integer> mutlist = new ArrayList<>();
        int[] num = new int[twuPattern.size()];
        // count the number of different items in the elite sample
        for (int i = 0; i < alpha * popSize; ++i) {
            for (int j = 0; j < twuPattern.size(); ++j) {
                if (population.get(i).IV.get(j)) {
                    num[j] += 1;
                }
            }
        }
        // clear the last population for next add
        population.clear();
        // calculation probability
        for (int i = 0; i < twuPattern.size(); ++i) {
            PV[i] = (float) (num[i] / (alpha * popSize + 0.0));
            if (PV[i] > 0.5) {
                mutlist.add(i);
            }
        }

        List<Integer> transList;
        int k;
        // pro_size is the number of based on probability
        proSize = mutlist.size() > 0 ? (int) ((1 - beta) * popSize) : popSize;
        // generate a new round of population based on probability
        for (int i = 0; i < proSize; ++i) {
            Particle tempParticle = new Particle(twuPattern.size());
            // A new sample particle is generated
            update_Particle(tempParticle);
            // used to record which transactions the particle appears in
            transList = new ArrayList<>();
            // find out whether the itemset combination exists in the database
            if (isRBAIndividual(tempParticle, transList)) {
                // the pro_size of itemset
                k = tempParticle.IV.cardinality();
                // calculate the utility of the itemset
                tempParticle.calculateFitness(k, transList);
                // add it to population
                population.add(tempParticle);
                // insert the tempParticle into SFUIs list
                judge(tempParticle);
            }
        }

        /* Using variation to generate some new samples */
        mutation(mutlist);

    }

    /**
     * This is the method to generate a sample particle based on probability
     *
     * @param temp the new particle
     */
    private void update_Particle(Particle temp) {
        for (int i = 0; i < twuPattern.size(); ++i) {
            if (Math.random() < PV[i]) {
                temp.IV.set(i);
            }
        }
    }

    /**
     * this method is to generate the some new sample with mutation
     *
     * @param MRList mutation reference list
     */
    private void mutation(List<Integer> MRList) {
        if (MRList.size() == 0) {
            return;
        }
        int j, k, temp;
        List<Integer> transList;
        for (int i = 0; i < popSize - proSize; i++) {
            Particle tempParticle = new Particle(twuPattern.size());
            j = 0;
            k = (int) (Math.random() * MRList.size() + 1);
            while (j < k) {
                // roulette select the position of 1 in MRList
                temp = (int) (Math.random() * MRList.size());
                temp = MRList.get(temp);
                // if it is 0 in this position, 1 is inserted in this position
                if (!tempParticle.IV.get(temp)) {
                    j++;
                    tempParticle.IV.set(temp);
                }
            }
            // Stores the collection of transactions in which the itemset resides
            transList = new ArrayList<>();
            // find out whether the itemset combination exists in the database
            if (isRBAIndividual(tempParticle, transList)) {
                // calculate utility
                tempParticle.calculateFitness(k, transList);
                // insert itemset into itemsets collection of current iteration
                population.add(tempParticle);
                // insert the tempParticle into SFUIs list
                judge(tempParticle);
            }
        }
    }

    /**
     * judge the temp particle ,if it is not dominated then inset it into
     * sfuiParticleList
     *
     * @param temp the temp particle for judge
     */
    private void judge(Particle temp) {

        // if sfuiList is null then add it
        if (temp.utilityFitness < CUS) {
            return;
        }
        // if the item does not exist in the sfuiList, add it directly
        if (CSFUIList.isEmpty()) {
            CSFUIList.add(temp);
            return;
        }
        // if tmp is dominated by or equal to particle in sfuiList then return
        for (Particle particle : CSFUIList) {
            if ((particle.frequentFitness >= temp.frequentFitness && particle.utilityFitness >= temp.utilityFitness))
                return;
        }
        // if tempSFUI is dominated by tmp then remove it
        CSFUIList.removeIf(tempSFUI -> (tempSFUI.frequentFitness < temp.frequentFitness
                && tempSFUI.utilityFitness <= temp.utilityFitness)
                || (tempSFUI.frequentFitness <= temp.frequentFitness && tempSFUI.utilityFitness < temp.utilityFitness));
        // add it into sfuiList
        CSFUIList.add(temp);
    }

    /**
     * It is used to get the collection of transactions in which the itemset
     * resides. If the itemset itself is unreasonable, it is automatically
     * fine-tuned during the calculation. Make it reasonable and get the set of
     * transactions in which it is located
     *
     * @param tempParticle the temp particle
     * @param list         the transactions which the tempParticle appears
     * @return return to tempParticle is it a normal example
     */
    public boolean isRBAIndividual(Particle tempParticle, List<Integer> list) {
        List<Integer> templist = new ArrayList<>();
        // add tempParticle to templist
        for (int i = 0; i < twuPattern.size(); ++i) {
            if (tempParticle.IV.get(i)) {
                templist.add(i);
            }
        }
        // if templist is null then return false
        if (templist.size() == 0) {
            return false;
        }
        // clone the BitSet of tempParticle
        BitSet tempBitSet = (BitSet) Items.get(templist.get(0)).TIDS.clone();
        // create a midBitSet for transforming
        BitSet midBitSet = (BitSet) tempBitSet.clone();
        for (int i = 1; i < templist.size(); ++i) {
            tempBitSet.and(Items.get(templist.get(i)).TIDS);
            if (tempBitSet.cardinality() != 0) {
                midBitSet = (BitSet) tempBitSet.clone();
            } else {
                tempBitSet = (BitSet) midBitSet.clone();
                tempParticle.IV.clear(templist.get(i));
            }
        }
        // if tempBitSet.cardinality() == 0 then suggest that not exists any itemset
        // like tempBitset in database
        // else add tempBitSet to list
        if (tempBitSet.cardinality() == 0) {
            return false;
        } else {
            for (int m = 0; m < tempBitSet.length(); ++m) {
                if (tempBitSet.get(m)) {
                    list.add(m);
                }
            }
            return true;
        }
    }

    /**
     * Method to inseret tempItemset to sfuiSet
     *
     * @param tempParticle the itemset to be inserted
     */
    private void insert(Particle tempParticle) {
        int i;
        StringBuilder temp = new StringBuilder();
        for (i = 0; i < twuPattern.size(); i++) {
            if (tempParticle.IV.get(i)) {
                temp.append(twuPattern.get(i));
                temp.append(' ');
            }
        }
        // sfuiSet is null
        if (SFUIList.size() == 0) {
            SFUIList.add(new SFUI(temp.toString(), tempParticle.utilityFitness, tempParticle.frequentFitness));
        } else {
            // sfuiSet is not null, judge whether exist an itemset in sfuiSet
            // same with tempParticle
            for (i = 0; i < SFUIList.size(); i++) {
                if (temp.toString().equals(SFUIList.get(i).itemset)) {
                    break;
                }
            }
            // if not exist same itemset in sfuiSet with tempParticle,insert it
            // into sfuiSet
            if (i == SFUIList.size())
                SFUIList.add(new SFUI(temp.toString(), tempParticle.utilityFitness, tempParticle.frequentFitness));
        }
    }

    /**
     * Method to write a high utility itemset to the output file.
     *
     * @throws IOException throw input and output error
     */
    private void writeOut() throws IOException {
        for (SFUI sfui : SFUIList) {
            String buffer = sfui.itemset + "#SUP: " + sfui.F_fitness + " #UTIL: " + sfui.U_fitness;
            writer.write(buffer);
            writer.newLine();
        }
    }

    /**
     * Method to check the memory usage and keep the maximum memory usage.
     */
    private void checkMemory() {
        // get the current memory usage
        double currentMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024d / 1024d;
        // if higher than the maximum until now
        if (currentMemory > maxMemory) {
            // replace the maximum with the current memory usage
            maxMemory = currentMemory;
        }
    }

    /**
     * Print statistics about the latest execution to System.out.
     */
    public void printStats() {
        System.out.println("=============  SFU-CE ALGORITHM v2.51  =============");
        System.out.println(" Total time                 : " + (endTimestamp - startTimestamp) + " ms");
        System.out.println(" Memory                     : " + maxMemory + " MB");
        System.out.println(" Pattern count              : " + SFUIList.size());
        System.out.println(" Actual number of iterations: " + acIter);
        System.out.println("===================================================");
    }
}
