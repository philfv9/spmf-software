package ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items.creators.AbstractionCreator;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items.creators.ItemAbstractionPairCreator;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items.patterns.Pattern;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items.patterns.PatternCreator;
//import ca.pfv.spmf.test.MainTestGSP_saveToMemory;
/**
 * Inspired in SPMF
 * Implementation of a sequence database, where each sequence is implemented
 * as an array of integers and should have a unique id.
 * See examples in /test/ directory for the format of input files.
 *
 * Copyright (c) 2013 Antonio Gomariz Peñalver
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
 * @author agomariz
 */
public class SequenceDatabase {
    
    private AbstractionCreator abstractionCreator;
    
    /**
     * Map to associate the frequent items with the 1-patterns composed by themselves
     */
    private Map<Item, Pattern> frequentItems = new HashMap<Item, Pattern>();
    
    /**
     * List of sequences that compose the database
     */
    private List<Sequence> sequences = new ArrayList<Sequence>();
    
    /**
     * Instance of ItemFactory
     */
    private ItemFactory<Integer> itemFactory = new ItemFactory<Integer>();
    
    /**
     * Instance of PatternCreator
     */
    private PatternCreator patternCreator = PatternCreator.getInstance();
    
    // length
    public static List<Sequence>primarysequences = new ArrayList<Sequence>();
    
    public SequenceDatabase(AbstractionCreator abstractionCreator) {
        this.abstractionCreator = abstractionCreator;
    }

    /**
     * It loads the database contained in the file path given as parameter.
     * Besides, all the frequent 1-patterns are identified and the original database
     * is updated by removing the non-frequent items
     * @param path File path of the original database
     * @param minSupportAbsolute Minimum absolute support
     * @param gamma 
     * @param beta 
     * @param alpha
     * @throws IOException 
     */
    public void loadFile(String path, double minSupportAbsolute, double alpha, double beta, double gamma) throws IOException {
    	
    	// alpha beta gramma
    	int max_sequence_length = 398;
        double k1 = 13.2667;
        double k3 = 0.14;
        double each_vality_constraint_sup;
        double each_length_constraint_sup = 0;
        String str3;
        String strflag;
        int timeflag;
    	
        String thisLine;
        BufferedReader myInput = null;
        try {
            FileInputStream fis = new FileInputStream(new File(path));
            myInput = new BufferedReader(new InputStreamReader(fis));
            while ((thisLine = myInput.readLine()) != null) {
            	if (thisLine.charAt(0) != '#' && thisLine.charAt(0) != '%'
						&& thisLine.charAt(0) != '@') {
                    addSequence(thisLine.split(" "));
                }
            }
            double minSupRelative = (int) Math.ceil(minSupportAbsolute * sequences.size());
            Set<Item> items = frequentItems.keySet();
            Set<Item> itemsToRemove = new HashSet<Item>();
            for (Item item : items) {
                Pattern pattern = frequentItems.get(item);
                //length
                double local_total_length_constraint_sup = 0;
                // length
                pattern.total_length_constraint_sup = local_total_length_constraint_sup;
                // discrete
                pattern.total_discrete_constraint_sup = pattern.getSupport();
                // vality
                for (int m = pattern.getAppearingIn().nextSetBit(0); m >=0; m=pattern.getAppearingIn().nextSetBit(m+1)) {
                	// length
                	each_length_constraint_sup = Math.exp((-1.0*k1)*(1.0/max_sequence_length)*primarysequences.get(m).getLength());
                	local_total_length_constraint_sup = local_total_length_constraint_sup + each_length_constraint_sup;
                	// time
                	int low_num =0 ;
                	for(int k=0 ; k<sequences.get(m).size();k++) {
                		if(sequences.get(m).get(k).containItem(item)) {
                			str3 = Integer.toString((int)sequences.get(m).get(k).getTimestamp());
                        	strflag = str3.substring(str3.length()-1, str3.length());
                        	timeflag = Integer.parseInt(strflag);
            				if(timeflag <1)
            				{
            					low_num	++;
            				}
                		}
                	}
                	each_vality_constraint_sup = Math.exp(-1.0*k3*low_num);
                	pattern.total_vality_constraint_sup += each_vality_constraint_sup;
               	}
                
                // length
                pattern.total_length_constraint_sup = local_total_length_constraint_sup;
                // discrete
                pattern.total_discrete_constraint_sup = pattern.getSupport();

                pattern.total_three_constraint_integration_sup = alpha * pattern.total_length_constraint_sup+
                		beta*pattern.total_discrete_constraint_sup+gamma*pattern.total_vality_constraint_sup;
                // Integeration
                if (pattern.total_three_constraint_integration_sup < minSupRelative) {
                    itemsToRemove.add(item);
                }
            }
            for (Item nonFrequent : itemsToRemove) {
                frequentItems.remove(nonFrequent);
            }

            shrinkDatabase(frequentItems.keySet());
        } catch (Exception e) {
        } finally {
            if (myInput != null) {
                myInput.close();
            }
        }
    }
    
    /**
     * It creates and addes the sequence found in the array of Strings
     * @param integers 
     */
    public void addSequence(String[] integers) {
        ItemAbstractionPairCreator creadorPares = ItemAbstractionPairCreator.getInstance();
        long timestamp;
        Sequence sequence = new Sequence(sequences.size());
        
        // length
        Sequence primarysequence = new Sequence(sequences.size());
        
        Itemset itemset = new Itemset();
        int start = 0;

        for (int i = start; i < integers.length; i++) {
            if (integers[i].codePointAt(0) == '<') {  // Timestamp
                String value = integers[i].substring(1, integers[i].length() - 1);
                timestamp = Long.parseLong(value);
                itemset.setTimestamp(timestamp);
            } else if (integers[i].equals("-1")) { // indica el final de un itemset
                long time = itemset.getTimestamp() + 1;
                sequence.addItemset(itemset);
                // length
                primarysequence.addItemset(itemset);
                
                itemset = new Itemset();
                itemset.setTimestamp(time);
            } else if (integers[i].equals("-2")) { // indica el final de la secuencia
                sequences.add(sequence);
                //length
                primarysequences.add(primarysequence);
            } else {
                // extract the value for an item
                Item item = itemFactory.getItem(Integer.parseInt(integers[i]));
                Pattern pattern = frequentItems.get(item);
                if (pattern == null) {
                    pattern = patternCreator.createPattern(creadorPares.getItemAbstractionPair(item, abstractionCreator.CreateDefaultAbstraction()));
                    frequentItems.put(item, pattern);
                }
                pattern.addAppearance(sequence.getId());
                itemset.addItem(item);

            }
        }
    }
    // length
    public List<Sequence> getprimarySequences(){
    	return sequences;
    }
    // length
    public void addprimarySequences(Sequence primarysequence) {
    	primarysequences.add(primarysequence);
    }

    public void addSequence(Sequence sequence) {
        sequences.add(sequence);
    }

    @Override
    public String toString() {
        StringBuilder r = new StringBuilder();
        for (Sequence sequence : sequences) {
            r.append(sequence.getId());
            r.append(":  ");
            r.append(sequence.toString());
            r.append('\n');
        }
        return r.toString();
    }

    public int size() {
        return sequences.size();
    }

    public List<Sequence> getSequences() {
        return sequences;
    }

    /**
     * It returns the frequent 1-patterns
     * @return the list of frequent items.
     */
    public List<Pattern> frequentItems() {
        List<Pattern> celdasDeItemsFrecuentes = new ArrayList<Pattern>(frequentItems.values());
        Collections.sort(celdasDeItemsFrecuentes);
        return celdasDeItemsFrecuentes;
    }

    /**
     * It return a map where are associated each frequent item with the 
     * 1-pattern composed by itself
     * @return the map
     */
    public Map<Item, Pattern> getFrequentItems() {
        return frequentItems;
    }

    public void clear() {
        if (sequences != null) {
            sequences.clear();
        }
        sequences = null;
        if (frequentItems != null) {
            frequentItems.clear();
        }
        frequentItems = null;
        itemFactory = null;
    }

    /**
     * Reduce the original database, removing the items given in the 
     * parameter set
     * @param keySet 
     */
    private void shrinkDatabase(Set<Item> keySet) {
        for (Sequence sequence : sequences) {
            for (int i = 0; i < sequence.size(); i++) {
                Itemset itemset = sequence.get(i);
                for (int j = 0; j < itemset.size(); j++) {
                    Item item = itemset.get(j);
                    if (!keySet.contains(item)) {
                        sequence.remove(i, j);
                        j--;
                    }
                }
                if (itemset.size() == 0) {
                    sequence.remove(i);
                    i--;
                }
            }
        }
    }
}
