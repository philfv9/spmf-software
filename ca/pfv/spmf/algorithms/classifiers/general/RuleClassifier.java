/**
 * This file is part of Library for Associative Classification (LAC)
 *
 * Copyright (C) 2019
 *   
 * LAC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details. You should have 
 * received a copy of the GNU General Public License along with 
 * this program.  If not, see http://www.gnu.org/licenses/
 */
package ca.pfv.spmf.algorithms.classifiers.general;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.algorithms.classifiers.data.Attribute;
import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.data.StringDataset;

/**
 * Class representing a rule-based classifier
 * @see Classifier
 */
public class RuleClassifier extends Classifier implements Serializable{
	
    /**
	 *  UID
	 */
	private static final long serialVersionUID = -3339753093228301309L;

	/** Rules in this classifier */
    protected List<Rule> rules;
    
    /** Name of this classifier */
    protected String name;

    /**
     * Main constructor
     */
    public RuleClassifier(String name) {
        this.rules = new ArrayList<Rule>();
        this.name = name;
    }
    
    public String getName() {
    	return name;
    } 

    /**
     * Add a new rule to the classifier
     * 
     * @param rule to be added in the classifier
     */
    public void add(Rule rule) {
        this.rules.add(rule);
    }

    /**
     * Return the rules forming the classifier
     * 
     * @return rules forming the classifier
     */
    public List<Rule> getRules() {
        return this.rules;
    }

    /**
     * Performs a prediction on a instance. By default it iterates each rule and
     * assign the first fired rules class
     * 
     * @param rawExample instance to perform prediction
     * @return the assigned class using the current classifier
     */
    public short predict(Instance rawExample) {
        Short[] example = rawExample.getItems();

        // Check if some rule matchs
        for (Rule rule : rules) {
            if (rule.matching(example)) {
                return rule.getKlass();
            }
        }
        // No rule was fired
        return NOPREDICTION;
    }

    /**
     * Number of rules forming the classifier
     * 
     * @return number of rules
     */
    public int getNumberRules() {
        return this.rules.size();
    }
    
    /**
     * Get the average number of attributes in all the rules
     * 
     * @return the average number of attributes in all the classifier
     */
    public double getAverageNumberAttributes() {
        double avg = 0;
        for (Rule rule : rules) {
            avg += rule.getAntecedent().size();
        }
        return avg / getNumberRules();
    }
    
    /*
     * Write to disk
     * @param classifier a rule-based classifier
     * @param outputPath an output file path
     */
    public void writeRulesToFileSPMFFormatAsNumbers(String outputPath) {
        try {
            PrintWriter writer = new PrintWriter(outputPath, "UTF-8");

            for (Rule rule : rules) {
                // Transform from rule codified with short to string values
                int antecedentSize = rule.getAntecedent().size();
       
        		StringBuilder buffer = new StringBuilder();
        		// write itemset 1
                for (int j = 0; j < antecedentSize; j++) {
        			buffer.append(rule.getAntecedent().get(j));
        			if(rule.isIthAntecedentItemNegative(j)) {
                		buffer.append('-');
                	}
        			if (j != antecedentSize - 1) {
        				buffer.append(" ");
        			}
        		}
                
                // if the antecedent is empty
                if(antecedentSize == 0) {
                	buffer.append("DEFAULT");
                }
                
                buffer.append(" ==> ");
                buffer.append(rule.getKlass());
                // if the antecedent is not empty
                if(antecedentSize != 0) {
                	buffer.append(rule.getMeasuresToString());
                }
                writer.println(buffer.toString());
            }
            writer.close();
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
    
    /*
     * Write to disk
     * @param classifier a rule-based classifier
     * @param outputPath an output file path
     */
    public void writeRulesToFileSPMFFormatAsStrings(String outputPath, StringDataset dataset) {
        try {
            PrintWriter writer = new PrintWriter(outputPath, "UTF-8");

            for (Rule rule : rules) {
                // Transform from rule codified with short to string values
                int antecedentSize = rule.getAntecedent().size();
       
        		StringBuilder buffer = new StringBuilder();
        		// write itemset 1
                for (int j = 0; j < antecedentSize; j++) {
                	String item = dataset.getStringCorrespondingToItem(rule.getAntecedent().get(j));
                	if(rule.isIthAntecedentItemNegative(j)) {
                		buffer.append('-');
                	}
        			buffer.append(item);
        			if (j != antecedentSize - 1) {
        				buffer.append(" ");
        			}
        		}
                
                // if the antecedent is empty
                if(antecedentSize == 0) {
                	buffer.append("DEFAULT");
                }
                
                buffer.append(" ==> ");
                buffer.append(dataset.getStringCorrespondingToItem(rule.getKlass()));
                // if the antecedent is not empty
                if(antecedentSize != 0) {
                	buffer.append(rule.getMeasuresToString());
                }
                writer.println(buffer.toString());
            }
            writer.close();
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
    

    /*
     * Write to disk
     * @param classifier a rule-based classifier
     * @param outputPath an output file path
     */
    public void writeWithSringNames(Dataset training, String outputPath) {
        try {
            PrintWriter writer = new PrintWriter(outputPath, "UTF-8");

            for (Rule rule : rules){
                // Transform from rule codified with short to string values
                String[] antecedent = new String[rule.getAntecedent().size()];
                for (int j = 0; j < antecedent.length; j++) {
                    Short item = rule.getAntecedent().get(j);
					Attribute attr = training.getAttributeOfItem(item);
                    antecedent[j] = attr.getName() + "=" + training.getStringCorrespondingToItem(item);
                }

                String klass = training.getStringCorrespondingToItem(rule.getKlass());
                writer.println(String.join(" ", antecedent) + " => " + klass);
            }
            writer.close();
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
}
