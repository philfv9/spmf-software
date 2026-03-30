/**
 * This file is part of SPMF data mining library.
 * It is adapted from some GPL code obtained from the LAC library, which used some SPMF code.
 *
 * Copyright (C) SPMF, LAC
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
package ca.pfv.spmf.algorithms.classifiers.cba;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.general.RuleClassifier;

/**
 * This class has the logic for building a classifier using CARs. To produce the
 * best classifier out of the whole set of rules would involve evaluating all
 * the possible subsets of it on the training data and selecting the subset with
 * the right rule sequence that m gives the least number of errors. There are
 * 2^m such subsets, where m is the number of rules, which can be more than
 * 10,000, not to mention different rule sequences. This is clearly infeasible,
 * this class contains the heuristic proposed by the original authors to solve
 * this problem
 */
public class CBAM2 {
    private List<RuleCBA> listU;
    private List<RuleCBA> listQ;
    private List<SelectedRule> listC;
    private List<Structure> listA;

    /**
     * Dataset for the rules
     */
    private Dataset dataset;

    /**
     * Rules forming the classifier
     */
    private List<RuleCBA> rules;

    /**
     * Constructor to post-process the rules
     * 
     * @param dataset of the rules being processed
     * @param rules   being used as base for the classifier
     */
    public CBAM2(Dataset dataset, List<RuleCBA> rules) {
    	this.dataset = dataset;
        this.rules = rules;

        this.listU = new ArrayList<RuleCBA>();
        this.listQ = new ArrayList<RuleCBA>();
        this.listA = new ArrayList<Structure>();

        Collections.sort(this.rules);

        this.stage1();
        this.stage2();
        this.stage3();
    }

    /**
     * Sort the set of generated rules considering confidence, support and size.
     * This is to ensure that we will choose the highest precedence rules for our
     * classifier.
     */
    private void stage1() {
        int cRule;
        int wRule;
        short y;
        Short[] items;
        RuleCBA rule;

        for (int i = 0; i < this.dataset.getInstances().size(); i++) {
            Instance instance = this.dataset.getInstances().get(i);
            
			items = instance.getItems();
            y = instance.getKlass();

            cRule = -1;
            wRule = -1;

            for (int j = 0; j < this.rules.size() && (cRule < 0 || wRule < 0); j++) {

                rule = this.rules.get(j);

                if (rule.matching(items)) {
                    if ((cRule < 0) && (y == rule.getKlass()))
                        cRule = j;
                    if ((wRule < 0) && (y != rule.getKlass()))
                        wRule = j;
                }
            }

            if (cRule > -1) {
                rule = this.rules.get(cRule);

                if (this.isNew(this.listU, rule))
                    this.listU.add(rule);

                rule.incrementKlassCovered(y);
                if ((cRule < wRule) || (wRule < 0)) {
                    rule.mark();
                    if (this.isNew(this.listQ, rule))
                        this.listQ.add(rule);
                } else {
                    Structure str = new Structure(i, y, cRule, wRule);
                    this.listA.add(str);
                }
            } else if (wRule > -1) {
                Structure str = new Structure(i, y, cRule, wRule);
                this.listA.add(str);
            }
        }
    }

    /**
     * Select rules for the classifier from R following the sorted sequence. For
     * each rule r, we go through D to find those cases covered by r (they satisfy
     * the conditions of r). We mark r if it correctly classifies a case d . d.id is
     * the unique identification number of d. If r can correctly classify at least
     * one case (i.e., if r is marked), it will be a potential rule in our
     * classifier. Those cases it covers are then removed from D. A default class is
     * also selected (the majority class in the remaining data),which means that if
     * we stop selecting more rules for our classifier C this class will be the
     * default class of C. We then compute and record the total number of errors
     * that are made by the current C and the default class . This is the sum of the
     * number of errors that have been made by all the selected rules in C and the
     * number of errors to be made by the default class in the training data. When
     * there is no rule or no training case left, the rule selection process is
     * completed.
     */
    private void stage2() {
        int poscRule;
        int poswRule;
        Structure str;
        RuleCBA cRule;
        RuleCBA wRule;
        RuleCBA rule;

        for (int i = 0; i < this.listA.size(); i++) {
            str = this.listA.get(i);
            poscRule = str.getIndexCRule();
            poswRule = str.getIndexWRule();

            wRule = this.rules.get(poswRule);
            if (wRule.isMark()) {
                if (poscRule > -1)
                    this.rules.get(poscRule).decrementKlassCovered(str.getKlass());
                wRule.incrementKlassCovered(str.getKlass());
            } else {
                for (int j = 0; j < this.listU.size(); j++) {
                    rule = this.listU.get(j);

                    if (rule.matching(this.dataset.getInstances().get(str.getdIdInstance()).getItems())
                            && rule.getKlass() != str.getKlass()) {
                        if (poscRule > -1) {
                            cRule = this.rules.get(poscRule);
                            if (rule.isPrecedence(cRule)) {
                                rule.addReplace(new Replace(poscRule, str.getdIdInstance(), str.getKlass()));
                                rule.incrementKlassCovered(str.getKlass());

                                if (this.isNew(this.listQ, rule))
                                    this.listQ.add(rule);
                            }
                        } else {
                            rule.addReplace(new Replace(poscRule, str.getdIdInstance(), str.getKlass()));
                            rule.incrementKlassCovered(str.getKlass());

                            if (this.isNew(this.listQ, rule))
                                this.listQ.add(rule);
                        }
                    }
                }
            }
        }
    }

    /**
     * Discard those rules in C that do not improve the accuracy of the classifier.
     * The first rule at which there is the least number of errors recorded on D is
     * the cutoff rule. All the rules after this rule can be discarded because they
     * only produce more errors. The undiscarded rules and the default class of the
     * last rule in C form our classifier.
     */
    private void stage3() {
        long ruleErrors;
        long errorsOfRule;
        int posLowest;
        long lowestTotalErrors;
        Long totalErrors;
        Map<Short, Long> compClassDistr;
        int[] exampleCovered;
        Short[] items;
        RuleCBA rule;
        Replace rep;
        SelectedRule sel;

        this.listC = new ArrayList<SelectedRule>();

        compClassDistr = this.dataset.getMapClassToFrequency();
        ruleErrors = 0;

        exampleCovered = new int[this.dataset.getInstances().size()];
        for (int i = 0; i < this.dataset.getInstances().size(); i++)
            exampleCovered[i] = 0;

        Collections.sort(this.listQ);

        for (int i = 0; i < this.listQ.size(); i++) {
            rule = this.listQ.get(i);
            if (rule.getKlassesCovered(rule.getKlass()) > 0) {
                for (int j = 0; j < rule.getReplaceCount(); j++) {
                    rep = rule.getReplace(j);
                    if (exampleCovered[rep.getdIdInstance()] > 0)
                        rule.decrementKlassCovered(rep.getKlass());
                    else {
                        if (rep.getIndexCRule() > -1) {
                            this.rules.get(rep.getIndexCRule()).decrementKlassCovered(rep.getKlass());
                        }
                    }
                }

                errorsOfRule = 0;
                for (int j = 0; j < this.dataset.getInstances().size(); j++) {
                    if (exampleCovered[j] < 1) {
                        Instance instance = this.dataset.getInstances().get(j);
						items = instance.getItems();

                        if (rule.matching(items)) {
                            exampleCovered[j] = 1;
                            short klass = instance.getKlass();
                            compClassDistr.put(klass, compClassDistr.get(klass) - 1);

                            if (rule.getKlass() != instance.getKlass())
                                errorsOfRule++;
                        }
                    }
                }
                ruleErrors += errorsOfRule;

                short defaultKlass = this.dataset.getKlassAt(0);
                for (Entry<Short, Long> entry : compClassDistr.entrySet()) {
                    if (compClassDistr.getOrDefault(defaultKlass,0L) < entry.getValue())
                        defaultKlass = entry.getKey();
                }

                Long defaultErrors = 0L;
                for (Entry<Short, Long> entry : compClassDistr.entrySet()) {
                    if (!entry.getKey().equals(defaultKlass))
                        defaultErrors += entry.getValue();
                }

                totalErrors = ruleErrors + defaultErrors;
                this.listC.add(new SelectedRule(rule, defaultKlass, totalErrors));
            }
        }

        if (this.listC.size() > 0) {
            lowestTotalErrors = this.listC.get(0).getTotalErrors();
            posLowest = 0;
            for (int i = 1; i < this.listC.size(); i++) {
                sel = this.listC.get(i);
                if (sel.getTotalErrors() < lowestTotalErrors) {
                    lowestTotalErrors = sel.getTotalErrors();
                    posLowest = i;
                }
            }
            while (this.listC.size() > (posLowest + 1))
                this.listC.remove(posLowest + 1);
        }
    }

    /**
     * Returns the classifier
     * 
     * @return Classifier with the whole of the classifier
     */
    public RuleClassifier getClassifier(String name){
        short defaultKlass;
        RuleClassifier rb = new RuleClassifier(name);
        SelectedRule sel;

        if (this.listC.size() > 0) {
            for (int i = 0; i < this.listC.size(); i++) {
                sel = this.listC.get(i);
                rb.add( new RuleCBA(sel.getRule()));
            }

            sel = this.listC.get(this.listC.size() - 1);
            rb.add(new RuleCBA(sel.getDefaultKlass()));
        } else {
            defaultKlass = this.dataset.getKlassAt(0);
            for (int i = 0; i < this.dataset.getClassesCount(); i++) {
                defaultKlass = this.dataset.getKlassAt(0);
                short klass = this.dataset.getKlassAt(i);

                if (this.dataset.getNumberInstancesPerKlass(klass) > this.dataset
                        .getNumberInstancesPerKlass(defaultKlass))
                    defaultKlass = klass;
            }
            rb.add(new RuleCBA(defaultKlass));
        }

        return rb;
    }

    /**
     * Check if the rule was contained in the set of rules
     * 
     * @param rb   set of rules
     * @param rule to check if it was contained or not
     * @return true if rule was not contained
     */
    private boolean isNew(List<RuleCBA> rb, RuleCBA rule) {
        for (RuleCBA r:  rb){
            if (rule.equals(r)) {
                return false;
            }
        }
        return true;
    }
}
