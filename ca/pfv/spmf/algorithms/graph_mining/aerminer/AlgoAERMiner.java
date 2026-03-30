package ca.pfv.spmf.algorithms.graph_mining.aerminer;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ca.pfv.spmf.algorithms.graph_mining.tseqminer.ParametersSetting;
import ca.pfv.spmf.tools.MemoryLogger;

/* This file is copyright (c) 2020 by Ganghuan He
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
 * The AER-Miner algorithm as described in this paper: <br/>
 * <br/>
 * Fournier-Viger, P., He, G., Lin, J. C.-W., Gomes, H. M. (2019). Mining
 * Attribute Evolution Rules in Dynamic Attributed Graphs. Proc. 22nd Intern.
 * Conf. on Data Warehousing and Knowledge Discovery (DAWAK 2020), Springer, pp.
 * 167-182. [ppt] <br/>
 * <br/>
 * 
 * @author Ganghuan He 2020
 */
public class AlgoAERMiner {

	/** after processing:trend dynamic attributed graph */
	private Map<Integer, AttributedGraph> trendDyAg;

	/** min support */
	// static double minSup = ParametersSetting.MINSUP;
	private int minSupRelative = Integer.MAX_VALUE;
	
	/** min lift: a measure for rule */
	// static double minLift = ParametersSetting.MINLIFT;
	private double minConf = ParametersSettingAERMiner.MINCONF;

	/** expect confidence when the variation of an attribute is already decided */
	private  Map<Integer, Map<Integer, Double>> expectConBasedAttr = new HashMap<>();
 
	/** expect probability */
	private Map<Integer, Double> expectConfience = new HashMap<>();
	
	/** map from event type(integer) -> enent type name(String) */
	private Map<Integer, String> eventTypeMapping = new LinkedHashMap<>();
	
	/** attribute to support points */
	private Map<Integer, SupportPoints> attrPointSet = new HashMap<>();

	/** store core patern condidence, for filtering patterns in the end */
	private Map<CorePattern, Double> patterntoConfidence = new HashMap<>();
	
	/** store Core patterns and its instaces */
	private List<Map<CorePattern, Set<Instance>>> patterns;

	private List<List<CorePattern>> merge = new ArrayList<>();

	/** number of patterns found */
	private int patternCount = 0;

	/** total runtime */
	private long totalMiningTime = 0;

//    public static void main(String[] args) throws IOException {
////        ParametersSetting.MINSUP = Double.parseDouble(args[0]);
////        ParametersSetting.MINCONF = Double.parseDouble(args[1]);
////        ParametersSetting.MINLIFT = Double.parseDouble(args[2]);
//        runAlgorithm();
////         experiment 1, comparision between real and synthetic noisy data
////        experiment1("DBLP");
////        experiment1("US Flight");
//
//        // experiment 2, different Attribute Number
////        experiment2("DBLP");
////        experiment2("US Flight");
//
//
//        // experiment 3, different length of dynamic graph sequence
////        experiment3("DBLP");
////        experiment3("US Flight");
//
//
//        // experiment 4, different size of graph
////        experiment4("DBLP");
////        experiment4("US Flight");
//
//        //  experiment 5, parameter: minsup
////        experiment5("DBLP");
////        experiment5("US Flight");
//
//
//        // experiment 6, parameter: minlift
////        experiment6("DBLP");
////        experiment6("US Flight");
//
//        // print some results
////        System.out.println(patterns.get(0).keySet());
////        //System.out.println(patterns.get(0).keySet());
////        System.out.println(patterns.get(1).keySet());
////        System.out.println(patterns.get(2).keySet());
////        System.out.println(merge);
////        List<Map.Entry<CorePattern,Double>>list = patterntoConfidence.entrySet().stream()
////                .sorted((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()))
////                .collect(Collectors.toList());
////
////        System.out.println(list);
//
////        for (CorePattern p : patterns.get(2).keySet()){
////            System.out.println(p +" " +patterns.get(2).get(p).size());
////        }
//        //}
//
//    }

//	private void experiment6(String dataName) throws IOException {
//		double[] minLift;
//		if (dataName.equals("DBLP")) {
//			System.out.println("Real DBLP on experiment 6 ");
//			ParametersSettingAERMiner.switchData(0);
//			minLift = new double[] { 1.05, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0, 3, 4, 5, 10 };
//
//			ParametersSettingAERMiner.MINSUP = 0.04;
//			ParametersSettingAERMiner.REPEAT = 10;
//			ParametersSettingAERMiner.MINCONF = 0.2;
//		} else {
//			System.out.println("Real US Flight on experiment 6 ");
//
//			ParametersSettingAERMiner.switchData(1);
//			minLift = new double[] { 1.05, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0, 3, 4, 5, 10 };
//
//			ParametersSettingAERMiner.MINSUP = 0.04;
//			ParametersSettingAERMiner.REPEAT = 10;
//			ParametersSettingAERMiner.MINCONF = 0.2;
//		}
//
//		for (double v : minLift) {
//			ParametersSettingAERMiner.MINLIFT = v;
//			System.out.println("MINLIFT :" + ParametersSettingAERMiner.MINLIFT);
//			System.out.println("\nstart to run AER miner algorithm...\n");
//			System.gc();
//			MemoryLogger.getInstance().reset();
//			long t1 = System.currentTimeMillis();
//
//			runAlgorithm();
//			// System.out.println(expectConBasedAttr.size());
//			long t2 = System.currentTimeMillis();
//			System.out.println("running time:" + (t2 - t1) / 1000 + "s");
//			System.out.println("MAX MEMORY:" + MemoryLogger.getInstance().getMaxMemory());
//			System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
//		}
//
//	}
//
//	private void experiment5(String dataName) throws IOException {
//		double[] minSup;
//		if (dataName.equals("DBLP")) {
//			System.out.println("Real DBLP on experiment 5 ");
//			ParametersSettingAERMiner.switchData(0);
//			minSup = new double[] { 0.001, 0.002, 0.004, 0.008, 0.016, 0.02, 0.04, 0.08, 0.16, 0.2, 0.3, 0.4, 0.5 };
//
//			ParametersSettingAERMiner.MINLIFT = 1.3;
//			ParametersSettingAERMiner.REPEAT = 10;
//			ParametersSettingAERMiner.MINCONF = 0.2;
//		} else {
//			System.out.println("Real US Flight on experiment 5");
//
//			ParametersSettingAERMiner.switchData(1);
//			minSup = new double[] { 0.001, 0.002, 0.004, 0.008, 0.016, 0.02, 0.04, 0.08, 0.16, 0.2, 0.3, 0.4, 0.5 };
//
//			ParametersSettingAERMiner.MINLIFT = 1.3;
//			ParametersSettingAERMiner.REPEAT = 50;
//			ParametersSettingAERMiner.MINCONF = 0.2;
//		}
//
//		for (double v : minSup) {
//			ParametersSettingAERMiner.MINSUP = v;
//			System.out.println("MINSUP :" + ParametersSettingAERMiner.MINSUP);
//			System.out.println("\nstart to run AER miner algorithm...\n");
//			System.gc();
//			MemoryLogger.getInstance().reset();
//			long t1 = System.currentTimeMillis();
//
//			runAlgorithm();
//			// System.out.println(expectConBasedAttr.size());
//			long t2 = System.currentTimeMillis();
//			System.out.println("running time:" + (t2 - t1) / 1000 + "s");
//			System.out.println("MAX MEMORY:" + MemoryLogger.getInstance().getMaxMemory());
//			System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
//		}
//	}
//
//	private void experiment4(String dataName) throws IOException {
//		int[] graphSize;
//		if (dataName.equals("DBLP")) {
//			System.out.println("Real DBLP on experiment 4 ");
//			ParametersSettingAERMiner.switchData(0);
//			ParametersSettingAERMiner.MINLIFT = 1.3;
//			graphSize = new int[] { 1, 5, 10, 15, 20, 25, 30, 50 };
//			ParametersSettingAERMiner.REPEAT = 2;
//		} else {
//			System.out.println("Real US Flight on experiment 4");
//			graphSize = new int[] { 1, 5, 10, 15, 20, 25, 30, 50, 100 };
//			ParametersSettingAERMiner.switchData(1);
//			ParametersSettingAERMiner.MINLIFT = 1.3;
//			ParametersSettingAERMiner.REPEAT = 10;
//		}
//
//		for (int i = 0; i < graphSize.length; i++) {
//			ParametersSettingAERMiner.ATTR_FILE_PATH = ParametersSettingAERMiner.OLD_ATTR_FILE_PATH
//					+ ("_" + graphSize[i] + ".txt");
//			ParametersSettingAERMiner.EDGE_FILE_PATH = ParametersSettingAERMiner.OLD_EDGE_FILE_PATH
//					+ ("_" + graphSize[i] + ".txt");
//			ParametersSettingAERMiner.VERTEX_MAP_NAME_PATH = ParametersSettingAERMiner.OLD_VERTEX_MAP_NAME_PATH
//					+ ("_" + graphSize[i] + ".txt");
//			System.out.println("graph size :" + graphSize[i] * ParametersSettingAERMiner.VERTEXNUM);
//			System.out.println("\nstart to run AER miner algorithm...\n");
//			System.gc();
//			MemoryLogger.getInstance().reset();
//			long t1 = System.currentTimeMillis();
//
//			runAlgorithm();
//			// System.out.println(expectConBasedAttr.size());
//			long t2 = System.currentTimeMillis();
//			System.out.println("running time:" + (t2 - t1) / 1000 + "s");
//			System.out.println("MAX MEMORY:" + MemoryLogger.getInstance().getMaxMemory());
//			System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
//		}
//	}
//
//	private void experiment3(String dataName) throws IOException {
//		int[] repeats;
//		if (dataName.equals("DBLP")) {
//			System.out.println("Real DBLP on experiment 3 ");
//			repeats = new int[] { 5, 10, 15, 20, 25, 50, 100 };
//			ParametersSettingAERMiner.switchData(0);
//			ParametersSettingAERMiner.MINLIFT = 1.3;
//		} else {
//			System.out.println("Real US Flight on experiment 3");
//			repeats = new int[] { 1, 5, 10, 15, 20, 25, 50, 100, 150, 250 };
//			ParametersSettingAERMiner.switchData(1);
//			ParametersSettingAERMiner.MINLIFT = 1.3;
//		}
//
//		for (int i = 0; i < repeats.length; i++) {
//			ParametersSettingAERMiner.REPEAT = repeats[i];
//			System.out.println("Time sequence length  " + ParametersSettingAERMiner.REPEAT);
//			System.out.println("address: " + ParametersSettingAERMiner.EDGE_FILE_PATH);
//			System.out.println("\nstart to run AER miner algorithm...\n");
//			System.gc();
//			MemoryLogger.getInstance().reset();
//			long t1 = System.currentTimeMillis();
//			runAlgorithm();
//			// System.out.println(expectConBasedAttr.size());
//			long t2 = System.currentTimeMillis();
//			System.out.println("running time:" + (t2 - t1) / 1000 + "s");
//			System.out.println("MAX MEMORY:" + MemoryLogger.getInstance().getMaxMemory());
//			System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
//			System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
//			reset();
//		}
//
//	}
//
//	private void experiment2(String dataName) throws IOException {
//		int[] attributeNum;
//		System.out.println("EXPERIMENT 2: " + " ATTRIBUTE NUM");
//		if (dataName.equals("DBLP")) {
//			System.out.println("Real DBLP data experiment 2 ");
//			attributeNum = new int[] { 2, 5, 10, 15, 20, 25, 30, 35, 40, 43 };
//			ParametersSettingAERMiner.switchData(0);
//			ParametersSettingAERMiner.MINLIFT = 1.3;
//			ParametersSettingAERMiner.REPEAT = 10;
//		} else {
//			System.out.println("Real US Flight data experiment");
//			attributeNum = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 };
//			ParametersSettingAERMiner.switchData(1);
//			ParametersSettingAERMiner.MINLIFT = 1.3;
//			ParametersSettingAERMiner.REPEAT = 100;
//		}
//
//		for (int i = 0; i < attributeNum.length; i++) {
//			ParametersSettingAERMiner.TOTAL_NUM_ATTR = attributeNum[i];
//			System.out.println("Attribute Num " + ParametersSettingAERMiner.TOTAL_NUM_ATTR);
//			System.out.println("address: " + ParametersSettingAERMiner.EDGE_FILE_PATH);
//			System.out.println("\nstart to run AER miner algorithm...\n");
//			System.gc();
//			MemoryLogger.getInstance().reset();
//			long t1 = System.currentTimeMillis();
//			runAlgorithm();
//			// System.out.println(expectConBasedAttr.size());
//			long t2 = System.currentTimeMillis();
//			System.out.println("running time:" + (t2 - t1) / 1000 + "s");
//			System.out.println("MAX MEMORY:" + MemoryLogger.getInstance().getMaxMemory());
//			System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
//			System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
//			reset();
//		}
//
//	}
//
//	public void experiment1(String dataName) throws IOException {
//		// vary parameter lift
//		double[] minLiftRepeats = new double[] { 1.05, 1.1, 1.2, 1.3, 1.4, 1.5, 1.8, 2.2, 2.6, 3, 4, 5 };
//
//		// comparision between real and synthetic data
//		for (int i = 0; i < minLiftRepeats.length * 2; i++) {
//			if (i % 2 == 0) {
//
//				if (dataName.equals("DBLP")) {
//					System.out.println("Real DBLP data experiment");
//					ParametersSettingAERMiner.switchData(0);
//				}
//
//				else {
//					System.out.println("Real US Flight data experiment");
//					ParametersSettingAERMiner.switchData(1);
//				}
//			} else {
//				if (dataName.equals("DBLP")) {
//					System.out.println("Synthetic DBLP data experiment");
//					ParametersSettingAERMiner.switchData(2);
//				}
//
//				else {
//					System.out.println("Synthetic USFlight data experiment");
//					ParametersSettingAERMiner.switchData(3);
//				}
//			}
//
//			ParametersSettingAERMiner.MINLIFT = minLiftRepeats[i / 2];
//			System.out.println("minlift = " + ParametersSettingAERMiner.MINLIFT);
//			System.out.println("address: " + ParametersSettingAERMiner.EDGE_FILE_PATH);
//			System.out.println("\nstart to run AER miner algorithm...\n");
//			System.gc();
//			MemoryLogger.getInstance().reset();
//			long t1 = System.currentTimeMillis();
//			runAlgorithm();
//			// System.out.println(expectConBasedAttr.size());
//			long t2 = System.currentTimeMillis();
//			System.out.println("running time:" + (t2 - t1) / 1000 + "s");
//			System.out.println("MAX MEMORY:" + MemoryLogger.getInstance().getMaxMemory());
//			System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
//			reset();
//		}
//	}

	/**
	 * the whole Algorithm
	 * 
	 * @throws IOException from preprocessing class
	 */
	public void runAlgorithm(String inputDirectory, String outputPath, double minsup, double minconf, double minlift)
			throws IOException {

		ParametersSettingAERMiner.EDGE_FILE_PATH = inputDirectory + "graph.txt";
		ParametersSettingAERMiner.ATTRI_MAPPING_PATH = inputDirectory + "attributes_mapping.txt";
		ParametersSettingAERMiner.ATTR_FILE_PATH = inputDirectory + "attributes.txt";
		ParametersSettingAERMiner.VERTEX_MAP_NAME_PATH = inputDirectory + "vertices_mapping.txt";

		ParametersSettingAERMiner.MINSUP = minsup;
		ParametersSettingAERMiner.MINCONF = minconf;
		ParametersSettingAERMiner.MINLIFT = minlift;

		ParametersSettingAERMiner.PATTERN_PATH = outputPath;

		// record the start time
		totalMiningTime = System.currentTimeMillis();

		// ====== Reset variables ==========

		trendDyAg = null;
		/** expect confidence when the variation of an attribute is already decided */
		expectConBasedAttr = new HashMap<>();

		// static Map<Integer,SupportPoints> attrPointSet = new HashMap<>();
		/** expect probability */
		expectConfience = new HashMap<>();
		/** map from event type(integer) -> enent type name(String) */
		eventTypeMapping = new LinkedHashMap<>();
		/** attribute to support points */
		attrPointSet = new HashMap<>();

		/** store core patern condidence, for filtering patterns in the end */
		patterntoConfidence = new HashMap<>();
		/** store Core patterns and its instaces */
		patterns = null;

		merge = new ArrayList<>();

		// =================

		// read ori graph and precessing(convert it to trend graph)
		trendDyAg = Preprocess.convertToTrendGraph();

		// Map trend attribute to integer (Integer:String)
		eventTypeMapping = Preprocess.findEventTypeMapping();
		// get the support points set for every trend attributed
		getAttrSupPointSet();

		// calculate expect confidence
		calExpectConfidence();

//        System.out.println(expectConfience.get(40));
		System.out.println("Start to seach pattern by bfs");
		bfsSearch();
		System.out.println("seach pattern by bfs end!");

		MemoryLogger.getInstance().checkMemory();
		//
		filterCorePattern();

		// merge
		mergeCorePatternSameChild();
		mergeCorePatternSamePattern();
		MemoryLogger.getInstance().checkMemory();
		System.out.println(eventTypeMapping);

		// save
		writePatternOnFile();
		// mergeCorePatternSameChild();

		// record the end time
		totalMiningTime = System.currentTimeMillis() - totalMiningTime;
	}

	/**
	 * this method is used to generate patterns and its instances by bfs,level by
	 * level result is saved in the class variation patterns
	 */
	private void bfsSearch() {
		patterns = new ArrayList<>();
		// generateAllCoreVertex();
		patterns.add(extendCorePattern(generateAllCoreVertex()));
		// generate k pattern from k-1 pattern BFS
		for (int i = 0; i < 2; i++) {
			if (patterns.get(patterns.size() - 1) == null)
				break;
			patterns.add(extendCorePattern(patterns.get(patterns.size() - 1)));
			System.out.println("size " + (i + 3) + " patterns complete!");
		}

		// while |k| > 0
//        Map<CorePattern, Set<Instance>> corePatternSetMap = extendCorePattern(generateAllCoreVertex());
//        while (corePatternSetMap.size() != 0){
//            patterns.add(corePatternSetMap);
//            corePatternSetMap = extendCorePattern(corePatternSetMap);
//        }
		MemoryLogger.getInstance().checkMemory();

	}

	/**
	 * This method allow parent nodes have multiply attributes merge when their
	 * child attribute is the same.
	 */
	public void mergeCorePatternSameChild() {

		for (int size = 0; size < patterns.size(); size++) {
			Map<CorePattern, Set<Instance>> sizeiPatterns = patterns.get(size);
			List<CorePattern> sortpattern = new ArrayList<>(sizeiPatterns.keySet());
			sortpattern.sort(Comparator.comparingInt(CorePattern::getChildAttr));
			int[] visted = new int[sortpattern.size()];

			for (int i = 0; i < sortpattern.size(); i++) {
				if (visted[i] == 1)
					continue;
				for (int j = i + 1; j < sortpattern.size(); j++) {
					if (visted[j] == 1)
						continue;
					CorePattern p1 = sortpattern.get(i);
					CorePattern p2 = sortpattern.get(j);
					if (p1.getChildAttr() != p2.getChildAttr())
						break;
					Set<Instance> instances1 = new HashSet<>(sizeiPatterns.get(p1));
					// System.out.println(instances1);

					Set<Instance> instances2 = new HashSet<>(sizeiPatterns.get(p2));
					// System.out.println(instances2);

					Set<Instance> joinSet = new HashSet<>();
					joinSet.addAll(instances1);
					joinSet.retainAll(instances2);

//                    System.out.println(instances1.size()+" "+instances2.size());
//                    System.out.println(joinSet.size());
					if (joinSet.size() > minSupRelative) {
						// System.out.println(instances1.size()+":"+instances2.size());
						List<CorePattern> mergePari = new ArrayList<>();
						mergePari.add(p1);
						mergePari.add(p2);
						merge.add(mergePari);
						// System.out.println("merge Pattern" + p1+"and"+p2+"supp"+joinSet.size());
						visted[i] = 1;
						visted[j] = 1;
					}
				}
			}
		}

		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * This method allow child node has multiply attributes merge when their parent
	 * attribute are the same.
	 */
	public void mergeCorePatternSamePattern() {

		for (int size = 0; size < patterns.size(); size++) {
			Map<CorePattern, Set<Instance>> sizeiPatterns = patterns.get(size);
			List<CorePattern> keys = new ArrayList<>(sizeiPatterns.keySet());
			int[] visted = new int[keys.size()];
			for (int i = 0; i < keys.size(); i++) {
				if (visted[i] == 1)
					continue;
				for (int j = i + 1; j < keys.size(); j++) {
					CorePattern p1 = keys.get(i);
					CorePattern p2 = keys.get(j);
					List<Integer> list = new ArrayList<>();
					list.addAll(p1.getParentAttr());
					list.retainAll(p2.getParentAttr());
					if (list.size() != size + 1 || visted[j] == 1)
						break;
					Set<Instance> instances1 = new HashSet<>(sizeiPatterns.get(p1));
					// System.out.println(instances1);

					Set<Instance> instances2 = new HashSet<>(sizeiPatterns.get(p2));
					// System.out.println(instances2);

					Set<Instance> joinSet = new HashSet<>();
					joinSet.addAll(instances1);
					joinSet.retainAll(instances2);

//
//                    System.out.println(instances1.size()+" "+instances2.size());
//                    System.out.println(joinSet.size());
					if (joinSet.size() > minSupRelative) {
						// System.out.println(instances1.size()+":"+instances2.size());
						List<CorePattern> mergePari = new ArrayList<>();
						mergePari.add(p1);
						mergePari.add(p2);
						merge.add(mergePari);
						// System.out.println("merge Pattern" + p1+"and"+p2+"supp"+joinSet.size());
						visted[i] = 1;
						visted[j] = 1;
					}

				}
			}
		}
		MemoryLogger.getInstance().checkMemory();
		// to do
	}

	/**
	 * filter patterns using minConf and when size k minus last attribute == size k
	 * -1 pattern
	 */
	public void filterCorePattern() {
		MemoryLogger.getInstance().checkMemory();
		// List<Map<CorePattern, Set<Instance>>> patterns = bfsSearch();
		for (int i = 0; i < patterns.size(); i++) {
			Iterator<CorePattern> it = patterns.get(i).keySet().iterator();
			while (it.hasNext()) {
				CorePattern p = it.next();
				if (i > 0) {
					int attr = p.getLastAttr();
					p.deleteLastAttr();
					patterns.get((i - 1)).remove(p);
					patterntoConfidence.remove(p);
					p.growParentAttr(attr);
				}
				if (patterntoConfidence.get(p) < minConf) {
					it.remove();
					patterntoConfidence.remove(p);
				}

			}
		}
		for (int i = 0; i < patterns.size(); i++) {
			System.out.println("size " + (i + 2) + "pattern number" + " : " + patterns.get(i).size());
		}
	}

	/**
	 * m initialize one attribute pattern, and a set of instance for each pattern
	 * 
	 * @return the map of single attribute to its instaces
	 */
	public Map<CorePattern, Set<Instance>> generateAllCoreVertex() {

		Map<CorePattern, Set<Instance>> patterns = new HashMap<>();
		// for each trend attribute
		for (Entry<Integer, SupportPoints> entry : attrPointSet.entrySet()) {
			Integer attr = entry.getKey();
			// except attribute maintaining no change(=)
			if (attr % 3 != 2) {
				SupportPoints sups = attrPointSet.get(attr);
				// initialization of a pattern,start from child attribute(resulted attribute)*/
				CorePattern p = new CorePattern(attr);
				Set<Instance> isos = new HashSet<>();
				// each pattern to its instance,based on minimum image */
				for (int timestamp : sups.getSupportPoints().keySet()) {
					// find them in attribute to support points directly,not in the trend graph*/
					for (int vId : sups.getSupportPoints().get(timestamp)) {
						isos.add(new Instance(new SupportPoint(timestamp, vId)));
					}
				}
				patterns.put(p, isos);

//                System.out.println(p + ":" + isos);
			}
		}

		return patterns;
	}

	/**
	 * the method is used for extend 1 attribute for k-1 patterns,
	 * 
	 * @param patterns: k-1 size
	 * @return new patterns: k size
	 */
	public Map<CorePattern, Set<Instance>> extendCorePattern(Map<CorePattern, Set<Instance>> patterns) {
		Map<CorePattern, Set<Instance>> newPatterns = new HashMap<>();
		// for each pattern from last iteration
		for (Entry<CorePattern, Set<Instance>> entry : patterns.entrySet()) {
			CorePattern pattern = entry.getKey();

			// attributes to be add in the pattern, generate and test is used in here
			for (Entry<Integer, SupportPoints> entry2 : attrPointSet.entrySet()) {
				Integer attr = entry2.getKey();

				// grow pattern use attributed increasing, make no repeat patterns appear
				int lastAttr = pattern.getLastAttr();
				if (attr % 3 != 2 && lastAttr <= attr) {
					// System.out.println(attr);
					// @@@@@@@ key part @@@@@@@
					// new pattern is generated from old pattern grow one parent attribute
					CorePattern newPattern = pattern.clone();
					newPattern.growParentAttr(attr);

					// find new instance in the trend DAG
					Set<Instance> newInstace = mapGrowth(attr, patterns.get(pattern));
					// System.out.println(patterns.get(pattern).size());
					// System.out.println(newInstace.size());
					int support = newInstace.size();
					if (support <= minSupRelative)
						continue;
					// calulate lift for new patterns
					double[] confidenceAndLift = calNewPatternLift(attr, pattern, patterns.get(pattern));
					// double[] confidenceAndLift = calConfidenceLift(newPattern,newInstace);
					double confidence = confidenceAndLift[0];
					double lift = confidenceAndLift[1];
					// System.out.println(confidence+" "+lift);
					// && confidence>minConfidence,we use it latter
					// filter no interesting pattern by support and lift
					if (lift > ParametersSettingAERMiner.MINLIFT) {
						newPatterns.put(newPattern, newInstace);
						patterntoConfidence.put(newPattern, confidence);
						// System.out.println("support="+support+"confidence="+confidence+",lift="+lift);
//                        System.out.println(newPattern);
					}

				}
			}
		}
		System.out.println("candidate num:" + newPatterns.size());
		MemoryLogger.getInstance().checkMemory();
		return newPatterns;
	}

	/**
	 * this method is used for calculating the measure of
	 * rule:lift(confidence/expected confidence) lift > 1 means the rule has impact,
	 * the lager the value, more better
	 * 
	 * @param attr      attribute to be extend on the old pattern
	 * @param pattern   old pattern
	 * @param instances instances for k-1 size patterns
	 * @return confidence and lift
	 */
	private double[] calNewPatternLift(int attr, CorePattern pattern, Set<Instance> instances) {
		double[] result = new double[2];
		int count = 0;
		int sum = 0;
		for (Instance instance : instances) {

			SupportPoint sp = instance.getChildPoint();
			int vId = sp.getvId(), timeStamp = sp.getTimestamp() - 1;
			if (timeStamp < 0)
				continue;

			// we do not grow attribute on the same node in this procedure
			List<Integer> used = instance.getParentVIds();
			// System.out.println(used);
			Set<Integer> neighbors = trendDyAg.get(timeStamp).getNeighbors(vId);
			// System.out.println(neighbors);
			neighbors.removeAll(used);

			for (int id : neighbors) {
				sum++;
				if (trendDyAg.get(timeStamp).getVertex(id) == null)
					continue;
				if (trendDyAg.get(timeStamp).getVertex(id).getAttrDouMap().containsKey(attr)) {
					count++;
				}
			}
		}
		// Bayes formula：Posterior probability
		double confidence = (double) count / sum;
		double expectCon;
		double lift;

		// two kind of expect confidence, when size is equal 1, its confidence is
		// probability
		// size > 1: the expect confidence is based on child attribute
		if (pattern.getSize() == 1) {
			expectCon = expectConfience.get(attr);
			lift = confidence / expectCon;
			expectConBasedAttr.computeIfAbsent(pattern.getChildAttr(), k -> new HashMap<>());
			expectConBasedAttr.get(pattern.getChildAttr()).put(attr, confidence);

		} else {
			// if(expectConBasedAttr.get(pattern.getChildAttr()) != null) {
			expectCon = expectConBasedAttr.get(pattern.getChildAttr()).get(attr);
			lift = confidence / expectCon;
			// }

		}
		result[0] = confidence;
		result[1] = lift;
		// System.out.println(confidence+":"+expectConfience.get(attr)+":" + lift);
		return result;
	}

//	private static double[] calConfidenceLift(CorePattern newPattern, Set<Instance> newInstances) {
//		int childAttr = newPattern.getChildAttr();
//		int count = 0;
//		int sum = 0;
//		for (Instance instance : newInstances) {
//			SupportPoint child = instance.getChildPoint();
//			int timeStamp = child.getTimestamp() - 1;
//			Set<Integer> commonNodeId = new HashSet<>();
//			commonNodeId.addAll(trendDyAg.get(timeStamp).getEdgesMap().keySet());
////            for (int id:trendDyAg.get(timeStamp).getAllVerticeId()){
////                commonNodeId.add(id);
////            }
//			for (int i = 0; i < instance.getParentVIds().size(); i++) {
//				Set<Integer> neis = trendDyAg.get(timeStamp).getNeighbors(instance.getParentVIds().get(i));
//				commonNodeId.retainAll(neis);
//
//			}
//			// System.out.println(commonNodeId.size());
//			sum += commonNodeId.size();
//			for (int id : commonNodeId) {
//				if (trendDyAg.get(timeStamp + 1).getVertex(id).getAttrDouMap().containsKey(childAttr)) {
//					count++;
//				}
//			}
//		}
//		if (sum == 0)
//			System.out.println(newPattern + ":" + newInstances);
//		double confidence = (double) count / sum;
//		double expectCon = expectConfience.get(childAttr);
//		double lift = confidence / expectCon;
//		// System.out.println(count +" "+sum);
//		// System.out.println(confidence+" "+lift);
//		return new double[] { confidence, lift };
//	}

	/**
	 * instance grow for new pattern
	 * 
	 * @param attr      attribute to be extend on the old pattern
	 * @param instances old instance for k-1 pattern
	 * @return new instance for k pattern, match one extra node for old instance
	 */
	private Set<Instance> mapGrowth(int attr, Set<Instance> instances) {
		Set<Instance> newInstaces = new HashSet<>();

		for (Instance instance : instances) {
			SupportPoint sp = instance.getChildPoint();
			int vId = sp.getvId(), timeStamp = sp.getTimestamp() - 1;
			if (timeStamp < 0)
				continue;

			List<Integer> used = instance.getParentVIds();
			// System.out.println(used);
			Set<Integer> neighbors = trendDyAg.get(timeStamp).getNeighbors(vId);
			// System.out.println(neighbors);
			neighbors.removeAll(used);
			MemoryLogger.getInstance().checkMemory();
			// System.out.println(neighbors);
			// minimum image
			for (int id : neighbors) {
				if (trendDyAg.get(timeStamp).getVertex(id) == null)
					continue;
				if (trendDyAg.get(timeStamp).getVertex(id).getAttrDouMap().containsKey(attr)) {

					Instance newInstance = instance.clone();
					newInstance.growParent(id);
					newInstaces.add(newInstance);
					break;
				}
			}
			MemoryLogger.getInstance().checkMemory();

		}
		MemoryLogger.getInstance().checkMemory();

		return newInstaces;
	}

	/**
	 * find all support point for each trend attribute,
	 */
	public void getAttrSupPointSet() {
		attrPointSet = new HashMap<>();
		int[] attCount = new int[ParametersSettingAERMiner.TOTAL_NUM_ATTR * 3 + 1];

		// for each attributed graph
		for (int i = 0; i < trendDyAg.size(); i++) {
			AttributedGraph trendAg = trendDyAg.get(i);
			// System.out.println(trendAg.getVerNum());
			// for each vertex
			for (int vId : trendAg.getAllVerticeId()) {
				Vertex v = trendAg.getVertex(vId);
//				vertexCount++;
				// for each attribute
				for (int attribute : v.getAttrDouMap().keySet()) {
					attCount[attribute]++;
					SupportPoints sp = attrPointSet.get(attribute);
					if (sp == null) {
						sp = new SupportPoints();
						attrPointSet.put(attribute, sp);
					}
					sp.addPoint(i, vId);
				}
			}
		}
		// System.out.println(attCount[40]);
		// System.out.println(attrPointSet.size());

		// for test
//		int num = 0;
//		for (int value : attCount) {
//			// System.out.printf(i+":" + attCount[i]+" ");
//			num += value;
//		}
//        System.out.println("\n"+vertexCount+"   " +num);
		minSupRelative = (int) (trendDyAg.size() * trendDyAg.get(0).getVerNum() * ParametersSettingAERMiner.MINSUP);
		System.out.println("minSupRelative" + ":" + minSupRelative);
//        for (int i :attrPointSet.keySet()){
//            System.out.println(i+":"+attrPointSet.get(i).toString());
//        }
	}

	// calculate expect confidence for each trend attribute
	private void calExpectConfidence() {
		for (Entry<Integer, SupportPoints> entry : attrPointSet.entrySet()) {
			Integer attr = entry.getKey();
			expectConfience.put(attr,
					(double) attrPointSet.get(attr).getSize() / (trendDyAg.size() * trendDyAg.get(0).getVerNum()));
		}
		System.out.println(expectConfience);
	}

//	private static void reset() {
//		trendDyAg = null;
//		/** expect confidence when the variation of an attribute is already decided */
//		expectConBasedAttr = new HashMap<>();
//
//		// static Map<Integer,SupportPoints> attrPointSet = new HashMap<>();
//		/** expect probability */
//		expectConfience = new HashMap<>();
//		/** map from event type(integer) -> enent type name(String) */
//		eventTypeMapping = new LinkedHashMap<>();
//		/** attribute to support points */
//		attrPointSet = new HashMap<>();
//
//		/** store core patern condidence, for filtering patterns in the end */
//		patterntoConfidence = new HashMap<>();
//		/** store Core patterns and its instaces */
//		patterns = null;
//
//		merge = new ArrayList<>();
//	}

	/**
	 * write result on files
	 */
	public void writePatternOnFile() throws IOException {
//		if (ParametersSettingAERMiner.TASK_FLAG < 0 || ParametersSettingAERMiner.TASK_FLAG > 1)
//			return;
		FileWriter fileWriter = new FileWriter(ParametersSettingAERMiner.PATTERN_PATH);
		// to do
		for (int i = 0; i < patterns.size(); i++) {
			fileWriter.write("size " + (i + 2) + "pattern," + "count: " + patterns.get(i).size() + "\n");
			for (Entry<CorePattern, Set<Instance>> entry : patterns.get(i).entrySet()) {
				CorePattern corePattern = entry.getKey();
				fileWriter.write(corePattern.toString() + " #SUP: " + patterns.get(i).get(corePattern).size()
						+ " #CONF: " + patterntoConfidence.get(corePattern) + "\n");
			}
			patternCount += patterns.get(i).size();
		}

		fileWriter.close();
	}

	public void printStats() {
		System.out.println("=============  AERMiner v2.44 - STATS =============");
		System.out.println(" Time to prepare the data: " + ParametersSetting.PREPARE + " ms");
		System.out.println(" Time to mine patterns from data: " + totalMiningTime + " ms");
		System.out.println(" Maximum memory usage : " + MemoryLogger.getInstance().getMaxMemory() + " MB");
		System.out.println(" Pattern count: " + patternCount);
		System.out.println("====================================================");
	}

}
