package ca.pfv.spmf.algorithms.episodes.poerm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import ca.pfv.spmf.tools.MemoryLogger;

/* This file is copyright (c) 2021  CHEN YANGMING, Philippe Fournier-Viger
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
 * This is an implementation of the POERM-ALL algorithm <br/>
 * <br/>
 *
 * Paper: Mining Partially-Ordered Episode Rules in an Event Sequence

 * @author Yangming Chen & Philippe Fournier-Viger
 */
public class AlgoPOERM {
	/** the input file */
	private String inputFile;

	/** the start time of program run */
	private long startTime;

	/** the end time of program run */
	private long endTime;

	/** the runtime of program run */
	private long deltaTime;

	public long getDeltaTime() {
		return deltaTime;
	}

	/** the end time of database sequence */
	private int end = 0;

	/** a datastruct to record parameter of the algorithm */
	private POERParas parameter = null;

	/**
	 * a sequence that eliminates all events having less than minsup occurrences
	 * from the input dataset
	 */
	private Map<Integer, List<Integer>> XFreS;

	/**
	 * a sequence that eliminates all events having less than minsup*minconf
	 * occurrences from the input dataset
	 */
	private Map<Integer, List<Integer>> YFreS;

	/** a map to record item and its appear time interval */
	private Map<Integer, List<Interval>> thisAppear;

	/** a list to record xEventSet */
	private List<EventSetAppear> XFreAppear;

	/** a list to record yEventSet */
	private List<EventSetAppear> YFreAppear;

	/** a list to record vaild poerm rule */
	private List<POERRule> ruleAppear;

	/** Double formatting code */
	private static final String DOUBLE_FORMAT = "%.4f";
	/**
	 * Convert a double to a formatted string
	 * 
	 * @param value the double value
	 * @return the strings
	 */
	private String asString(double value) {
		return String.format(DOUBLE_FORMAT, value);
	}

	/** Maximum memory used during the last execution */
	private double maxMemory;
	
	public double getMaxMemory() {
		return maxMemory;
	}

	private int maxWindowNum;
	
	public int getMaxWindowNum() {
		return maxWindowNum;
	}

	/**
	 * Default constructor
	 */
	public AlgoPOERM() throws FileNotFoundException {
		// empty
	}

	/**
	 * Run the algorithm
	 * 
	 * @param inputFile     The input file path.
	 * @param xSpan         the XSpan of POERM algorithm.
	 * @param ySpan         the YSpan of POERM algorithm.
	 * @param minConfidence the min confidence of POERM algorithm.
	 * @param xySpan        the XYSpan of POERM algorithm
	 * @param selfIncrement If the input file does not contain timestamps, then set
	 *                      this variable to true to automatically assign timestamps
	 *                      as 1,2,3...
	 */
	public List<POERRule> runAlgorithm(String inputFile, int minSupport, int xSpan, int ySpan, double minConfidence, int xySpan,
			boolean selfIncrement) {

		/** Initialize data structures */
		XFreS = new HashMap<Integer, List<Integer>>();
		YFreS = new HashMap<Integer, List<Integer>>();
		thisAppear = new HashMap<Integer, List<Interval>>();
		XFreAppear = new ArrayList<EventSetAppear>();
		YFreAppear = new ArrayList<EventSetAppear>();
		ruleAppear = new ArrayList<POERRule>();

		/** save input file path and parameters */
		this.inputFile = inputFile;
		this.parameter = new POERParas(minSupport, xSpan, ySpan, minConfidence, xySpan, selfIncrement);

		MemoryLogger.getInstance().reset();
		this.startTime = System.currentTimeMillis();

		this.preProcess(this.inputFile);

		this.miningXEventSet();
		this.findRule(this.YFreS, this.parameter.getYSpan(), this.parameter.getXYSpan());

		this.endTime = System.currentTimeMillis();

		this.deltaTime = this.endTime - this.startTime;
//		this.printRule();
		MemoryLogger.getInstance().checkMemory();
		maxMemory = MemoryLogger.getInstance().getMaxMemory();
		return this.ruleAppear;
//		System.err.println("Execution time: " + this.deltaTime + " " + );
	}

	/**
	 * a Comparator to sort interval by its end.
	 */
	public class myComparator implements Comparator<Interval> {
		public int compare(Interval a, Interval b) {
			if (a.end == b.end) {
				return a.start - b.start;
			}
			return a.end - b.end;
		}
	}
	
	/**
	 * Read the dataset Convert the item in the dataset into numbers and build a map
	 * for it. record each item occur time eliminates all events having less than
	 * minsup occurrences from the input dataset to obtain a sequence XFres and less
	 * than minsup * minconf occurrences to obtain a sequence YFres filter out
	 * frequent-1 item in XFreAppear and YFreAppear
	 */
	private void preProcess(String input) {
		// TODO Auto-generated method stub
		try {
			HashMap<Integer, Integer> eventSet = new HashMap<Integer, Integer>();

			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(input), "UTF-8"));

			String line = null;
			int timestamp = 1;
			Integer num;
			int preTimestamp = -1;
			int maxWindow = this.parameter.getXSpan() + this.parameter.getYSpan() + this.parameter.getXYSpan();
			this.maxWindowNum = 0;
			// if self increment mode
			if (parameter.isSelfIncrement() == true) {
				while ((line = reader.readLine()) != null) {
//	                current_TID++;
					// if the line is a comment, is empty or is a
					// kind of metadata
					if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
						continue;
					}

					String[] array = line.split(" ");

					List<Integer> eSet = new ArrayList<Integer>();
					List<Integer> eSet2 = new ArrayList<Integer>();
					for (String event : array) {
						// Convert the item in the dataset into numbers and build a map for it.
						num = Integer.parseInt(event);
						Integer support = eventSet.get(num);
						if (support != null) {
							eventSet.put(num, support + 1);
							thisAppear.get(num).add(new Interval(timestamp, timestamp));
						} else {
							eventSet.put(num, 1);
							List<Interval> interval = new ArrayList<Interval>();
							interval.add(new Interval(timestamp, timestamp));
							this.thisAppear.put(num, interval);
						}
						// use list save the data in this timestamp
						eSet.add(num);
						eSet2.add(num);
					}
					if (eSet.size() > 0) {
						// use hashMap save the data in this timestamp
						this.XFreS.put(timestamp, eSet2);
					}
					if (preTimestamp == -1) {
						preTimestamp = timestamp;
					} if (timestamp - preTimestamp > maxWindow) {
						preTimestamp = timestamp;
						this.maxWindowNum++;
					}
					timestamp++;
				}
			} else {
				while ((line = reader.readLine()) != null) {
//	                current_TID++;
					// if the line is a comment, is empty or is a
					// kind of metadata
					if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
						continue;
					}

					String[] lineSplited = line.split("\\|");

					timestamp = Integer.parseInt(lineSplited[1]);

					String[] array = lineSplited[0].split(" ");

					List<Integer> eSet = new ArrayList<Integer>();
					List<Integer> eSet2 = new ArrayList<Integer>();
					for (String event : array) {
						// Convert the item in the dataset into numbers and build a map for it.
						num = Integer.parseInt(event);
						Integer support = eventSet.get(num);
						if (support != null) {
							eventSet.put(num, support + 1);
							thisAppear.get(num).add(new Interval(timestamp, timestamp));
						} else {
							eventSet.put(num, 1);
							List<Interval> interval = new ArrayList<Interval>();
							interval.add(new Interval(timestamp, timestamp));
							this.thisAppear.put(num, interval);
						}
						// use list save the data in this timestamp
						eSet.add(num);
						eSet2.add(num);
					}
					if (eSet.size() > 0) {
						// use hashMap save the data in this timestamp
						this.XFreS.put(timestamp, eSet2);
					}
					if (preTimestamp == -1) {
						preTimestamp = timestamp;
					} if (timestamp - preTimestamp > maxWindow) {
						preTimestamp = timestamp;
						this.maxWindowNum++;
					}
//					timestamp++;
				}
			}

			MemoryLogger.getInstance().checkMemory();
			// eliminates all events having less than minsup occurrences from the input
			// dataset to obtain a sequence XFres
			// and less than minsup * minconf occurrences to obtain a sequence YFres
			loadFrequent(eventSet);
			reader.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * eliminates all events having less than minsup occurrences from the input
	 * dataset to obtain a sequence XFres and less than minsup * minconf occurrences
	 * to obtain a sequence YFres filter out frequent-1 item in XFreAppear and
	 * YFreAppear
	 */
	private void loadFrequent(Map<Integer, Integer> eventSet) {
		// TODO Auto-generated method stub
		for (Entry<Integer, List<Integer>> entry : this.XFreS.entrySet()) {
			List<Integer> eSet = entry.getValue();
			List<Integer> XnewList = new ArrayList<Integer>();
			List<Integer> YnewList = new ArrayList<Integer>();
			for (Integer e : eSet) {
				int support = eventSet.get(e);
				if (support >= this.parameter.getMinSupport() * this.parameter.getMinConfidence()) {
					YnewList.add(e);
					if (support >= this.parameter.getMinSupport()) {
						XnewList.add(e);
					}
				}
			}
			this.XFreS.put(entry.getKey(), XnewList);
			this.YFreS.put(entry.getKey(), YnewList);
		}

		for (Entry<Integer, Integer> entry : eventSet.entrySet()) {
			Integer key = entry.getKey();
			Integer val = entry.getValue();
			ArrayList<Integer> numKey = new ArrayList<Integer>();

			numKey.add(key);
			if ((double) val >= (double) this.parameter.getMinSupport() * this.parameter.getMinConfidence()) {
//				YFreAppear.put(numKey, XFreAppear.get(numKey));
				List<Interval> value = this.thisAppear.get(key);
//				System.out.println("key: " + key);
				this.YFreAppear.add(new EventSetAppear(numKey, value));
				if (val >= this.parameter.getMinSupport()) {
					this.XFreAppear.add(new EventSetAppear(numKey, value));
				}
//				this.TotalFreAppear.add(new POERAppear(numKey, value));
			}
		}
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Find all XEventSet that maybe the anti episode of a Partially-Ordered Episode
	 * Rule
	 */
	public void miningXEventSet() {
		try {
			int index = 0;
			int end = this.XFreAppear.size();

			while (index < end) {
				this.thisAppear.clear();
				EventSetAppear episodeAppear = this.XFreAppear.get(index);
				index++;
				// Frequent-i item
				List<Integer> episode = episodeAppear.getEventSet();

				Integer compareKey = episode.get(episode.size() - 1);

				List<Interval> appear = episodeAppear.getIntervals();
				for (Interval interval : appear) {
					Integer intStart = interval.start;
					Integer intEnd = interval.end;
					// for a frequent-i itemset and its time intervals[interval.start,
					// interval.end),
					// Search the time intervals [interval.end - XSpan + 1, interval.start) to add
					// each event setF∪{e}
					// such that e > frequent-i itemset's lastItemand and its occurrences in the map
					// fresMap;
					for (int j = intEnd - this.parameter.getXSpan() + 1; j < intStart; ++j) {
						if (!this.XFreS.containsKey(j)) {
							continue;
						}
						List<Integer> eventSet = this.XFreS.get(j);
						for (Integer eventItem : eventSet) {
							// add each event setF∪{e}
							// such that e > frequent-i itemset's lastItemand and its occurrences in the map
							// fresMap;
							if (eventItem > compareKey) {
								if (this.thisAppear.containsKey(eventItem)) {
									this.thisAppear.get(eventItem).add(new Interval(j, intEnd));
								} else {
									List<Interval> appearTime = new ArrayList<Interval>();
									appearTime.add(new Interval(j, intEnd));
									this.thisAppear.put(eventItem, appearTime);
								}
							}
						}
					}
					// Search the time intervals [interval.end + 1, interval.start + XSpan)
					for (int j = intEnd + 1; j < intStart + this.parameter.getXSpan(); ++j) {
						if (!this.XFreS.containsKey(j)) {
							continue;
						}
						List<Integer> eventSet = this.XFreS.get(j);
						for (Integer eventItem : eventSet) {
							if (eventItem > compareKey) {
								if (this.thisAppear.containsKey(eventItem)) {
									this.thisAppear.get(eventItem).add(new Interval(intStart, j));
								} else {
									ArrayList<Interval> appearTime = new ArrayList<Interval>();
									appearTime.add(new Interval(intStart, j));
									this.thisAppear.put(eventItem, appearTime);
								}
							}
						}
					}
					// Search the time intervals [intStart, intEnd]
					for (int j = intStart; j <= intEnd; ++j) {
						if (!this.XFreS.containsKey(j)) {
							continue;
						}
						List<Integer> eventSet = this.XFreS.get(j);
						for (Integer eventItem : eventSet) {
							if (eventItem > compareKey) {
								if (this.thisAppear.containsKey(eventItem)) {
									this.thisAppear.get(eventItem).add(new Interval(intStart, intEnd));
								} else {
									List<Interval> appearTime = new ArrayList<Interval>();
									appearTime.add(new Interval(intStart, intEnd));
									this.thisAppear.put(eventItem, appearTime);
								}
							}
						}
					}
				}
				// Add each pair of fresMap such that |value|≥minsup into XFreAppear;
				for (Entry<Integer, List<Interval>> curAppearentry : this.thisAppear.entrySet()) {
//					System.out.println("Finded ？");
					Integer key = curAppearentry.getKey();
					List<Interval> value = curAppearentry.getValue();
					value.sort(new myComparator());
					List<Interval> newValue = new ArrayList<Interval>();
					for (int i = 0; i < value.size(); ++i) {
						if (i == 0 || !value.get(i).equal(newValue.get(newValue.size() - 1))) {
							newValue.add(value.get(i));
						}
					}
					if (newValue.size() >= this.parameter.getMinSupport()) {
						List<Integer> newKey = new ArrayList<Integer>(episode);
						newKey.add(key);
						this.XFreAppear.add(new EventSetAppear(newKey, newValue));
					}
				}
				end = this.XFreAppear.size();
				MemoryLogger.getInstance().checkMemory();
			}
			System.out.println("end: " + end);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {

		}
	}

	/**
	 * Searches for event sets (consequents) that could be combined with these
	 * antecedents to create valid POERs
	 */
	public void findRule(Map<Integer, List<Integer>> itemFreS, int window_size, int span) {
		Map<Integer, ArrayList<RuleInterval>> conseRecodeMap = new HashMap<Integer, ArrayList<RuleInterval>>();
		BitSet visited = new BitSet(this.end);
		System.out.println("thfee+" + this.XFreAppear.size());
		for (EventSetAppear anitemset : this.XFreAppear) {
			// for one anit episode and its appear time intervals
			List<Integer> anitKey = anitemset.getEventSet();
			List<Interval> anitValues = anitemset.getIntervals();
			int anitStart = -1;
			int anitCount = 0;

			// if anit episode's appear time less than MinSupport, skip it.
			for (Interval anitValue : anitValues) {
				if (anitValue.start <= anitStart) {
					continue;
				}
				anitCount++;
				anitStart = anitValue.end;
			}
			if (anitCount < this.parameter.getMinSupport()) {
				continue;
			}

			conseRecodeMap.clear();
			// Scan each timestamp of YFres in anit episode OccurrenceList to
			// obtain a map conseMap that records each event e and its occurrence list;
			for (Interval anitValue : anitValues) {
//		    	visited.clear();
				for (int i = 1 + anitValue.end; i < span + anitValue.end + window_size; ++i) {
					if (!itemFreS.containsKey(i) || itemFreS.get(i).size() == 0) {
						continue;
					}
//		    		visited.set(i);
					RuleInterval thisInterval = new RuleInterval(anitValue.start, anitValue.end, i, i);
					List<Integer> itemSet = itemFreS.get(i);
					for (Integer item : itemSet) {
//		    			if (anitKey.size() == 1 && anitKey.get(0).equals(item)) {
//		    				continue;
//		    			}
						if (conseRecodeMap.containsKey(item)) {
							conseRecodeMap.get(item).add(thisInterval);
						} else {
							ArrayList<RuleInterval> intervalList = new ArrayList<RuleInterval>();
							intervalList.add(thisInterval);
							conseRecodeMap.put(item, intervalList);
						}
					}
				}
			}
			// Scan conseRecodeMap and put the pair (x−→e, OccurrenceList) in a
			// queuecandidateRuleQueue
			// (note: infrequent rules are kept because eventemay be extended to obtain some
			// frequent rules)
			for (Entry<Integer, ArrayList<RuleInterval>> conseRecodeMapItem : conseRecodeMap.entrySet()) {
				Integer key = conseRecodeMapItem.getKey();
				List<POERRuleOccur> ruleOccur = new ArrayList<POERRuleOccur>();
				List<RuleInterval> occurList = conseRecodeMapItem.getValue();
				if (occurList.size() < anitCount * this.parameter.getMinConfidence()) {
					continue;
				}
				int possibleRuleStart = -1;
				int possibleRuleCount = 0;
				int realRuleStart = -1;
				int realRuleCount = 0;

				for (RuleInterval occur : occurList) {
					if (occur.antiStart > realRuleStart && occur.start - occur.antiEnd <= span) {
						realRuleCount++;
						realRuleStart = occur.end;
					}
					if (occur.antiStart > possibleRuleStart) {
						possibleRuleCount++;
						possibleRuleStart = occur.end;
					}
				}
				if (possibleRuleCount < anitCount * this.parameter.getMinConfidence()) {
					continue;
				}
				List<Integer> conseEpi = new ArrayList<Integer>();
				conseEpi.add(key);
				if (realRuleCount >= anitCount * this.parameter.getMinConfidence() && !anitKey.equals(conseEpi)) {
//			    	System.out.println("outside find rule " + anitKey + " " + conseEpi + " " + anitCount + " " + realRuleCount);
					this.ruleAppear.add(new POERRule(anitKey, conseEpi, null, anitCount, realRuleCount));
				}

				Map<Integer, List<RuleInterval>> tempRuleMap = new HashMap<Integer, List<RuleInterval>>();
				// extend a rule with i-item to rules with i+1-item
				for (RuleInterval occur : occurList) {
					int intervalStart = Math.max(occur.antiEnd + 1, occur.end - this.parameter.getYSpan() + 1);
					// search [intervalStart, occur.start) to extend the rule
					for (int i = intervalStart; i < occur.start; ++i) {
						if (!itemFreS.containsKey(i) || itemFreS.get(i).size() == 0) {
							continue;
						}
						List<Integer> eventSet = itemFreS.get(i);
						RuleInterval ruleInteval = new RuleInterval(occur.antiStart, occur.antiEnd, i, occur.end);
						for (Integer eventItem : eventSet) {
							if (eventItem > key) {
								if (tempRuleMap.containsKey(eventItem)) {
									tempRuleMap.get(eventItem).add(ruleInteval);
								} else {
									ArrayList<RuleInterval> appearTime = new ArrayList<RuleInterval>();
									appearTime.add(ruleInteval);
									tempRuleMap.put(eventItem, appearTime);
								}
							}
						}
					}
					// search [occur.start, occur.end] to extend the rule
					for (int i = occur.start; i <= occur.end; ++i) {
						if (!itemFreS.containsKey(i) || itemFreS.get(i).size() == 0) {
							continue;
						}
						List<Integer> eventSet = itemFreS.get(i);
						RuleInterval ruleInteval = new RuleInterval(occur.antiStart, occur.antiEnd, occur.start,
								occur.end);
						for (Integer eventItem : eventSet) {
							if (eventItem > key) {
								if (tempRuleMap.containsKey(eventItem)) {
									tempRuleMap.get(eventItem).add(ruleInteval);
								} else {
									List<RuleInterval> appearTime = new ArrayList<RuleInterval>();
									appearTime.add(ruleInteval);
									tempRuleMap.put(eventItem, appearTime);
								}
							}
						}
					}
					int intervalEnd = Math.min(occur.antiEnd + span + window_size, occur.start + window_size);
					// search [occur.end + 1, intervalEnd) to extend the rule
					for (int i = occur.end + 1; i < intervalEnd; ++i) {
						if (!itemFreS.containsKey(i) || itemFreS.get(i).size() == 0) {
							continue;
						}
						List<Integer> eventSet = itemFreS.get(i);
						RuleInterval ruleInteval = new RuleInterval(occur.antiStart, occur.antiEnd, occur.start, i);
						for (Integer eventItem : eventSet) {
							if (eventItem > key) {
								if (tempRuleMap.containsKey(eventItem)) {
									tempRuleMap.get(eventItem).add(ruleInteval);
								} else {
									List<RuleInterval> appearTime = new ArrayList<RuleInterval>();
									appearTime.add(ruleInteval);
									tempRuleMap.put(eventItem, appearTime);
								}
							}
						}
					}
				}
				// scan tempRuleMap and put vaild rule in ruleAppear, and possible rule in
				// ruleOccur
				for (Entry<Integer, List<RuleInterval>> ruleMap : tempRuleMap.entrySet()) {
					Integer tempRuleMapKey = ruleMap.getKey();
					List<RuleInterval> tempRuleMapInterval = ruleMap.getValue();
					if (tempRuleMapInterval.size() < anitCount * this.parameter.getMinConfidence()) {
						continue;
					}
					int tempPossibleRuleStart = -1;
					int tempPossibleRuleCount = 0;
					int tempRealRuleStart = -1;
					int tempRealRuleCount = 0;
					for (RuleInterval tempRuleMapInter : tempRuleMapInterval) {
						if (tempRuleMapInter.antiStart > tempRealRuleStart
								&& tempRuleMapInter.start - tempRuleMapInter.antiEnd <= span) {
							tempRealRuleCount++;
							tempRealRuleStart = tempRuleMapInter.end;
						}
						if (tempRuleMapInter.antiStart > tempPossibleRuleStart) {
							tempPossibleRuleCount++;
							tempPossibleRuleStart = tempRuleMapInter.end;
						}
					}

					if (tempPossibleRuleCount < anitCount * this.parameter.getMinConfidence()) {
						continue;
					}
					List<Integer> conseEpisode = new ArrayList<Integer>();
					conseEpisode.add(key);
					conseEpisode.add(tempRuleMapKey);
//				    if (anitKey.get(0) == 39) {
//				    	System.out.println("add");
//				    }
					if (tempRealRuleCount >= anitCount * this.parameter.getMinConfidence()
							&& !anitKey.equals(conseEpisode)) {
						this.ruleAppear.add(new POERRule(anitKey, conseEpisode, null, anitCount, tempRealRuleCount));
//					    System.out.println("key " + anitKey + "==>" + conseEpisode + " " + tempRealRuleCount + " / " + anitCount);
					}

					ruleOccur.add(new POERRuleOccur(conseEpisode, tempRuleMapInterval));
					MemoryLogger.getInstance().checkMemory();
				}
				int breadthSearthStart = 0;
				int breadthSearthEnd = ruleOccur.size();
				// extend a rule with i-item to rules with i+1-item
				while (breadthSearthStart < breadthSearthEnd) {
					tempRuleMap.clear();
					POERRuleOccur oneOccurRule = ruleOccur.get(breadthSearthStart);
					breadthSearthStart++;
					List<Integer> episode = oneOccurRule.getEpisode();

					Integer compareKey = episode.get(episode.size() - 1);
					List<RuleInterval> oneOccurRuleInters = oneOccurRule.getIntervals();
					for (RuleInterval oneOccurRuleInter : oneOccurRuleInters) {
						int intervalStart = Math.max(oneOccurRuleInter.antiEnd + 1,
								oneOccurRuleInter.end - this.parameter.getYSpan() + 1);
						for (int i = intervalStart; i < oneOccurRuleInter.start; ++i) {
							if (!itemFreS.containsKey(i) || itemFreS.get(i).size() == 0) {
								continue;
							}
							List<Integer> eventSet = itemFreS.get(i);
							RuleInterval ruleInteval = new RuleInterval(oneOccurRuleInter.antiStart,
									oneOccurRuleInter.antiEnd, i, oneOccurRuleInter.end);
							for (Integer eventItem : eventSet) {
								if (eventItem > compareKey) {
									if (tempRuleMap.containsKey(eventItem)) {
										tempRuleMap.get(eventItem).add(ruleInteval);
									} else {
										ArrayList<RuleInterval> appearTime = new ArrayList<RuleInterval>();
										appearTime.add(ruleInteval);
										tempRuleMap.put(eventItem, appearTime);
									}
								}
							}
						}
						// search [oneOccurRuleInter.start, oneOccurRuleInter.end] to extend the rule
						for (int i = oneOccurRuleInter.start; i <= oneOccurRuleInter.end; ++i) {
							if (!itemFreS.containsKey(i) || itemFreS.get(i).size() == 0) {
								continue;
							}
							List<Integer> eventSet = itemFreS.get(i);
							RuleInterval ruleInteval = new RuleInterval(oneOccurRuleInter.antiStart,
									oneOccurRuleInter.antiEnd, oneOccurRuleInter.start, oneOccurRuleInter.end);
							for (Integer eventItem : eventSet) {
								if (eventItem > compareKey) {
									if (tempRuleMap.containsKey(eventItem)) {
										tempRuleMap.get(eventItem).add(ruleInteval);
									} else {
										ArrayList<RuleInterval> appearTime = new ArrayList<RuleInterval>();
										appearTime.add(ruleInteval);
										tempRuleMap.put(eventItem, appearTime);
									}
								}
							}
						}
						// search [oneOccurRuleInter.end + 1, intervalEnd) to extend the rule
						int intervalEnd = Math.min(oneOccurRuleInter.antiEnd + span + window_size,
								oneOccurRuleInter.start + window_size);
						for (int i = oneOccurRuleInter.end + 1; i < intervalEnd; ++i) {
							if (!itemFreS.containsKey(i) || itemFreS.get(i).size() == 0) {
								continue;
							}
							List<Integer> eventSet = itemFreS.get(i);
							RuleInterval ruleInteval = new RuleInterval(oneOccurRuleInter.antiStart,
									oneOccurRuleInter.antiEnd, oneOccurRuleInter.start, i);
							for (Integer eventItem : eventSet) {
								if (eventItem > compareKey) {
									if (tempRuleMap.containsKey(eventItem)) {
										tempRuleMap.get(eventItem).add(ruleInteval);
									} else {
										ArrayList<RuleInterval> appearTime = new ArrayList<RuleInterval>();
										appearTime.add(ruleInteval);
										tempRuleMap.put(eventItem, appearTime);
									}
								}
							}
						}
					}
					// scan tempRuleMap and put vaild rule in ruleAppear, and possible rule in
					// ruleOccur
					for (Entry<Integer, List<RuleInterval>> ruleMap : tempRuleMap.entrySet()) {
						Integer tempRuleMapKey = ruleMap.getKey();
						List<RuleInterval> tempRuleMapInterval = ruleMap.getValue();
						if (tempRuleMapInterval.size() < anitCount * this.parameter.getMinConfidence()) {
							continue;
						}
						int tempPossibleRuleStart = -1;
						int tempPossibleRuleCount = 0;
						int tempRealRuleStart = -1;
						int tempRealRuleCount = 0;

						for (RuleInterval tempRuleMapInter : tempRuleMapInterval) {
							if (tempRuleMapInter.antiStart > tempRealRuleStart
									&& tempRuleMapInter.start - tempRuleMapInter.antiEnd <= span) {
								tempRealRuleCount++;
								tempRealRuleStart = tempRuleMapInter.end;
							}
							if (tempRuleMapInter.antiStart > tempPossibleRuleStart) {
								tempPossibleRuleCount++;
								tempPossibleRuleStart = tempRuleMapInter.end;
							}
						}
						if (tempPossibleRuleCount < anitCount * this.parameter.getMinConfidence()) {
							continue;
						}
						List<Integer> conseEpisode = new ArrayList<Integer>();
						conseEpisode.addAll(episode);
						conseEpisode.add(tempRuleMapKey);
						if (tempRealRuleCount >= anitCount * this.parameter.getMinConfidence()
								&& !anitKey.equals(conseEpisode)) {

							this.ruleAppear
									.add(new POERRule(anitKey, conseEpisode, null, anitCount, tempRealRuleCount));
//						    System.out.println("find rule " + anitKey + " " + conseEpisode + " " + anitCount + " " + tempRealRuleCount);
						}

						ruleOccur.add(new POERRuleOccur(conseEpisode, tempRuleMapInterval));
					}
					MemoryLogger.getInstance().checkMemory();
					breadthSearthEnd = ruleOccur.size();
				}
			}
		}
	}

	/**
	 * transform and print the rule in the console
	 */
	public void printRule() {
		for (POERRule poerrule : this.ruleAppear) {
			String episodeRule = "";
			List<Integer> antiEpisode = poerrule.getAntiEpisode();
			List<Integer> conseEpisode = poerrule.getConseEpisode();

			for (Integer anti : antiEpisode) {
				episodeRule += anti + " ";
			}
			episodeRule += "==> ";
			for (Integer conse : conseEpisode) {
				episodeRule += conse + " ";
			}
			System.out.println("rule: " + episodeRule + "#SUP:" + poerrule.getRuleCount() + " #CONF: "
					+ asString(poerrule.getRuleCount() / (double) poerrule.getAntiCount()));
		}
	}

	/**
	 * write the information to file
	 */
	public void writeRule2File(String filename) {
		try {
			MemoryLogger.getInstance().checkMemory();
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename, false)));
			// rankRuleBySupport();

			StringBuilder buffer = new StringBuilder();
			for (POERRule poerrule : this.ruleAppear) {

				for (Integer anti : poerrule.getAntiEpisode()) {
					buffer.append(anti);
					buffer.append(' ');
				}
				buffer.append("==> ");
				for (Integer conse : poerrule.getConseEpisode()) {
					buffer.append(conse);
					buffer.append(' ');
				}
				buffer.append("#SUP: ");
				buffer.append(poerrule.getAntiCount());
				buffer.append(" #CONF: ");
				buffer.append(asString(poerrule.getRuleCount() / (double) poerrule.getAntiCount())
						+ System.lineSeparator());
			}
			bw.write(buffer.toString());
			bw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Print statistics about the algorithm execution to System.out.
	 */
	public void printStats() {
		System.out.println("=============  POERM v.2.45 - STATS =============");
		System.out.println(" Rule count : " + ruleAppear.size());
		System.out.println(" Maximum memory usage : " + asString(maxMemory) + " mb");
		System.out.println(" Total time ~ : " + deltaTime + " ms");
		System.out.println("===================================================");
	}
}
