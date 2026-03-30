package ca.pfv.spmf.algorithms.episodes.poerm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.episodes.poerm.AlgoPOERM.myComparator;
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
 * 
 * @author Yangming Chen & Philippe Fournier-Viger
 * 
 */
public class AlgoPOERMAll {
	/** the input file */
	private String inputFile;

	/** the start time of program run */
	private long startTime;

	/** the end time of program run */
	private long endTime;

	/** the runtime of program run */
	private long deltaTime;
	
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

	public long getDeltaTime() {
		return deltaTime;
	}

	/** a datastructure to record parameter of the algorithm */
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

	/** Object to format double numbers in decimal format */
	private DecimalFormat formatter = new DecimalFormat("#.####");

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
	public AlgoPOERMAll() throws FileNotFoundException {
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

		this.miningXEventSet2();


		this.miningYEventSet();
		
//		System.out.println("x: " + this.XFreAppear.size() + " y: " + this.YFreAppear.size());
		this.findRule();
		MemoryLogger.getInstance().checkMemory();
		maxMemory = MemoryLogger.getInstance().getMaxMemory();
		this.endTime = System.currentTimeMillis();
		this.deltaTime = this.endTime - this.startTime;
		return this.ruleAppear;
//		this.printRule();
//		System.err.println("Execution time: " + this.deltaTime + " " + MemoryLogger.getInstance().getMaxMemory());
	}
	
	/**
	 * Find all XEventSet that maybe the anti episode of a Partially-Ordered Episode
	 * Rule
	 */
	public void miningXEventSet2() {
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
					// each event setF鈭獅e}
					// such that e > frequent-i itemset's lastItemand and its occurrences in the map
					// fresMap;
					for (int j = intEnd - this.parameter.getXSpan() + 1; j < intStart; ++j) {
						if (!this.XFreS.containsKey(j)) {
							continue;
						}
						List<Integer> eventSet = this.XFreS.get(j);
						for (Integer eventItem : eventSet) {
							// add each event setF鈭獅e}
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
				// Add each pair of fresMap such that |value|鈮insup into XFreAppear;
				for (Entry<Integer, List<Interval>> curAppearentry : this.thisAppear.entrySet()) {
//					System.out.println("Finded 锛�");
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
	
	
	public class myComparator2 implements Comparator<Interval> {
		public int compare(Interval a, Interval b) {
			if (a.start == b.start) {
				return a.end - b.end;
			}
			return a.start - b.start;
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
			Map<Integer, Integer> eventSet = new HashMap<Integer, Integer>();

			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(input), "UTF-8"));

			String line = null;
			int timestamp = 1;
			Integer num;
			
			int preTimestamp = -1;
			int maxWindow = this.parameter.getXSpan() + this.parameter.getYSpan() + this.parameter.getXYSpan() - 1;
			this.maxWindowNum = 0;
			
			// if self increment mode
			if (parameter.isSelfIncrement() == true) {
				while ((line = reader.readLine()) != null) {
//	                current_TID++;
					// if the line is a comment, is empty or is a
					// kind of metadata
					if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
						timestamp++;
						continue;
					}
					//System.out.println(timestamp+" " + line);
					String[] array = line.split(" ");

					List<Integer> eSet = new ArrayList<Integer>();
					List<Integer> eSet2 = new ArrayList<Integer>();
//					if (timestamp == 789) {
//						System.out.print("aa");
//					}
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

					String[] lineSplited = line.split("\\:");

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
					// each event setF鈭獅e}
					// such that e > frequent-i itemset's lastItemand and its occurrences in the map
					// fresMap;
					for (int j = intEnd - this.parameter.getXSpan() + 1; j < intStart; ++j) {
						if (!this.XFreS.containsKey(j)) {
							continue;
						}
						List<Integer> eventSet = this.XFreS.get(j);
						for (Integer eventItem : eventSet) {
							// add each event setF鈭獅e}
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
				// Add each pair of fresMap such that |value|鈮insup into XFreAppear;
				for (Entry<Integer, List<Interval>> curAppearentry : this.thisAppear.entrySet()) {
//					System.out.println("Finded 锛�");
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
		} catch (Exception e) {
			e.printStackTrace();
		} finally {

		}
	}

	/**
	 * Find all YEventSet that maybe the anti episode of a Partially-Ordered Episode
	 * Rule, similar to miningXEventSet
	 */
	public void miningYEventSet() {
		try {
			int index = 0;
			int end = this.YFreAppear.size();

			while (index < end) {
				this.thisAppear.clear();
				EventSetAppear episodeAppear = this.YFreAppear.get(index);
				index++;
				// Frequent-i item
				List<Integer> episode = episodeAppear.getEventSet();
//				System.out.println("visited " + episode);

				Integer compareKey = episode.get(episode.size() - 1);

				List<Interval> appear = episodeAppear.getIntervals();
				for (Interval interval : appear) {
					Integer intStart = interval.start;
					Integer intEnd = interval.end;
					// Search the time intervals [interval.end - YSpan + 1, interval.start) to add
					// each event setF鈭獅e}
					// such that e > frequent-i itemset's lastItemand and its occurrences in the map
					// fresMap;
					for (int j = intEnd - this.parameter.getYSpan() + 1; j < intStart; ++j) {
						if (!this.YFreS.containsKey(j)) {
							continue;
						}
						List<Integer> eventSet = this.YFreS.get(j);
						for (Integer eventItem : eventSet) {
							if (eventItem > compareKey) {
								if (this.thisAppear.containsKey(eventItem)) {
									this.thisAppear.get(eventItem).add(new Interval(j, intEnd));
								} else {
									ArrayList<Interval> appearTime = new ArrayList<Interval>();
									appearTime.add(new Interval(j, intEnd));
									this.thisAppear.put(eventItem, appearTime);
								}
							}
						}
					}
					// Search the time intervals [interval.end + 1, interval.start + YSpan)
					for (int j = intEnd + 1; j < intStart + this.parameter.getYSpan(); ++j) {
						if (!this.YFreS.containsKey(j)) {
							continue;
						}
						List<Integer> eventSet = this.YFreS.get(j);
						for (Integer eventItem : eventSet) {
							if (eventItem > compareKey) {
								List<Interval> list = this.thisAppear.get(eventItem);
								if (list != null) {
									list.add(new Interval(intStart, j));
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
						if (!this.YFreS.containsKey(j)) {
							continue;
						}
						List<Integer> eventSet = this.YFreS.get(j);
						for (Integer eventItem : eventSet) {
							if (eventItem > compareKey) {
								if (this.thisAppear.containsKey(eventItem)) {
									this.thisAppear.get(eventItem).add(new Interval(intStart, intEnd));
								} else {
									ArrayList<Interval> appearTime = new ArrayList<Interval>();
									appearTime.add(new Interval(intStart, intEnd));
									this.thisAppear.put(eventItem, appearTime);
								}
							}
						}
					}
				}
				// Add each pair of fresMap such that |value|鈮insup * minconf into XFreAppear;
				for (Entry<Integer, List<Interval>> curAppearentry : this.thisAppear.entrySet()) {
//					System.out.println("Finded 锛�");
					Integer key = curAppearentry.getKey();
					List<Interval> value = curAppearentry.getValue();
					value.sort(new myComparator());
					List<Interval> newValue = new ArrayList<Interval>();
					for (int i = 0; i < value.size(); ++i) {
						if (i == 0 || !value.get(i).equal(newValue.get(newValue.size() - 1))) {
							newValue.add(value.get(i));
						}
					}
					if (newValue.size() >= this.parameter.getMinSupport() * this.parameter.getMinConfidence()) {
						List<Integer> newKey = new ArrayList<Integer>(episode);
						newKey.add(key);
						this.YFreAppear.add(new EventSetAppear(newKey, newValue));
					}
				}
				end = this.YFreAppear.size();
				MemoryLogger.getInstance().checkMemory();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {

		}
	}

	/**
	 * try to combine all xEventSet and yEventSet to generate rules
	 */
	public void findRule() {
		System.out.println("XFreAppear: " + this.XFreAppear.size() + " ");
		System.out.println("YFreAppear: " + this.YFreAppear.size() + " ");
		//Integer anotherInteger = 225;
		for (EventSetAppear anitemset : this.XFreAppear) {
			List<Integer> anitKey = anitemset.getEventSet();
//			if (anitKey.size() == 1 && anitKey.get(0).compareTo(anotherInteger) == 0) {
//				System.out.println(anitKey.get(0));
//			}else {
//				continue;
//			}
			List<Interval> anitValues = anitemset.getIntervals();
//		    System.out.println("scan X " + anitKey);
			int anitStart = 0;
			int anitCount = 0;
			
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

			for (EventSetAppear conseset : this.YFreAppear) {
				int start = 0;
				int j = 0;
				List<Integer> conseKey = conseset.getEventSet();
//				if (anitKey.size() == 1 && anitKey.get(0).compareTo(anotherInteger) == 0 && conseKey.size() == 1 && conseKey.get(0).compareTo(anotherInteger) == 0) {
//					System.out.println(anitKey.get(0));
//				}else {
//					continue;
//				}
				if (anitKey.size() > 1 && conseKey.get(0) == 3) {
					System.out.println(" aaa ");
				}
				List<Interval> conseValue = conseset.getIntervals();

//				if (anitKey.equals(conseKey)) {
//					continue;
//				}
				List<Interval> intervalList = new ArrayList<Interval>();
				int anitIndex = 0;
				for (Interval anitValue : anitValues) {
					if (anitValue.start <= start) {
						continue;
					}
					while (j < conseValue.size() && conseValue.get(j).end <= anitValue.end) {
						j++;
					}
					for (int k = j; k < conseValue.size(); ++k) {
						if (conseValue.get(k).end - this.parameter.getYSpan() + 1 - this.parameter.getXYSpan() > anitValue.end) {
			    			break;
			    		}
			    		if (conseValue.get(k).start <= anitValue.end || conseValue.get(k).start > anitValue.end + this.parameter.getXYSpan()) {
			    			continue;
			    		}
//			    		count++;
						intervalList.add(anitValue);
						intervalList.add(conseValue.get(k));
						intervalList.add(new Interval(0, 0));
//						if (intervalList.size() < 200) {
//							System.out.println(anitValue.start + " " + conseValue.get(k).end + "end");
//						}
						start = conseValue.get(k).end;
						break;
					}
				}
				int confidence = intervalList.size() / 3;
//			    String. 
//				System.out.println("key " + anitKey + "==>" + conseKey + " " + confidence + " / " + anitCount);
				MemoryLogger.getInstance().checkMemory();
				if (confidence >= anitCount * this.parameter.getMinConfidence()) {
					
//			    	System.out.println("key " + anitKey + "==>" + conseKey + " " + confidence + " / " + anitCount);
					ruleAppear.add(new POERRule(anitKey, conseKey, null, anitCount, confidence));
				}
			}
		}
	}

	/**
	 * write the information to file
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
			System.out.println("rule: " + episodeRule + "#SUP: " + poerrule.getRuleCount() + " #CONF: "
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
		System.out.println("=============  POERM-ALL v.2.45 - STATS =============");
		System.out.println(" Rule count : " + ruleAppear.size());
		System.out.println(" Maximum memory usage : " + asString(maxMemory) + " mb");
		System.out.println(" Total time ~ : " + deltaTime + " ms");
		System.out.println("===================================================");
	}
}
