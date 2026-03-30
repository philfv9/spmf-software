/* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* It was obtained from the LAC library under the GNU GPL license and adapted for SPMF.
* @Copyright original version LAC 2019   @copyright of modifications SPMF 2021
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
*/
package ca.pfv.spmf.algorithms.classifiers.adt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;

/**
 * Class used to mine rules from the training dataset
 * @see AlgoADT
 */
public class RuleExtractorADT {
	
    /**
     * Minimum value for the confidence measure
     */
    private double minConf;

    /**
     * Dataset used for the training phase
     */
    private Dataset dataset;

    /**
     * Constructor
     * 
     * @param training dataset used as training
     * @param minconf minimum confidence threshold
     */
    public RuleExtractorADT(Dataset training, double minConf) {
        this.dataset = training;
        this.minConf = minConf;
    }

    /**
     * Obtain rules from the training dataset
     * 
     * @return the rules
     */
    public List<RuleADT> run() {
    	// Generate rules
    	List<RuleADT> rules = this.generateK();

        List<RuleADT> candidates = new ArrayList<RuleADT>();
        Map<RuleADT, Long> candidatesH = new HashMap<RuleADT, Long>();

        // For each rule
        for (RuleADT rule : rules) {
            List<Short> antecedent = rule.getAntecedent();

            // For each item of the antecedent
            for (int j = 0; j < antecedent.size(); j++) { 
            	// Remove the item at the j-th position  
            	List<Short> newAntecedent = new ArrayList<Short>(antecedent);
                newAntecedent.remove(j);                           // !!!!! might be Inefficient !!!

                RuleADT newRule = new RuleADT(newAntecedent, rule.getKlass());
                if (!candidates.contains(newRule)) {
                    candidates.add(newRule);
                }
            }
        }
        // Evaluate candidates and count support
        for (RuleADT rule : candidates){
            rule.calculateSupports(dataset);
        }
        candidates.removeIf(rule -> rule.getConfidence() < minConf);  // !!! might be inefficient !!!
        rules.addAll(candidates);

        do {
            int k = candidates.get(0).getAntecedent().size() - 1;
            for (RuleADT candidate : candidates) {
                List<Short> antecedent = candidate.getAntecedent();

                for (int j = 0; j < antecedent.size(); j++) {
                    List<Short> newAntecedent = new ArrayList<Short>(antecedent);
                    newAntecedent.remove(j);                         // !!!!! might be Inefficient !!!

                    RuleADT newRule = new RuleADT(newAntecedent, candidate.getKlass());
                    Long count = candidatesH.getOrDefault(newRule, 0L);

                    candidatesH.put(newRule, count + 1);
                }
            }

            // To consider one candidate, it must occur at least k times. Where k is the
            // size
            // of the antecedent being mined
            candidates = candidatesH.entrySet().stream().filter(candidate -> candidate.getValue() >= k)
                    .map(candidate -> candidate.getKey()).collect(Collectors.toList());

            candidatesH.clear();
            // Evaluate candidates and count support
            for (RuleADT rule : candidates) {
                rule.calculateSupports(dataset);
            }
            candidates.removeIf(rule -> rule.getConfidence() < minConf);  /// !!! might be inefficient !!!
            rules.addAll(candidates);
            if (k == 1) {
            	break;
            }
        } while (!candidates.isEmpty() && candidates.get(0).getAntecedent().size() > 1);

        return rules;
    }

    /**
     * Generates rules of size k
     * 
     * @return candidate rules of size k
     */
    private List<RuleADT> generateK() {
        List<RuleADT> rules = new ArrayList<RuleADT>();

        Map<List<Short>, List<Integer>> mapAntecedentToIndex = new HashMap<List<Short>, List<Integer>>();

        for (Instance instance : dataset.getInstances()) {
            
			Short[] antecedent = Arrays.copyOfRange(instance.getItems(), 0,
                    this.dataset.getAttributes().size());
            List<Short> antecedentArray = new ArrayList<Short>(Arrays.asList(antecedent));
            short klass = instance.getKlass();

            RuleADT rule = new RuleADT(antecedent, klass);

            if (mapAntecedentToIndex.containsKey(antecedentArray) && rules.contains(rule)) {
            	List<Integer> indexAntecedents = mapAntecedentToIndex.getOrDefault(antecedent,
                        new ArrayList<Integer>());
                int index = rules.lastIndexOf(rule);

                rules.get(index).incrementSupportRule();
                rules.get(index).incrementSupportAntecedent();
                for (int j = 0; j < indexAntecedents.size(); j++) {
                    index = indexAntecedents.get(j);
                    rules.get(index).incrementSupportAntecedent();
                }
                mapAntecedentToIndex.put(antecedentArray, indexAntecedents);
            } else {
                rules.add(rule);
                rule.incrementSupportAntecedent();
                rule.incrementSupportRule();

                List<Integer> indexAntecedents = mapAntecedentToIndex.getOrDefault(antecedent,
                        new ArrayList<Integer>());
                indexAntecedents.add(rules.size() - 1);

                mapAntecedentToIndex.put(antecedentArray, indexAntecedents);
            }
        }

        // Filter only for those rules whose confidence is greater than the threshold
        rules.removeIf(rule -> rule.getConfidence() < minConf);   //  !!  might be inefficient !!

        return rules;
    }
}
