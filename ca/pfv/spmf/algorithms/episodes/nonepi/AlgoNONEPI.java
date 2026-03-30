/*
 * This file is part of the SPMF DATA MINING SOFTWARE *
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the *
 * terms of the GNU General Public License as published by the Free Software *
 * Foundation, either version 3 of the License, or (at your option) any later *
 * version. SPMF is distributed in the hope that it will be useful, but WITHOUT
 * ANY * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Copyright Oualid Ouarem et al.
 */
package ca.pfv.spmf.algorithms.episodes.nonepi;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * The NONEPI algorithm, which was presented in this paper:
 * 
 * Ouarem, O., Nouioua, F., Fournier-Viger, P. (2021). Mining Episode Rules From
 * Event Sequences Under Non-Overlapping Frequency. Proc. 34th Intern. Conf. on
 * Industrial, Engineering and Other Applications of Applied Intelligent Systems
 * (IEA AIE 2021), Springer LNAI, pp. 73-85.
 * 
 * @author Oualid Ouarem et al.
 */
public class AlgoNONEPI {

	/** Start time of the algorithm */
	private long startExecutionTime;

	/** End time of the algorithm */
	private long endExecutionTime;
//
//	/** List of frequent episodes */
	private List<Episode> FrequentEpisodes;

	/** List of episode rules */
	private List<String> allRules = new ArrayList<>();

	/** Candidate episode count */
	private int CandidatEpisodesCount;

	/** Number of frequent episodes */
	private int episodeCount;

	/** Maximum size */
	private int maxsize;

	/** Episode rule count */
	private int ruleCount;

	/** Constructor */
	public AlgoNONEPI() {
		
		this.FrequentEpisodes = new ArrayList<>();
		this.episodeCount = 0;
		this.CandidatEpisodesCount = 0;
		this.ruleCount = 0;
		this.maxsize = 0;

	}

	/**
	 * Scans the input sequence and return the list of single events episodes
	 *
	 * @param path the path to the file that holds the sequences
	 * @throws java.io.IOException if the path is incorrect
	 */
	private Map<String, List<Occurrence>> scanSequence(String path) throws IOException {
		BufferedReader reader;
		reader = new BufferedReader(new FileReader(path));
		String line;
		Map<String, List<Occurrence>> SingleEventEpisode = new HashMap<>();
		while ((line = reader.readLine()) != null) {
			String[] lineSplited = line.split("\\|");
			String event = lineSplited[0];
			long timeStamp = Integer.parseInt(lineSplited[1]);
			List<String> events;
			events = new ArrayList<>();
			events.add(event);
			Episode epi = new Episode(events);
			Occurrence occ = new Occurrence(timeStamp, timeStamp);
			if (SingleEventEpisode.containsKey(epi.toString())) {
				SingleEventEpisode.get(epi.toString()).add(occ);
			} else {
				SingleEventEpisode.put(epi.toString(), new ArrayList<>());
				SingleEventEpisode.get(epi.toString()).add(occ);
			}

		}
		reader.close();
		return SingleEventEpisode;
	}
	
	
	/**
	 * Recognize the new episode's occurrences starting from two episodes
	 *
	 * @param alpha       a N-node episode to grow
	 * @param singleEvent a single event to grow alpha by.
	 * @return the list of new episode's occurrences
	 */

	private List<Occurrence> OccurrenceRecognition(Episode alpha, Episode singleEvent) {
		List<Occurrence> oc_1 = alpha.getOccurrences();
		List<Occurrence> oc_2 = singleEvent.getOccurrences();
		List<Occurrence> new_occurrences;
		new_occurrences = new ArrayList<>();
		int i = 0, j = 0, k;
		boolean trouve;
		int taille_1 = oc_1.size(), taille_2;
		while (i < taille_1) {
			// j = 0;
			Occurrence I1 = oc_1.get(i);
			trouve = false;
			k = i + 1;
			taille_2 = oc_2.size();
			while (j < taille_2) {
				Occurrence I2 = oc_2.get(j);
				if (I2.getStart() > I1.getEnd()) {
					Occurrence occ = new Occurrence(I1.getStart(), I2.getEnd());
					occ.setProb(occ.getProb());
					new_occurrences.add(occ);
					trouve = true;
					while (k < taille_1) {
						if (oc_1.get(k).getStart() > occ.getEnd()) {
							break;
						}
						k = k + 1;
					}
				}
				if (trouve) {
					break;
				} else {
					j++;
				}

			}
			i = k;
		}
		return new_occurrences;
	}

	
	/**
	 * Generate the set of episode rules from the frequent episodes set (with
	 * pruning)
	 *
	 * @param FrequentEpisodes set of frequent episodes already recognized
	 * @param minconf          confidence threshold
	 * @return set of all episodes rules
	 */
	public List<String> findNONEpiRulesWithPruning(List<Episode> FrequentEpisodes, float minconf) {
		this.startExecutionTime = System.currentTimeMillis();
		allRules = new ArrayList<>();
		for (int i = 0; i < FrequentEpisodes.size(); i++) {
			Episode alpha = FrequentEpisodes.get(i);
			Episode beta = Predecessor(alpha.toString());
			boolean stop = false;
			while (!stop && beta != null) {
				double beta_support = 0;
				for (Episode t_beta : FrequentEpisodes) {
					if (beta.toString().equals(t_beta.toString())) {
						beta_support = t_beta.getSupport();
						break;
					}
				}
				double alpha_support = alpha.getSupport();
				if (((float) alpha_support / (float) beta_support) >= minconf) {
					allRules.add(beta.toSPMFString() + " ==> " + alpha.toSPMFString() 
					+ " #SUP: " + beta_support 
					+ " #CONF: "
							+ (float) alpha_support / (float) beta_support);
					beta = Predecessor(beta.toString());
				} else {
					stop = true;
				}
			}
		}
		this.endExecutionTime = System.currentTimeMillis();
		this.ruleCount = allRules.size();
		return allRules;
	}
	
	
	public void NONEPIDFS(Episode alpha,  List<Episode> epsilon, Episode deleted,  double minsup){
		
    	if (epsilon.size() != 0) {
    		Episode beta = epsilon.get(0);
    		ArrayList<String> t_events = new ArrayList<>(alpha.getEvents());
    		if(alpha.getEvents().size() > 0 && alpha.getEvents().indexOf(beta.getEvents().get(0))<0) {//episode is not injective
    			Collections.copy(t_events, alpha.getEvents());
        		t_events.add(beta.getEvents().get(0));
        		Episode gamma = new Episode(t_events);
    			if (!Exists(gamma, FrequentEpisodes)) {
					List<Occurrence> occurrences = OccurrenceRecognition(alpha, beta);
					int t_sup = occurrences.size();
					CandidatEpisodesCount++;
		            if (t_sup >= minsup) {
		            	gamma.setOccurrences(occurrences);
		            	gamma.setSupport(t_sup);
		            	List<Episode> l = new ArrayList<>(FrequentEpisodes);
		                Collections.copy(l, FrequentEpisodes);
		                l.add(gamma);
		                FrequentEpisodes = new ArrayList<>(l);
		                
		                Collections.copy(FrequentEpisodes, l);
		                List<Episode> epsiloncopy = new ArrayList<>(epsilon);
		                Collections.copy(epsiloncopy, epsilon);
		                if (deleted != null)
		                	epsiloncopy.add(deleted);
		                NONEPIDFS(gamma, epsiloncopy, epsilon.get(0), minsup);
		                
		                Episode temp = epsiloncopy.get(0);
		                List<Episode> epsiloncopy_2 = new ArrayList<>(epsiloncopy);
		                epsiloncopy.remove(temp);
		                Collections.copy(epsiloncopy_2, epsiloncopy);
		                epsiloncopy_2.add(temp);
		                NONEPIDFS(alpha, epsiloncopy_2, null, minsup);
		                
		            }
		            	List<Episode> sublist = epsilon.subList(1, epsilon.size());
			            List<Episode> epsiloncopy = new ArrayList<>(sublist);
		                Collections.copy(epsiloncopy, sublist);
		                if (deleted != null)
		                	epsiloncopy.add(deleted);
		                //System.out.println("    gamma= "+gamma);
		                NONEPIDFS(alpha, epsiloncopy, epsilon.get(0) , minsup);
				}else {
					NONEPIDFS(alpha, epsilon.subList(1, epsilon.size()), epsilon.get(0) , minsup);
				}
				
				
	    	} else {
	    		
	    		List<Episode> sublist = epsilon.subList(1, epsilon.size()); 
                NONEPIDFS(alpha, sublist, epsilon.get(0) , minsup);
                
	    	}
    	}
    }
	

	/**
	 * Generate new Episodes and filter only the frequent ones
	 *
	 * @param input      A sequence of events
	 * @param minsupport Support threshold
	 * @return f_episode list of all frequent episodes
	 * @throws IOException If the path is incorrect or the file doesn't exists
	 */
	
	public List<Episode> findFrequentEpisodes(String input, double minsupport) throws IOException {
		MemoryLogger.getInstance().reset();
		this.startExecutionTime = System.currentTimeMillis();
		Map<String, List<Occurrence>> singleEpisodeEvent;
		singleEpisodeEvent = scanSequence(input);
		Object[] episodes = singleEpisodeEvent.keySet().toArray();
		this.CandidatEpisodesCount = episodes.length;
		for (Object episode : episodes) {
			List<Occurrence> occs = singleEpisodeEvent.get(episode.toString());
			double t_sup = expSupport(occs);
			if (t_sup >= minsupport) {
				List<String> t_events = new ArrayList<>();
				Collections.addAll(t_events, StrToList(episode.toString()));
				Episode t_epi = new Episode(t_events);
				List<Occurrence> occurrences = singleEpisodeEvent.get(episode.toString());
				t_epi.setOccurrences(occurrences);
				t_epi.setSupport(t_sup);
				episodeCount++;
				FrequentEpisodes.add(t_epi);
			}
		}
		
		List<Episode> t_freq = new ArrayList<>(FrequentEpisodes);
		Collections.copy(t_freq, FrequentEpisodes);
		int i = 0, j = 0, k = 0;
		int thesize = t_freq.size();
		boolean stop;
		Episode alpha, beta, gamma, root;
		ArrayList<String> t_events;
		//System.out.println(FrequentEpisodes);
		while (i < FrequentEpisodes.size()) {
			alpha = FrequentEpisodes.get(i);
			root = alpha;
			stop = false;
			k = 0;
			while(k<thesize-1) {
				j = k;
				alpha = root;
				while(j<thesize) {
					beta = t_freq.get(j);
					t_events = new ArrayList<>(alpha.getEvents());
					if(alpha.getEvents().indexOf(beta.getEvents().get(0))<0) {
						Collections.copy(t_events, alpha.getEvents());
		        		t_events.add(beta.getEvents().get(0));
		        		gamma = new Episode(t_events);
		    			if (!Exists(gamma, FrequentEpisodes)) {
		    				List<Occurrence> occurrences = OccurrenceRecognition(alpha, beta);
							int t_sup = occurrences.size();
							CandidatEpisodesCount++;
				            if (t_sup >= minsupport) {
				            	gamma.setOccurrences(occurrences);
				            	gamma.setSupport(t_sup);
				            	alpha = gamma;
				                FrequentEpisodes.add(gamma);
				                episodeCount = episodeCount + 1;
				                if (gamma.getSize() >= maxsize){
				                	maxsize = gamma.getSize();
				                }
				            }
						}
					}
					beta = null;
					gamma = null;
					j = j + 1;
				}				
				k = k + 1;
			}
			alpha = null;
            //Runtime.getRuntime().gc();
			i++;
		}
		this.endExecutionTime = System.currentTimeMillis();
		MemoryLogger.getInstance().checkMemory();
		episodeCount = FrequentEpisodes.size();
		return FrequentEpisodes;
	}	
	
	
	public boolean Exists(Episode epi, List<Episode> F) {
		if ((F.size() == 0) || (F == null)) 
    		return false;
		
		boolean trouve = false;
    	int i = 0;
    	
    	while (i < F.size() && !trouve) {
    		if (epi.Equals(F.get(i))){
    			trouve = true;
    		}
    		i++;
    	}
    	return trouve;
	}
	
	
	

	/**
	 * Check if it is injective
	 * 
	 * @param events a list of events
	 * @return true if injective, otherwise false
	 */

	public double expSupport(List<Occurrence> occurrences){
		double sum = 0;
        for (Occurrence occurrence : occurrences) {
            sum = sum + occurrence.getProb();
        }
        return sum;
	}

	/**
	 * Get the predecessor
	 * 
	 * @param alpha
	 * @return the predecessor episode or null
	 */
	private Episode Predecessor(String alpha) {
		String[] temp_alpha = StrToList(alpha);
		if (temp_alpha.length != 1) {
			String[] t_predecessor = new String[temp_alpha.length - 1];
			for (int i = 0; i < t_predecessor.length; i++) {
				t_predecessor[i] = temp_alpha[i];
			}
			List<String> events = new ArrayList<>();
			Collections.addAll(events, t_predecessor);
			return new Episode(events);
		}
		return null;
	}

	/**
	 * Convert a String to an array of String
	 * 
	 * @param string the string
	 * @return an array of String
	 */
	private static String[] StrToList(String string) {
		int index_1 = string.indexOf("<");
		String tempString = string.substring(index_1 + 1, string.length() - 1);
		if (tempString.contains("->")) {
			return tempString.split("->");
		}
		return new String[] { tempString };
	}


	/**
	 * Print statistics about the last execution of the algorithm.
	 */
	public void printStats() {
		System.out.println("=============  NONEPI - STATS =============");
		System.out.println(" Candidates count : " + this.CandidatEpisodesCount);
		System.out.println(" The algorithm stopped at size : " + maxsize);
		System.out.println(" Frequent episodes count : " + episodeCount);
		System.out.println(" Maximum memory usage : " + MemoryLogger.getInstance().getMaxMemory() + " mb");
		System.out.println(" Total time ~ : " + (this.endExecutionTime - this.startExecutionTime) + " ms");
		System.out.println(" Episode rule count: " + this.ruleCount);
		System.out.println("===================================================");
	}

	/**
	 * Save the rules to a file
	 * 
	 * @param outputPath the output file path
	 */
	public void saveRulesToFile(String outputPath) {
		try {
			PrintWriter writer = new PrintWriter(outputPath, "UTF-8");
			writer.write(rulesAsString());
			writer.close();
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
	
	

	/**
	 * Get a string representation of this set of rules (for printing or writing to
	 * file)
	 * 
	 * @return a string
	 */
	private String rulesAsString() {
		StringBuilder buffer = new StringBuilder();

		// For each rule
		for (int z = 0; z < allRules.size(); z++) {
			String rule = allRules.get(z);

			buffer.append(rule);
			buffer.append(System.lineSeparator());
		}
		return buffer.toString();
	}
}
