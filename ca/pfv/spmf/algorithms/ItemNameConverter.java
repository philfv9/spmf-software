package ca.pfv.spmf.algorithms;

import java.util.Arrays;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger
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
 * This class is used to rename items in a datasets.
 * It is used by several algorithms that use a total order, which
 * is different from the alphabetical or lexicographical order.
 * By renaming items with consecutive names, it allows several optimization
 * such as faster comparison between items by using the > < == operators.
 * 
 * v2.66: updated to use arrays instead of hashmap for improved speed.
 *
 * @author Philippe Fournier-Viger, 2015
 */
public class ItemNameConverter {

    /** This structure is used for converting new names to old names.
     *  The i-th position contains the old item name corresponding to the new name "i" **/
    int[] newNamesToOldNames;

    /** This structure is used for converting old names to new names.
     *  Uses a flat int array instead of a HashMap for faster access.
     *  Index is the old name, value is the new name. 0 means unmapped. **/
    int[] oldNamesToNewNames;

    /** Tracks which old names have been assigned, parallel to oldNamesToNewNames **/
    boolean[] oldNameExists;

    /** the maximum old item name (used to size the reverse map) **/
    int maxOldName;
    
    /** the maximum new item name (used to size the forward map) **/
    int maxNewName;

    /** this variable is the next new name that will be given **/
    int currentIndex;

    /**
     * Constructor
     * @param itemCount we have to specify the number of items in the dataset.
     */
    public ItemNameConverter(int itemCount) {
        // initialize the internal data structures with default max size
        // assumes old names won't exceed itemCount * 2
        int estimatedMaxOldName = itemCount * 2;
        newNamesToOldNames = new int[itemCount + 1];
        oldNamesToNewNames = new int[estimatedMaxOldName + 1];
        oldNameExists = new boolean[estimatedMaxOldName + 1];
        this.maxOldName = estimatedMaxOldName;
        this.maxNewName = itemCount;
        currentIndex = 1;
    }

    /**
     * Constructor
     * @param itemCount we have to specify the number of items in the dataset.
     * @param firstItemName the first "new name" to be used. This can be used to start from 0, instead of 1 (the default).
     */
    public ItemNameConverter(int itemCount, int firstItemName) {
        // initialize the internal data structures with default max size
        // assumes old names won't exceed itemCount * 2
        int estimatedMaxOldName = itemCount * 2;
        newNamesToOldNames = new int[itemCount + firstItemName];
        oldNamesToNewNames = new int[estimatedMaxOldName + 1];
        oldNameExists = new boolean[estimatedMaxOldName + 1];
        this.maxOldName = estimatedMaxOldName;
        this.maxNewName = itemCount + firstItemName - 1;
        currentIndex = firstItemName;
    }

    /**
     * Expand the internal arrays if the old name is larger than current capacity.
     * @param oldName the old name that needs to fit
     */
    private void ensureOldNameCapacity(int oldName) {
        if (oldName > maxOldName) {
            // grow to accommodate the new old name, with some extra room
            int newMaxOldName = Math.max(oldName, maxOldName * 2);
            oldNamesToNewNames = Arrays.copyOf(oldNamesToNewNames, newMaxOldName + 1);
            oldNameExists = Arrays.copyOf(oldNameExists, newMaxOldName + 1);
            maxOldName = newMaxOldName;
        }
    }

    /**
     * Expand the new names array if needed.
     * @param newName the new name that needs to fit
     */
    private void ensureNewNameCapacity(int newName) {
        if (newName > maxNewName) {
            // grow to accommodate the new new name, with some extra room
            int newMaxNewName = Math.max(newName, maxNewName * 2);
            newNamesToOldNames = Arrays.copyOf(newNamesToOldNames, newMaxNewName + 1);
            maxNewName = newMaxNewName;
        }
    }

    /**
     * This method takes an old name as parameter and create a new name.
     * @param oldName the old name
     * @return the new name
     */
    public int assignNewName(int oldName) {
        // expand arrays if needed
        ensureOldNameCapacity(oldName);
        
        // we give the new name "currentIndex"
        int newName = currentIndex;
        
        // expand new names array if needed
        ensureNewNameCapacity(newName);
        
        // store the mapping old -> new
        oldNamesToNewNames[oldName] = newName;
        oldNameExists[oldName] = true;
        // store the mapping new -> old
        newNamesToOldNames[newName] = oldName;
        // increment so the next call gets the next new name
        currentIndex++;
        // we return the new name
        return newName;
    }

    /**
     * Convert an old name to the corresponding new name.
     * @param oldName an old name
     * @return the corresponding new name or 0, if no new name exists for that old name.
     */
    public int toNewName(int oldName) {
        if (oldName < 0 || oldName > maxOldName) {
            return 0;
        }
        return oldNamesToNewNames[oldName];
    }

    /**
     * Convert an old name to the corresponding new name.
     * @param oldName an old name
     * @return the corresponding new name or 0, if no new name exists for that old name.
     */
    public int toNewName(Integer oldName) {
        if (oldName < 0 || oldName > maxOldName) {
            return 0;
        }
        return oldNamesToNewNames[oldName];
    }

    /**
     * Convert a new name to the corresponding old name.
     * @param newName a new name
     * @return the corresponding old name
     */
    public int toOldName(int newName) {
        return newNamesToOldNames[newName];
    }

    /**
     * Is the item existing?
     * @param item an item
     * @return true if the item exist, otherwise false
     */
    public boolean isOldItemExisting(int item) {
        // check bounds first, then the existence array
        return item >= 0 && item <= maxOldName && oldNameExists[item];
    }
}