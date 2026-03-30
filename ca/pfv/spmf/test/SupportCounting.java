package ca.pfv.spmf.test;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items.CandidateInSequenceFinder;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items.Item;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items.Sequence;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items.SequenceDatabase;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items.abstractions.ItemAbstractionPair;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items.creators.AbstractionCreator;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items.patterns.Pattern;


/** 
 * This is an implementation of the counting of support phase addressed in GSP algorithm.
 * This class is one of the two method continuously repeated by means of the GSP's main loop.
 * Here, from a set of (k+1)-sequences candidates we check which of those sequences are actually frequent and which can be ruled out.
 *
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
 * @author agomariz
 */

class SupportCounting {
    /**
     * Original database where we have to look for each candidate.
     */
    private SequenceDatabase database;
    /**
     * Indexation map. A tool for the next candidate generation step.
     */
    private Map<Item, Set<Pattern>> indexationMap;
    private AbstractionCreator abstractionCreator;
    

    /**  alpha */
    double alpha;
    /**  alpha */
    double beta;
    /**  alpha */
    double gamma;

    /**
     * The only constructor
     * @param database the original sequence database
     * @param creador 
     * @param gramma1 
     * @param beta12 
     * @param alpha12 
     */
    public SupportCounting(SequenceDatabase database, AbstractionCreator creador, double alpha12, double beta12, double gramma1) {
        this.database = database;
        this.abstractionCreator = creador;
        this.indexationMap = new HashMap<Item, Set<Pattern>>();
        this.alpha = alpha12;
        this.beta = beta12;
        this.gamma = gramma1;
    }

    /**
     * Main method. For all of the elements from the candidate set, we check if
     * they are or not frequent. 
     * @param candidateSet the candidate set
     * @param k the level where we are checking
     * @param minSupportAbsolute the absolute minimum support, i.e. the minimum number of
     * sequences where a candidate have to appear
     * @return the set of frequent patterns.
     */
    public Set<Pattern> countSupport(List<Pattern> candidateSet, int k, double minSupportAbsolute) {
    	
    	// alpha beta gramma
    	double k1 = 13.2667;
    	double k2 = 0.6;
    	double k3= 0.14;
    	int max_min = 547;
    	DecimalFormat df = new DecimalFormat(".000");
    	int count =0;
        double average = 0;
        String str_primaryitemsettime;
    	String str_time_day;
    	double time_day;
    	String str_time_flag;
    	int time_flag;
    	int max_sequence_length = 398;
        indexationMap.clear();
        //For each sequence of the original database
        for (Sequence sequence : database.getSequences()) {
            //we check for each candidate if it appears in that sequence
        	// length 
        	for (Pattern candidate : candidateSet) {
                //We define a list of k positions, all initialized at itemset 0, item 0, i.e. first itemset, first item.
                List<int[]> position = new ArrayList<int[]>(k);
                for (int i = 0; i < k; i++) {
                    position.add(new int[]{0,0});
                }
                CandidateInSequenceFinder finder = new CandidateInSequenceFinder(abstractionCreator);
                //we check if the current candidate appears in the sequence
                abstractionCreator.isCandidateInSequence(finder, candidate, sequence, k, 0, position);
                if (finder.isPresent()) {
                	List<Double> timelist = new ArrayList<>();
                	List<Integer> flaglist = new ArrayList<>();
                    candidate.addAppearance(sequence.getId());
                    for(int j = 0;j<position.size();j++) {
                    	str_primaryitemsettime = Integer.toString((int)(database.getSequences().get(sequence.getId()).get(position.get(j)[0]).getTimestamp()));
                    	str_time_day = str_primaryitemsettime.substring(0,str_primaryitemsettime.length()-1);
                    	time_day = (Double.parseDouble(str_time_day)-1)/max_min;
                    	str_time_flag = str_primaryitemsettime.substring(str_primaryitemsettime.length()-1 , str_primaryitemsettime.length());
                    	time_flag = Integer.parseInt(str_time_flag);
                    	timelist.add(time_day);
                    	flaglist.add(time_flag);
                    }
                    // discrete vality
                    count =0;
                    double sum =0;
                    average = 0;
                    double discrete_q = 0;
                    count = timelist.size();
                    for(int i =0;i<count;i++) {
                    	sum += timelist.get(i);
                    }
                    average = sum/count;
                    for(int j = 0;j<count;j++) {
                    	double tempdistance = timelist.get(j) - average;
                    	discrete_q += Math.pow(tempdistance,2);
                    }
                    candidate.total_discrete_constraint_sup += Math.exp(-1.0/k2*discrete_q);
                    // vality
                    int low_num=0;
                    // discrete vality
                    for(int p =0;p<flaglist.size();p++) {
                    	if(flaglist.get(p) < 1) {
                    		low_num = low_num+1;
                    	}
                    }
                    candidate.total_vality_constraint_sup += Math.exp(-1.0*k3*low_num);
                    candidate.addAppearance(sequence.getId());
                }
            }
        }
        Set<Pattern> result = new LinkedHashSet<Pattern>();
        //We keep all the frequent candidates and we put them in the indexation map
        for (Pattern candidate : candidateSet) {
        	// length
        	double each_length_constraint_sup = 0;
        	double local_total_length_constraint_sup = 0;
            for(int i =0;i<candidate.getAppearingIn().length();i++) {
                if(candidate.getAppearingIn().get(i)) {
             	    each_length_constraint_sup = Math.exp((-1.0*k1)*(1.0/max_sequence_length)*SequenceDatabase.primarysequences.get(i).getLength());
             		local_total_length_constraint_sup = local_total_length_constraint_sup + each_length_constraint_sup;
             	}
             }
            candidate.total_length_constraint_sup = local_total_length_constraint_sup;
            candidate.total_three_constraint_integration_sup = alpha * candidate.total_length_constraint_sup+
            		beta*candidate.total_discrete_constraint_sup+gamma*candidate.total_vality_constraint_sup;
            // integration
            if (candidate.total_three_constraint_integration_sup >= minSupportAbsolute) {
                result.add(candidate);
                putInIndexationMap(candidate);
            }
        }
        candidateSet = null;
        //We end returning the frequent candidates, i.e. the frequent k-sequence set
        return result;
    }


    /**
     * Method to create the indexation map useful for the next step of
     * generation of candidates
     * @param entry 
     */
    private void putInIndexationMap(Pattern entry) {
        ItemAbstractionPair pair = entry.getIthElement(0);
        Set<Pattern> correspondence = indexationMap.get(pair.getItem());
        if (correspondence == null) {
            correspondence = new LinkedHashSet<Pattern>();
            indexationMap.put(pair.getItem(), correspondence);
        }
        correspondence.add(entry);
    }

    /**
     * Get the indexation map associated with the frequent k-sequence set
     * @return the indexation map
     */
    public Map<Item, Set<Pattern>> getIndexationMap() {
        return indexationMap;
    }
}
