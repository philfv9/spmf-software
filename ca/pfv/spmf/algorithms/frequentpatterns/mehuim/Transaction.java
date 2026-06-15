package ca.pfv.spmf.algorithms.frequentpatterns.mehuim;


/**
 * This class represents a transaction
 *
 * @author Philippe Fournier-Viger
 */
public class Transaction {

    /** a buffer to store items of an itemset*/
    public static int[] tempItems = new int[2000];
    /** a buffer to store utilities of an itemset */
    public static int[] tempUtilities = new int[2000];

    /** an array of items representing the transaction */
    int[] items;
    /** an array of utilities associated to items of the transaction */
    int[] utilities;

    int transactionUtility;

    public Transaction() {
        ;
    }

    /**
     * Constructor of a transaction
     * @param items the items in the transaction
     * @param utilities the utilities of item in this transaction
     * @param transactionUtility the transaction utility
     */

    public Transaction(int[] items, int[] utilities, int transactionUtility) {
        this.items = items;
        this.utilities = utilities;
        this.transactionUtility = transactionUtility;
    }

    /**
     * Get a string representation of this transaction
     */
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < items.length; i++) {
            buffer.append(items[i]);
            buffer.append("[");
            buffer.append(utilities[i]);
            buffer.append("] ");
        }
        buffer.append(" Remaining Utility:" +transactionUtility);
        // buffer.append(" Prefix Utility:" + prefixUtility);
        return buffer.toString();
    }


    /**
     * Get the array of items in this transaction
     * @return array of items
     */
    public int[] getItems() {
        return items;
    }


    /**
     * Get the array of utilities in this transaction
     * @return array of utilities
     */
    public int[] getUtilities() {
        return utilities;
    }


    /**
     * This method removes unpromising items from the transaction and at the same time rename
     * items from old names to new names
     * @param oldNamesToNewNames An array indicating for each old name, the corresponding new name.
     */
    public void removeUnpromisingItems(int[] oldNamesToNewNames) {
        // In this method, we used buffers for temporary storing items and their utilities
        // (tempItems and tempUtilities)
        // This is for memory optimization.

        // for each item
        int i = 0;
        for(int j=0; j< items.length;j++) {
            int item = items[j];

            // Convert from old name to new name
            int newName = oldNamesToNewNames[item];

            // if the item is promising (it has a new name)
            if(newName != 0) {
                // copy the item and its utility
                tempItems[i] = newName;
                tempUtilities[i] = utilities[j];
                i++;
            }else{
                // else subtract the utility of the item
                transactionUtility -= utilities[j];
            }
        }
        // copy the buffer of items back into the original array
        this.items = new int[i];
        System.arraycopy(tempItems, 0, this.items, 0, i);

        // copy the buffer of utilities back into the original array
        this.utilities = new int[i];
        System.arraycopy(tempUtilities, 0, this.utilities, 0, i);

        // Sort by increasing TWU values
        insertionSort(this.items, this.utilities);
    }

    /**
     * Implementation of Insertion sort for integers.
     * This has an average performance of O(n log n)
     * @param items array of integers
     */
    public static void insertionSort(int [] items,  int[] utitilies){
        for(int j=1; j< items.length; j++){
            int itemJ = items[j];
            int utilityJ = utitilies[j];
            int i = j - 1;
            for(; i>=0 && (items[i]  > itemJ); i--){
                items[i+1] = items[i];
                utitilies[i+1] = utitilies[i];
            }
            items[i+1] = itemJ;
            utitilies[i+1] = utilityJ;
        }
    }


}
