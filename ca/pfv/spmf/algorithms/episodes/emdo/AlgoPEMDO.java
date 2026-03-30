package ca.pfv.spmf.algorithms.episodes.emdo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import ca.pfv.spmf.algorithms.episodes.nonepi.Episode;
import ca.pfv.spmf.tools.MemoryLogger;

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
 * Copyright Oualid Ouarem et al. 2025
 */

/**
 * This is a implementation of the EMDO and EMDO-P algorithms for parallel
 * episode mining. EMDO is for the case of an event sequences (based on the
 * support of episodes). EMDO-P is for the case of an event sequence with
 * uncertainty (based on the expected support of episodes). Besides, the code
 * can also generate episode rules from the frequent episodes discovered by EMDO
 * and EMDO-P.
 * 
 * @author Oualid Ouarem et al. 2025
 */

public class AlgoPEMDO {

	/**
	 * Timestamp marking the start of the algorithm execution.
	 */
	private long startExecutionTime;

	/**
	 * Timestamp marking the end of the algorithm execution.
	 */
	private long endExecutionTime;

	/**
	 * List of frequent episodes discovered by the algorithm.
	 */
	private List<Episode> frequentEpisodes;

	/**
	 * List of valid rules generated from the frequent episodes.
	 */
	private List<String> validRules;

	/**
	 * Count of candidate episodes generated during mining.
	 */
	private int CandidateEpisodesCount;

	/**
	 * Total number of frequent episodes discovered.
	 */
	private int episodeCount;

	/**
	 * Maximum size (length) of episodes discovered.
	 */
	private int maxsize;

	/**
	 * Flag to know if the last run was using the ExpectedSupport
	 */
	private boolean lastRunWasEMDOP = false;

	/**
	 * Constructor.
	 */
	public AlgoPEMDO() {
		this.episodeCount = 0;
		this.CandidateEpisodesCount = 0;
		this.frequentEpisodes = new ArrayList<Episode>();
		this.validRules = new ArrayList<String>();
		this.maxsize = 1;

	}

	/**
	 * Calculates the expected support of an episode by summing the probabilities of
	 * its occurrences.
	 *
	 * @param occurrences list of occurrences of an episode
	 * @return expected support (sum of probabilities)
	 */
	public double expectedSupport(List<Occurrence> occurrences) {
		double sum = 0;
		for (Occurrence occurrence : occurrences) {
			sum = sum + occurrence.getProbability();
		}
		return sum;
	}

	/**
	 * Checks whether a given list of events is injective (no duplicates).
	 *
	 * @param events the list of event types
	 * @return true if injective, false otherwise
	 */
	public boolean isInjective(List<String> events) {
		if (events.isEmpty()) {
			return true;
		}
		String event = events.get(events.size() - 1);
		events = events.subList(0, events.size() - 1);
		if (events.contains(event)) {
			return false;
		}
		return isInjective(events);
	}

	/**
	 * Performs distinct occurrence recognition when growing an episode.
	 *
	 * @param alpha       The episode to grow
	 * @param singleEvent single episode to grow alpha by.
	 * @return the set of probabilistic distinct occurrences of the new episode
	 *         composed by alpha and the single event episode "beta"
	 */
	public List<Occurrence> distinctOccurrenceRecognition(Episode alpha, Episode singleEvent) {
		List<Occurrence> oc_1 = new ArrayList<>(alpha.getOccurrences()),
				oc_2 = new ArrayList<>(singleEvent.getOccurrences()), new_occurrences = new ArrayList<>();
		Collections.copy(oc_1, alpha.getOccurrences());
		Collections.copy(oc_2, singleEvent.getOccurrences());
		List<Integer> timestamps;
		boolean found;
		Occurrence I1, I2, tempOccurrence = new Occurrence();
		List<String> events;
		while (oc_1.size() > 0) {
			I1 = oc_1.get(0);
			found = false;
			while (oc_2.size() > 0 && !found) {
				I2 = oc_2.get(0);
				tempOccurrence = new Occurrence();

				events = new ArrayList<>(I1.getEvents());
				Collections.copy(events, I1.getEvents());
				events.add(I2.getEvents().get(0));
				tempOccurrence.setEvents(events);
				events = null;

				timestamps = new ArrayList<>(I1.allTimeStamps());
				Collections.copy(timestamps, I1.allTimeStamps());
				timestamps.add(I2.allTimeStamps().get(0));
				tempOccurrence.setTimeStamps(timestamps);
				timestamps = null;
				// boolean stop = false;
				int c = 0;
				Occurrence lastoccurrence;
				lastoccurrence = tempOccurrence;
				while (c < oc_2.size()) {

					I2 = oc_2.get(c);
					if (lastoccurrence.allTimeStamps().indexOf(I2.allTimeStamps().get(0)) < 0) {
						c = c + 1;
						found = true;
					} else {
						oc_2.remove(I2);
					}
				}

				c = 0;
				while (c < oc_1.size()) {
					I1 = oc_1.get(c);
					if (lastoccurrence.isDistinct(I1.allTimeStamps())) {
						c = c + 1;
						found = true;
					} else {
						oc_1.remove(I1);
					}
				}

				if (new_occurrences.size() >= 0) {
					new_occurrences.add(tempOccurrence);
					found = true;
				}

				if (oc_1.size() == 0) {
					found = true;
				} else {
					I1 = oc_1.get(0);
				}
				tempOccurrence = null;
			}
			// if (j == taille_2) {
			if (oc_2.size() == 0) {
				break;
			}
		}
		oc_1 = null;
		oc_2 = null;
		Runtime.getRuntime().gc();
		return new_occurrences;
	}

	/**
	 * Scans an input sequence file to extract episodes of size 1 using expected
	 * support.
	 * 
	 * @param minsup              the support threshold
	 * @param singleEventEpisodes list of candidate episodes of size 1
	 * @return the list of frequent episode under distinct occurrences-based
	 *         frequency
	 * @throws IOException
	 */
	public List<Episode> findFrequentEpisodesEMDOP(double minsup, Map<String, List<Occurrence>> singleEventEpisodes)
			throws IOException {
		validRules = null;
		lastRunWasEMDOP = true;

		MemoryLogger.getInstance().reset();
		frequentEpisodes = new ArrayList<>();
		this.startExecutionTime = System.currentTimeMillis();
		Object[] episodes = singleEventEpisodes.keySet().toArray();
		this.CandidateEpisodesCount = episodes.length;
		double t_sup;
		List<Occurrence> occurrences;
		Episode t_epi, alpha;
		ArrayList<String> t_events;
		for (int i = 0; i < episodes.length; i++) {
			t_sup = expectedSupport(singleEventEpisodes.get(episodes[i].toString()));
			if (t_sup >= minsup) {
				t_events = new ArrayList<>();
				Collections.addAll(t_events, strToList(episodes[i].toString()));
				t_epi = new Episode(t_events);
				occurrences = singleEventEpisodes.get(episodes[i].toString());
				t_epi.setOccurrences(occurrences);
				t_epi.setExpectedSupport(t_sup);
				frequentEpisodes.add(t_epi);
				episodeCount = episodeCount + 1;
			}
		}

		List<Episode> t_freq = frequentEpisodes;
		int i = 0, j;
		int size = t_freq.size();
		// int k = 0;
		while (i < size) {
			j = 0;
			alpha = t_freq.get(i);
			while (j < size) {
				t_events = new ArrayList<>();
				Collections.addAll(t_events, strToList(alpha.toString()));
				t_events.add(t_events.size(), strToList(t_freq.get(j).toString())[0]);
				t_epi = new Episode(t_events);
				this.CandidateEpisodesCount++;
				t_sup = t_epi.getExpectedSupport();
				if (isInjective(t_epi.getEvents())) {
					List<Occurrence> newOccurrences = distinctOccurrenceRecognition(alpha, t_freq.get(j));
					// t_epi.setOccurrences(newOccurrences);
					t_sup = expectedSupport(newOccurrences);
					// t_epi.setExpectedSupport();
					if (t_sup >= minsup) {
						t_epi.setOccurrences(newOccurrences);
						t_epi.setExpectedSupport(t_sup);
						frequentEpisodes.add(t_epi);
						alpha = t_epi;
						if (t_epi.getEvents().size() > maxsize) {
							maxsize = t_epi.getEvents().size();
						}
					}
				}
				j++;
			}
			i++;
		}
		this.endExecutionTime = System.currentTimeMillis();
		MemoryLogger.getInstance().checkMemory();
		// nbFrequent = f_episode.size();
		return frequentEpisodes;

	}

	/**
	 * Find frequent episodes using support.
	 * 
	 * @param minsup              the support threshold
	 * @param singleEventEpisodes list of candidate episodes of size 1
	 * @return the list of frequent episode under distinct occurrences-based
	 *         frequency
	 * @throws IOException
	 */
	public List<Episode> findFrequentEpisodesEMDO(double minsup, Map<String, List<Occurrence>> singleEventEpisodes)
			throws IOException {
		lastRunWasEMDOP = false;
		validRules = null;

		MemoryLogger.getInstance().reset();
		this.startExecutionTime = System.currentTimeMillis();
		Object[] episodes = singleEventEpisodes.keySet().toArray();

		Episode gamma, alpha;
		List<Occurrence> occurrences;
		ArrayList<String> t_events;
		int t_sup;
		for (int i = 0; i < episodes.length; i++) {
			t_sup = singleEventEpisodes.get(episodes[i].toString()).size();
			if (t_sup >= minsup) {
				t_events = new ArrayList<>();
				Collections.addAll(t_events, strToList(episodes[i].toString()));
				gamma = new Episode(t_events);
				occurrences = singleEventEpisodes.get(episodes[i].toString());
				gamma.setOccurrences(occurrences);
				gamma.setSupport(t_sup);
				this.episodeCount = this.episodeCount + 1;
				frequentEpisodes.add(gamma);
			}
		}
		int i = 0, j, k;
		List<Episode> t_freq = new ArrayList<>(frequentEpisodes);
		Collections.copy(t_freq, frequentEpisodes);
		int thesize = t_freq.size();
		Episode root, beta;
		boolean stop = false;

		while (i < frequentEpisodes.size()) {
			alpha = frequentEpisodes.get(i);
			root = alpha;
			k = 0;
			stop = false;
			while (k < thesize && !stop) {
				alpha = root;
				j = k;
				stop = false;
				while (j < thesize) {
					t_events = new ArrayList<>(alpha.getEvents());
					beta = t_freq.get(j);
					// the new episode is injective
					if (alpha.getEvents().indexOf(beta.getEvents().get(0)) < 0) {
						Collections.copy(t_events, alpha.getEvents());
						t_events.add(beta.getEvents().get(0));
						gamma = new Episode(t_events);
						if (!exists(gamma, frequentEpisodes)) {
							occurrences = distinctOccurrenceRecognition(alpha, beta);
							t_sup = occurrences.size();
							CandidateEpisodesCount++;
							if (t_sup >= minsup) {
								gamma.setOccurrences(occurrences);
								gamma.setSupport(t_sup);
								frequentEpisodes.add(gamma);
								this.episodeCount = this.episodeCount + 1;
								alpha = gamma;
								if (gamma.getEvents().size() >= maxsize) {
									maxsize = gamma.getSize();
								}
							}
						}
					}
					gamma = null;
					t_events = null;
					j = j + 1;
				}
				k = k + 1;
				if (alpha.Equals(root))
					stop = true;
			}
			i = i + 1;
			Runtime.getRuntime().gc();
		}
		this.endExecutionTime = System.currentTimeMillis();
		MemoryLogger.getInstance().checkMemory();

		return frequentEpisodes;
	}

	/**
	 * Scans an input sequence file to extract episodes of size 1.
	 *
	 * @param path      the path to the input dataset
	 * @param isComplex whether the input format is complex (multiple events per
	 *                  line)
	 * @param withProb  whether the input contains probabilistic events
	 * @return map from episode string to list of occurrences
	 * @throws IOException if reading the file fails
	 */
	public Map<String, List<Occurrence>> scanSequence(String path, boolean isComplex, boolean withProb)
	        throws IOException {

	    BufferedReader reader = new BufferedReader(new FileReader(path));
	    String line;

	    Map<String, List<Occurrence>> SingleEventEpisode = new HashMap<>();
	    double prob;
	    int timeStamp;
	    ArrayList<String> events;
	    Occurrence occ;
	    Episode epi;

	    while ((line = reader.readLine()) != null) {
	        line = line.trim();
	        if (line.isEmpty()) continue;

	        // Find last '|' for separating timestamp (avoid splitting event|prob!)
	        int lastPipeIndex = line.lastIndexOf('|');
	        if (lastPipeIndex == -1 || lastPipeIndex == line.length() - 1) continue;

	        String eventPart = line.substring(0, lastPipeIndex).trim();
	        String timestampPart = line.substring(lastPipeIndex + 1).trim();
	        timeStamp = Integer.parseInt(timestampPart);

	        if (isComplex) {
	            String[] _events = eventPart.split(" ");
	            for (String e : _events) {
	                events = new ArrayList<>();
	                occ = new Occurrence();

	                if (withProb) {
	                    String[] probSplit = e.split("\\|");
	                    if (probSplit.length != 2) continue;
	                    String eventName = probSplit[0];
	                    prob = Double.parseDouble(probSplit[1]);
	                    events.add(eventName);
	                    occ.insertProb(prob);
	                    occ.addEvent(eventName);
	                } else {
	                    events.add(e);
	                    occ.insertProb(1);
	                    occ.addEvent(e);
	                }

	                epi = new Episode(events);
	                occ.insertTimeStamp(timeStamp);
	                SingleEventEpisode.computeIfAbsent(epi.toString(), k -> new ArrayList<>()).add(occ);
	            }
	        } else {
	            events = new ArrayList<>();
	            occ = new Occurrence();

	            if (withProb) {
	                String[] probSplit = eventPart.split("\\|");
	                if (probSplit.length != 2) continue;
	                String eventName = probSplit[0];
	                prob = Double.parseDouble(probSplit[1]);
	                events.add(eventName);
	                occ.insertProb(prob);
	                occ.addEvent(eventName);
	            } else {
	                events.add(eventPart);
	                occ.insertProb(1);
	                occ.addEvent(eventPart);
	            }

	            epi = new Episode(events);
	            occ.insertTimeStamp(timeStamp);
	            SingleEventEpisode.computeIfAbsent(epi.toString(), k -> new ArrayList<>()).add(occ);
	        }
	    }

	    reader.close();
	    this.CandidateEpisodesCount = SingleEventEpisode.size();
	    return SingleEventEpisode;
	}



	/**
	 * Computes the support of the episode rule alpha => beta.
	 *
	 * @param alpha the antecedent episode
	 * @param beta  the consequent episode
	 * @return number of rule-supporting occurrences
	 */
	static int episodeRuleSupport(Episode alpha, Episode beta) {
		List<Occurrence> alpha_occurrences = alpha.getOccurrences();
		List<Occurrence> beta_occurrences = beta.getOccurrences();
		int i = 0, j = 0, k;
		boolean trouve;
		int taille_1 = alpha_occurrences.size();
		int taille_2 = beta_occurrences.size();
		int rule_support = 0;
		Occurrence occ_1, occ_2;

		do {
			occ_1 = alpha_occurrences.get(i);
			trouve = false;
			k = j;
			while (k < taille_2 && !trouve) {
				occ_2 = beta_occurrences.get(k);
				if (((occ_1.getStart() < occ_2.getStart()) && (occ_1.getEnd() < occ_2.getEnd()))) {
					rule_support = rule_support + 1;
					i = i + 1;
					trouve = true;
				}
				k = k + 1;
			}
			j = k;
		} while ((j < taille_2) && (i < taille_1));
		return rule_support;
	}

	/**
	 * Pruning episode rules with pruning by applying the properties mentionned in
	 * the article
	 *
	 * @param FrequentEpisodes List of frequent episodes obtained using
	 *                         FrequentEpisodes function mentionned before
	 * @param minconf          The user defined confidence threshold
	 * @param minsup           The user defined support threshold
	 * @return the set of pruned episode rules
	 */
	public List<String> generateEpisodeRulesWithPruning(List<Episode> FrequentEpisodes, double minconf) {
		MemoryLogger.getInstance().reset();
		this.startExecutionTime = System.currentTimeMillis();
		validRules = new ArrayList<>();
		List<Episode> P = new ArrayList<>();
		List<Occurrence> occurrences;
		Episode t_epi;
		ArrayList<String> t_events;
		List<Episode> f_copy = FrequentEpisodes;
		for (Episode episode : f_copy) {
			if (episode.getEvents().size() == 1) {
				t_events = new ArrayList<>();
				Collections.addAll(t_events, strToList(episode.toString()));
				t_epi = new Episode(t_events);
				occurrences = episode.getOccurrences();
				t_epi.setOccurrences(occurrences);
				t_epi.setSupport(occurrences.size());
				P.add(t_epi);
			} else {
				break;
			}
		}
		int j = 0, i = 0, k = 0, x = 0, rule_support = 0;
		int size_P = P.size(), size_F = FrequentEpisodes.size();
		Episode alpha = null, beta = null, root = null, gamma = null;
		ArrayList<String> events;
		ArrayList<Episode> validcons;
		validcons = new ArrayList<>(P);
		Collections.copy(validcons, P);
		double conf;
		while (i < size_F) {
			alpha = FrequentEpisodes.get(i);
			j = 0;

			validcons = new ArrayList<>(P);
			Collections.copy(validcons, P);
			while (j < size_P) {
				beta = P.get(j);
				rule_support = episodeRuleSupport(alpha, beta);
				conf = ((double) rule_support / alpha.getSupportAsDouble(lastRunWasEMDOP));

				if (conf >= minconf) {
					validcons.add(beta);
					validRules.add(alpha + " ==> " + beta + " #SUP: " + rule_support + " #CONF: " + conf);

				} else {
					validcons.remove(beta);
				}
				j++;
			}

			x = 0;
			int size_1 = validcons.size();
			while (x < validcons.size()) {
				beta = validcons.get(x);
				root = beta;
				j = 0;
				while (j < size_1) {
					k = j;
					beta = root;
					while (k < size_1) {
						events = new ArrayList<>(beta.getEvents());
						Collections.copy(events, beta.getEvents());
						events.add(validcons.get(k).getEvent(0));
						gamma = new Episode(events);
						boolean t = false;
						for (Episode delta : FrequentEpisodes) {
							if (delta.Equals(gamma)) {
								gamma.setOccurrences(delta.getOccurrences());
								t = true;
								break;
							}
						}
						if (t) {
							if (!exists(gamma, validcons)) {
								rule_support = episodeRuleSupport(alpha, gamma);
								conf = ((double) rule_support / alpha.getSupportAsDouble(lastRunWasEMDOP));
								if (conf >= minconf) {
									beta = gamma;
									validcons.add(beta);
									validRules.add(alpha + " ==> " + beta + " #SUP: " + rule_support + " #CONF: " + conf);

								}
							}
						}
						k = k + 1;
					}
					j = j + 1;
				}
				x = x + 1;
			}
			i = i + 1;
		}
		this.endExecutionTime = System.currentTimeMillis();
		MemoryLogger.getInstance().checkMemory();
		return validRules;
	}

	/**
	 * Generates all possible episode rules from the frequent episodes.
	 *
	 * @param frequentEpisodes list of frequent episodes
	 * @param minconf          minimum confidence threshold
	 * @return list of episode rules
	 */
	public List<String> generateEpisodeRules(List<Episode> frequentEpisodes, double minconf) {
		MemoryLogger.getInstance().reset();
		this.startExecutionTime = System.currentTimeMillis();
		validRules = new ArrayList<>();
		double conf;
		int rule_support;
		int i = 0, j = 0;
		Episode alpha, beta;
		while (i < frequentEpisodes.size()) {
			alpha = frequentEpisodes.get(i);
			j = 0;
			while (j < frequentEpisodes.size()) {
				beta = frequentEpisodes.get(j);
				rule_support = episodeRuleSupport(alpha, beta);
				conf = ((double) rule_support / alpha.getSupportAsDouble(lastRunWasEMDOP));
				if (conf >= minconf) {
					validRules.add(alpha.toString() + " ==> " + beta.toString() + " #SUP: " + rule_support + " #CONF: " + conf);
				}
				j++;
				beta = null;
			}
			i++;
			alpha = null;
		}
		this.endExecutionTime = System.currentTimeMillis();
		MemoryLogger.getInstance().checkMemory();
		return validRules;
	}

	/**
	 * Converts a string representation of an episode to a list of event strings.
	 *
	 * @param string the string format (e.g., {A,B}")
	 * @return array of event strings
	 */
	public static String[] strToList(String string) {
		int index_1 = string.indexOf("{");
		String tempString = string.substring(index_1 + 1, string.length() - 1);
		if (tempString.contains(",")) {
			return tempString.split(",");
		}
		return new String[] { tempString };
	}

	/**
	 * Print statistics and return them also as a String.
	 * 
	 * @return the string
	 */
	public void printStats() {
		StringBuilder stats = new StringBuilder();
		String newline = System.lineSeparator();

		String algoName = lastRunWasEMDOP ? "EMDO-P" : "EMDO";
		String isRules = (validRules == null) ? "-Episodes" : "-Rules";

		stats.append(newline).append("============= ").append(algoName).append(isRules).append(" - STATS =============")
				.append(newline).append(" Candidates count         : ").append(this.CandidateEpisodesCount)
				.append(newline).append(" Algorithm stopped at size: ").append(maxsize).append(newline);
		if (validRules != null)
			stats.append(" Frequent rule count  : ").append(validRules.size()).append(newline);
		else
			stats.append(" Frequent episode count  : ").append(episodeCount).append(newline);
		
		stats.append(" Maximum memory usage     : ").append(MemoryLogger.getInstance().getMaxMemory()).append(" mb")
				.append(newline).append(" Total time ~             : ")
				.append(this.endExecutionTime - this.startExecutionTime).append(" ms").append(newline)
				.append("============================================");

		System.out.println(stats);
	}

	/**
	 * Returns the index of a given episode in a list, if it exists.
	 *
	 * @param alpha            the episode to find
	 * @param FrequentEpisodes list of frequent episodes
	 * @return index if found, -1 otherwise
	 */
	public int indexOf(Episode alpha, List<Episode> FrequentEpisodes) {
		int size_F = FrequentEpisodes.size();
		Episode beta;
		boolean stop = false, founded = false;
		int i = 0, index = -1;
		while ((i < size_F) && !stop) {
			beta = FrequentEpisodes.get(i);
			founded = beta.Equals(alpha);
			if (founded) {
				stop = true;
				index = i;
			}
			i = i + 1;
		}
		return index;
	}

	/**
	 * Checks whether a given episode exists in a list.
	 *
	 * @param epi the episode to check
	 * @param F   the list of episodes
	 * @return true if found, false otherwise
	 */
	public boolean exists(Episode epi, List<Episode> F) {
		if ((F.size() == 0) || (F == null))
			return false;

		boolean trouve = false;
		int i = 0;
		while (i < F.size() && !trouve) {
			if (epi.Equals(F.get(i))) {
				trouve = true;
			}
			i++;
		}
		return trouve;
	}

	/**
	 * Returns the list of valid episode rules.
	 *
	 * @return the list of valid rules
	 */
	public List<String> getValidRules() {
		return validRules;
	}



	/**
	 * Save the list of frequent episodes to a file in SPMF format.
	 *
	 * @param outputFile the path of the output file
	 * @throws IOException if an error occurs while writing the file
	 */
	public void saveEpisodesToFile(String outputFile) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
		for (Episode episode : frequentEpisodes) {
			writer.write(episode.toStringSPMF(lastRunWasEMDOP));
			writer.newLine();
		}
		writer.close();
	}

	/**
	 * Save the list of valid rules to a file.
	 *
	 * @param outputFile the path of the output file
	 * @throws IOException if an error occurs while writing the file
	 */
	public void saveRulesToFile(String outputFile) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
		for (String rule : validRules) {
			writer.write(rule);
			writer.newLine();
		}
		writer.close();
	}
}