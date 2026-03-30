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
package ca.pfv.spmf.algorithms.classifiers.adt;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.algorithms.ArraysAlgos;
import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.general.Rule;

/**
 * Class that represents a rule of the ADT algorithm
 * @see AlgoADT
 */
public class RuleADT extends Rule implements Serializable{

	/**
	 * UID
	 */
	private static final long serialVersionUID = -878688143431488747L;

	/**
	 * Number of times that an instance is matched, but class is wrong
	 */
	private long misses;

	/**
	 * Number of times that an instance is matched and class is right
	 */
	private long hits;

	/**
	 * Instances covered by this rule
	 */
	private List<Integer> coveredInstances;

	/**
	 * Constructor
	 * 
	 * @param antecedent of the rule
	 * @param klass      consequent of the rule
	 */
	public RuleADT(Short[] antecedent, short klass) {
		super(antecedent, klass);
		coveredInstances = new ArrayList<Integer>();
		misses = 0;
		hits = 0;
	}

	/**
	 * Constructor to clone
	 * 
	 * @RuleADT a rule
	 */
	public RuleADT(RuleADT rule) {
		super(rule.klass);
		add(rule.antecedent);

		supportAntecedent = rule.supportAntecedent;
		supportRule = rule.supportRule;
		misses = rule.misses;
		hits = rule.hits;
		coveredInstances = new ArrayList<Integer>(rule.coveredInstances);
	}

	/**
	 * Constructor
	 * 
	 * @param newAntecedent antecedent of the rule
	 * @param klass         consequent of the rule
	 */
	public RuleADT(List<Short> newAntecedent, short klass) {
		super(klass);
		coveredInstances = new ArrayList<Integer>();
		this.antecedent = new ArrayList<Short>(newAntecedent);
		misses = 0;
		hits = 0;
	}

	/**
	 * Constructor
	 * 
	 * @param klass consequent of the rule
	 */
	public RuleADT(short klass) {
		super(klass);
		coveredInstances = new ArrayList<Integer>();
	}

	/**
	 * Add an tid to the array of covered instances
	 * 
	 * @param tid to be added as covered
	 */
	void addCoveredInstance(Integer tid) {
		this.coveredInstances.add(tid);
	}

	/**
	 * Get pessimistic error estimate
	 * 
	 * @return the pessimistic error estimate
	 */
	public double getPessimisticErrorEstimate() {
		return errors(this.hits + this.misses, this.misses) + this.misses;
	}

	/*
	 * (non-Javadoc)
	 * 
	 */
	public String toString() {
		return super.toString() + " hits: " + this.hits + " misses: " + this.misses + " per: "
				+ this.getPessimisticErrorEstimate();
	}

	/**
	 * Calculate supports for the dataset passed as parameter
	 * 
	 * @param train dataset used as training set
	 */
	void calculateSupports(Dataset train) {
		this.supportAntecedent = 0;
		this.supportRule = 0;

		for (Instance instance : train.getInstances()) {
			Short[] items = instance.getItems();

			Boolean matchAntecedent = ArraysAlgos.isSubsetOf(antecedent, items);
			Boolean matchConsequent = instance.getKlass() == this.klass;

			if (matchAntecedent) {
				this.supportAntecedent++;

				if (matchConsequent) {
					this.supportRule++;
				}
			}
		}
	}

	/**
	 * Increment the counter of misses
	 */
	void incrementMisses() {
		this.misses += 1;
	}
	
	/**
	 * Increment the counter for the hits
	 */
	void incrementHits() {
		this.hits++;
	}

	/**
	 * Get the array of covered instances
	 * 
	 * @return the array of covered instances
	 */
	public List<Integer> getCoveredInstances() {
		return this.coveredInstances;
	}

	/**
	 * Get the number of misses instances
	 * 
	 * @return the number of misses
	 */
	public double getMisses() {
		return this.misses;
	}

	/**
	 * Calculates the merit for current rule
	 * 
	 * @return the merit for current rule
	 */
	public double getMerit() {
		double n = this.hits + this.misses;
		if (n <= 0)
			return 0.0;

		return (n - this.misses) / n;
	}


	
	@Override
	public String getMeasuresToString() {
		return " #SUP: " + getSupportRule() 
		+ " #CONF: " + getConfidence() 
		+ " #MERIT: " + this.getMerit()
		+ " #ERROR: " + this.getPessimisticErrorEstimate();
	}
}
