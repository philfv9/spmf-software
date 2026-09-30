package ca.pfv.spmf.algorithms.sequentialpatterns.prefixSpan_AGP_fast;

import ca.pfv.spmf.datastructures.collections.list.ArrayListObject;
import ca.pfv.spmf.datastructures.collections.list.ListObject;
import ca.pfv.spmf.datastructures.collections.map.MapIntToInt;
/*
 * Copyright Antonio Gomariz Peñalver 2013
 * 
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */
/**
 * Implementation of a sequence.
 * A sequence is defined as a list of itemsets and can have an identifier.
 * 
 * Copyright Antonio Gomariz Peñalver 2013
 * 
 * @author agomariz
 */
public class Sequence {

    /**
     * Counter that has the total items contained in the sequence.
     */
    private int numberOfItems = 0;
    /**
     * Itemsets that compose the sequence
     */
    private final ListObject<Itemset> itemsets = new ArrayListObject<Itemset>();
    /**
     * Sequence identifier
     */
    private int id;

    /**
     * Standard constructor for a sequence
     * @param id The sequence identifier
     */
    public Sequence(int id) {
        this.id = id;
    }

    /**
     * It adds an itemset in the last position of the sequence
     * @param itemset the itemset to add
     */
    public void addItemset(Itemset itemset) {
        itemsets.add(itemset);
        numberOfItems += itemset.size();
    }

    /**
     * It adds an item to the last itemset of the sequence
     * @param item the item to add
     */
    public void addItem(int item){
        itemsets.get(size()-1).addItem(item);
        numberOfItems++;
    }

    /**
     * It adds an item in the specified itemset of the sequence
     * @param indexItemset Itemset index where we want to insert the item
     * @param item The item that we want to insert
     */
    public void addItem(int indexItemset, int item){
        itemsets.get(indexItemset).addItem(item);
        numberOfItems++;
    }

    /**
     * It adds an item in the specified item position of the specified itemset of the sequence
     * @param indexItemset Itemset index where we want to insert the item
     * @param indexItem Item index where we want to insert the item
     * @param item The item that we want to insert
     */
    public void addItem(int indexItemset, int indexItem, int item){
        itemsets.get(indexItemset).addItem(indexItem,item);
        numberOfItems++;
    }

    /**
     * It removes the specified itemset from the sequence
     * @param indexItemset Itemset index of itemset that we want to remove
     * @return the itemset removed
     */
    public Itemset remove(int indexItemset){
        Itemset itemset = itemsets.get(indexItemset);
        itemsets.removeAt(indexItemset);
        numberOfItems-=itemset.size();
        return itemset;
    }

    /**
     * It removes the specified item from the specified itemset in the sequence
     * @param indexItemset Itemset index from where we want to remove the item
     * @param indexItem Item index that we want to remove
     * @return the item that was removed
     */
    public int remove(int indexItemset, int indexItem){
        numberOfItems--;
        return itemsets.get(indexItem).removeItemAt(indexItem);
    }

    /**
     * It removes the specified item from the specified itemset in the sequence
     * @param indexItemset Itemset index from where we want to remove the item
     * @param item Item that we want to remove
     */
    public void removeItem(int indexItemset, int item){
        itemsets.get(indexItemset).removeItem(item);
        numberOfItems--;
    }

    /**
     * It clones a sequence
     * @return the clone sequence
     */
    public Sequence cloneSequence() {
        Sequence sequence = new Sequence(getId());
        for (int i = 0; i < itemsets.size(); i++) {
            sequence.addItemset(itemsets.get(i).cloneItemset());
        }
        return sequence;
    }

    /**
     * Get the string representation of this sequence
     * @return the string representation
     */
    @Override
    public String toString() {
        StringBuilder r = new StringBuilder("");
        for (int i = 0; i < itemsets.size(); i++) {
            Itemset itemset = itemsets.get(i);
            r.append('{');
            int[] rawItems = itemset.getRawItems();
            for (int j = 0; j < itemset.size(); j++) {
                r.append(rawItems[j]);
                r.append(' ');
            }
            r.append('}');
        }
        return r.append("    ").toString();
    }

    /**
     * It returns the sequence ID
     * @return  the sequence id
     */
    public int getId() {
        return id;
    }

    /**
     * It gets the list of itemsets in this sequence
     * @return the list of itemsets
     */
    public ListObject<Itemset> getItemsets() {
        return itemsets;
    }

    /**
     * It gets the itemset of this sequence at a given index position
     * @param index the itemset index in which we are interested in
     * @return the itemset
     */
    public Itemset get(int index) {
        return itemsets.get(index);
    }

    /**
     * It returns the number of itemsets that the sequence has
     * @return the number of itemsets
     */
    public int size() {
        return itemsets.size();
    }

    /**
     * It returns the number of items that the sequence has
     * @return the number of items.
     */
    public int length() {
        return numberOfItems;
    }

    /**
     * It clones a sequence, translating every remaining item to its dense
     * id and dropping the non-frequent ones (see AlgoPrefixSpan_AGP's item
     * renumbering).
     * @param itemRemap map from original item id to its dense id
     * @return the clone
     */
    public Sequence cloneSequenceMinusItems(MapIntToInt itemRemap) {
        Sequence sequence = new Sequence(getId());
        for (int i = 0; i < itemsets.size(); i++) {
            Itemset newItemset = itemsets.get(i).cloneItemSetMinusItems(itemRemap);
            if (newItemset.size() != 0) {
                sequence.addItemset(newItemset);
            }
        }
        return sequence;
    }

    /**
     * Sequence ID setter
     * @param id 
     */
    public void setID(int id) {
        this.id = id;
    }
}