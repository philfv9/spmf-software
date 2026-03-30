package ca.pfv.spmf.algorithms.sequentialpatterns.hmg;

// package dnacode;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import ca.pfv.spmf.tools.MemoryLogger;

/* Copyright (c) 2008-2025 M. Zohaib Nawaz, M. Saqib Nawaz, Philippe Fournier-Viger
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
* 
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
// import ca.pfv.spmf.tools.MemoryLogger;
/**
 * This is an implementation of the HMG algorithm. This algorithm is described
 * in: <br/>
 * <br/>
 * 
 * Efficient genome sequence compression via the fusion of MDL-based heuristics <br/>
 * <br/>
 * 
 *
 * @author M. Zohaib Nawaz, M. Saqib Nawaz, Philippe Fournier-Viger
 */
public class AlgoHMG {
	
	/** Enumeration of the two versions of HMG */
	public enum HMGVariant {
	    SA,
	    GA
	}

	/** The selected algorithm variant (GA or SA). */
	private HMGVariant algorithmVariant = null;

	/** Enables verbose console output when set to true. */
	private static final boolean DEBUG_MODE = false;

	/**
	 * Number of bits reserved for storing the pattern length in dictionary
	 * accounting.
	 */
	private static final int BITS_FOR_LENGTH = 4;

	/** Shared random generator to avoid repeated re-seeding. */
	private static final Random RANDOM = new Random();

	/** Results from the last run */
	private HMGResult lastRunResult;

	/** Nucleotides */
	private static final char[] NUCLEOTIDES = { 'A', 'C', 'T', 'G' };

	/** Non DNA codes */
	private static final char[] NON_DNA_CODES = createNonDNACodes();

	/** Log of 2 */
	private static final double LN2 = Math.log(2.0);

	/** Immutable container representing a pattern and its support. */
	private static final class PatternSupport {
		final String pattern;
		final int support;

		PatternSupport(final String pattern, final int support) {
			this.pattern = pattern;
			this.support = support;
		}
		
		 @Override
		    public String toString() {
		        return "PatternSupport{pattern='" + pattern + "', support=" + support + "}";
		    }
	}

	/** Result of a compression planning run. */
	public static class HMGResult {
		final long totalBases;
		final long originalBits;
		final long compressedBits;
		final int dictionaryBits;
		final List<PatternSupport> patterns;

		/** elapsed time */
		long elapsedMillis;

		/** maximum memory usage (for statistics) */
		private double maxMemoryUsage;

		HMGResult(long totalBases, long originalBits, long compressedBits, int dictionaryBits, long elapsedMillis,
				List<PatternSupport> patterns, double maxMemoryUsage) {
			this.totalBases = totalBases;
			this.originalBits = originalBits;
			this.compressedBits = compressedBits;
			this.dictionaryBits = dictionaryBits;
			this.elapsedMillis = elapsedMillis;
			this.patterns = patterns;
			this.maxMemoryUsage = maxMemoryUsage;
		}
	}

	/**
	 * Run the HMG algorithm from code.
	 * 
	 * @param dataset              dataset filename
	 * @param generations          GA generations
	 * @param requiredPatternCount number of top subsequences to target
	 * @param outputFile           output file to write patterns
	 * @param crossoverVariant     the type of crossover
	 * @param mutationVariant      the type of mutation to be used
	 * @param spmfStyleOutput if true the output will be in SPMF format otherwise as a String
	 * @return run result
	 */
	public HMGResult runGAAlgorithm(String dataset, int generations, int requiredPatternCount, String outputFile,
			HMGCrossoverVariant crossoverVariant, HMGMutationVariant mutationVariant, boolean spmfStyleOutput) {

		algorithmVariant = HMGVariant.GA;

		MemoryLogger.getInstance().reset();
		long startWall = System.currentTimeMillis();

		List<String> sequences = loadSequences(dataset);

		if (sequences.isEmpty()) {
			System.err.println("No sequences loaded from " + dataset);
			lastRunResult = new HMGResult(0, 0, 0, 0, 0, new ArrayList<>(), 0);
			return lastRunResult;
		}

		if (crossoverVariant == null || mutationVariant == null) {
			System.err.println("GA requires to select both crossoverVariant and mutationVariant.");
			lastRunResult = emptyResult();
			return lastRunResult;
		}

		lastRunResult = runGA(sequences, crossoverVariant, mutationVariant, generations, requiredPatternCount);

		writeOutputFile(outputFile, lastRunResult.patterns, spmfStyleOutput);
		
		MemoryLogger.getInstance().checkMemory();
		lastRunResult.maxMemoryUsage = MemoryLogger.getInstance().getMaxMemory();
		lastRunResult.elapsedMillis = System.currentTimeMillis() - startWall + lastRunResult.elapsedMillis;
		;

		printDebugInformation(dataset, lastRunResult);

		return lastRunResult;
	}

	/**
	 * Run the HMG algorithm from code.
	 * 
	 * @param dataset    dataset filename
	 * @param top        number of top subsequences to target
	 * @param outputFile output file to write patterns
	 * @param spmfStyleOutput if true the output will be in SPMF format otherwise as a String
	 * @return run result
	 */
	public HMGResult runSAAlgorithm(String dataset, int top, String outputFile, boolean spmfStyleOutput) {

		algorithmVariant = HMGVariant.SA;

		MemoryLogger.getInstance().reset();
		long startWall = System.currentTimeMillis();

		List<String> sequences = loadSequences(dataset);

		if (sequences.isEmpty()) {
			System.err.println("No sequences loaded from " + dataset);
			lastRunResult = emptyResult();
			return lastRunResult;
		}

		lastRunResult = runSA(sequences, top);

		writeOutputFile(outputFile, lastRunResult.patterns, spmfStyleOutput);

		MemoryLogger.getInstance().checkMemory();
		lastRunResult.maxMemoryUsage = MemoryLogger.getInstance().getMaxMemory();
		lastRunResult.elapsedMillis = System.currentTimeMillis() - startWall + lastRunResult.elapsedMillis;

		printDebugInformation(dataset, lastRunResult);

		return lastRunResult;
	}

	/**
	 * Print debugging information
	 * 
	 * @param dataset the input file
	 * @param result  the result
	 */
	private void printDebugInformation(String dataset, HMGResult result) {
		if (DEBUG_MODE) {
			double totalCompressed = result.compressedBits + result.dictionaryBits;
			double bpb = result.totalBases == 0 ? 0.0 : (totalCompressed / (double) result.totalBases);
			System.out.println("HMG debug:");
			System.out.println(" Variant: " + algorithmVariant);
			System.out.println(" Dataset: " + dataset);
			System.out.println(" Total bases: " + result.totalBases);
			System.out.println(" Original bits: " + result.originalBits);
			System.out.println(" Compressed bits: " + result.compressedBits + ", Dict bits: " + result.dictionaryBits);
			System.out.println(" BPB: " + bpb);
			System.out.println(" Time (s): " + (result.elapsedMillis / 1000.0));
		}
	}

	/** Empty result */
	private static HMGResult emptyResult() {
		return new HMGResult(0, 0, 0, 0, 0, new ArrayList<>(), 0);
	}

	/**
	 * Print stats from the last HMG run.
	 */
	public void printStats() {
		if (lastRunResult == null) {
			System.out.println("No HMG run executed yet.");
			return;
		}
		double totalCompressed = lastRunResult.compressedBits + lastRunResult.dictionaryBits;
		double bpb = lastRunResult.totalBases == 0 ? 0.0 : (totalCompressed / (double) lastRunResult.totalBases);
		System.out.println("=============  HMG-" + algorithmVariant + " 2.64 - STATS =============");
		System.out.println(" Patterns: " + (lastRunResult.patterns == null ? 0 : lastRunResult.patterns.size()));
		System.out.println(" BPB: " + bpb);
		System.out.println(" Time   : " + lastRunResult.elapsedMillis / (double) 1000 + " s");
		System.out.println(" Memory   : " + lastRunResult.maxMemoryUsage + " MB");
		System.out.println("========================================");
	}

	/**
	 * Load sequences from a dataset file in the current directory. Supports FASTA
	 * (.fa/.fasta) and plain concatenated lines.
	 * 
	 * @param dataset dataset filename or path
	 * @return list of loaded sequences
	 */
	private static List<String> loadSequences(String dataset) {
		List<String> dnaSequences = new ArrayList<>();
		StringBuilder builder = new StringBuilder();
		try {

			boolean isFasta = dataset.endsWith(".fasta") || dataset.endsWith(".fa");

			try (java.util.stream.Stream<String> stream = Files.lines(new File(dataset).toPath())) {
				java.util.Iterator<String> it = stream.iterator();

				while (it.hasNext()) {

					String raw = it.next();
					String line = raw.trim();

					if (line.isEmpty()) {
						continue;
					}

					if (isFasta) {
						if (line.startsWith(">")) {
							if (builder.length() > 0) {
								dnaSequences.add(builder.toString());
								builder.setLength(0);
							}
						} else {
							builder.append(line);
						}
					} else {
						if (builder.length() == 0 && line.startsWith(">")) {
							continue;
						}
						if (line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
							continue;
						}
						builder.append(line);
					}
				}
			}

			if (builder.length() > 0) {
				dnaSequences.add(builder.toString());
			}

		} catch (IOException e) {
			System.err.println("Error reading: " + dataset + " - " + e.getMessage());
		}
		return dnaSequences;
	}

	/**
	 * Run GA workflow with selected crossover and mutation operators.
	 * 
	 * @param sequences        input sequences
	 * @param crossoverVariant "single" or "cycle"
	 * @param mutationVariant  "single" or "scramble"
	 * @param generations      GA generations to iterate
	 * @param top              number of top subsequences to target
	 * @return compression planning result
	 */
	private static HMGResult runGA(List<String> sequences, HMGCrossoverVariant crossoverVariant,
			HMGMutationVariant mutationVariant, int generations, int top) {

		long totalBases = sequences.stream().mapToLong(String::length).sum();
		long originalBits = totalBases * 2L;

		Map<String, Character> sequenceToCodeMap = new HashMap<>();
		java.util.LinkedHashMap<String, Integer> collectedPatterns = new java.util.LinkedHashMap<>();

		// Build substitution symbol space (all printable ASCII except A,C,T,G)
		char[] codes = NON_DNA_CODES;

		while (collectedPatterns.size() < top) {

			String parent1 = generateRandomDNA(2, 6);
			String parent2;

			do {
				parent2 = generateRandomDNA(2, 6);
			} while (parent2.equals(parent1));

			Map<String, Integer> sequenceOccurrences = new HashMap<>();
			sequenceOccurrences.put(parent1, countOccurrencesAllowOverlap(sequences, parent1));
			sequenceOccurrences.put(parent2, countOccurrencesAllowOverlap(sequences, parent2));

			String[] crossoverResult;
			String child1, child2;

			int maxOverallOccurrences = 0;
			String bestDnaString = "";
			String secondBestDnaString = "";
			int secondMaxOccurrencesInGeneration = 0;

			// --- The GA loop -----
			for (int gen = 1; gen <= generations; gen++) {

				// Crossover
				if (crossoverVariant == HMGCrossoverVariant.SINGLE) {
					crossoverResult = applySinglePointCrossover(parent1, parent2);
				} else {
					crossoverResult = applyCycleCrossover(parent1, parent2);
				}

				if (crossoverResult[0].isEmpty() || crossoverResult[1].isEmpty())
					break;

				// Mutation
				if (mutationVariant == HMGMutationVariant.SINGLE) {
					child1 = applySinglePointMutation(parent1);
					do {
						child2 = applySinglePointMutation(parent2);
					} while (child2.equals(child1));
				} else {
					child1 = applyScrambleMutation(parent1);
					child2 = applyScrambleMutation(parent2);
				}

				// Randomly evaluate one mutated offspring to reduce compute
				if (RANDOM.nextBoolean()) {
					sequenceOccurrences.put(child1, countOccurrencesAllowOverlap(sequences, child1));
				} else {
					sequenceOccurrences.put(child2, countOccurrencesAllowOverlap(sequences, child2));
				}

				// Evaluate best of generation
				String bestInGen = "";
				int maxInGen = 0;
				String secondInGen = "";
				secondMaxOccurrencesInGeneration = 0;

				for (Map.Entry<String, Integer> e : sequenceOccurrences.entrySet()) {

					int v = e.getValue();

					if (v > maxInGen) {
						secondMaxOccurrencesInGeneration = maxInGen;
						secondInGen = bestInGen;

						maxInGen = v;
						bestInGen = e.getKey();
					} else if (v > secondMaxOccurrencesInGeneration) {
						secondMaxOccurrencesInGeneration = v;
						secondInGen = e.getKey();
					}
				}

				if (maxInGen > maxOverallOccurrences) {
					maxOverallOccurrences = maxInGen;
					bestDnaString = bestInGen;
				}

				if (secondMaxOccurrencesInGeneration > 0) {
					secondBestDnaString = secondInGen;
				}

				parent1 = bestInGen;
				parent2 = secondInGen;
			}

			// Insert best pattern found
			if (maxOverallOccurrences > 0 && !collectedPatterns.containsKey(bestDnaString)) {
				collectedPatterns.put(bestDnaString, maxOverallOccurrences);
				sequenceToCodeMap.putIfAbsent(bestDnaString, codes[Math.max(0, collectedPatterns.size() - 1)]);

				replaceAllInPlace(sequences, bestDnaString, String.valueOf(sequenceToCodeMap.get(bestDnaString)));
			}

			// Insert second best pattern found
			if (collectedPatterns.size() < top && secondMaxOccurrencesInGeneration > 0
					&& !secondBestDnaString.equals(bestDnaString)
					&& !collectedPatterns.containsKey(secondBestDnaString)) {
				collectedPatterns.put(secondBestDnaString, secondMaxOccurrencesInGeneration);
				sequenceToCodeMap.putIfAbsent(secondBestDnaString, codes[Math.max(0, collectedPatterns.size() - 1)]);

				replaceAllInPlace(sequences, secondBestDnaString,
						String.valueOf(sequenceToCodeMap.get(secondBestDnaString)));
			}
		}

		// ---- Score and select beneficial patterns ----
		List<PatternInfo> sorted = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : collectedPatterns.entrySet()) {
			String pattern = entry.getKey();
			int frequency = entry.getValue();

			if (pattern.length() >= 2 && frequency >= 2) {

				int originalBitsPattern = pattern.length() * 2 * frequency;
				int huffmanBits = estimateHuffmanSize(frequency, totalBases);
				int dictionaryOverhead = (pattern.length() * 2) + 8 + ilog2(Math.max(1, frequency));

				int compressedSize = huffmanBits + dictionaryOverhead;
				int compressionBenefit = originalBitsPattern - compressedSize;

				double lengthScore = pattern.length() * 2.0;
				double frequencyScore = log2(Math.max(2, frequency));
				double huffmanScore = originalBitsPattern / Math.max(1.0, compressedSize);
				double combinedScore = lengthScore * frequencyScore * huffmanScore;

				if (combinedScore >= 1 && compressionBenefit > dictionaryOverhead) {
					sorted.add(new PatternInfo(pattern, frequency, compressionBenefit, combinedScore, huffmanBits));
				}
			}
		}

		// Sort patterns by score
		Collections.sort(sorted, (a, b) -> {
			int c = Double.compare(b.combinedScore, a.combinedScore);
			if (c != 0)
				return c;

			double ra = (double) b.compressionBenefit / Math.max(1, b.huffmanBits);
			double rb = (double) a.compressionBenefit / Math.max(1, a.huffmanBits);
			return Double.compare(ra, rb);
		});

		List<PatternInfo> beneficial = filterByPhases(sorted);
		if (beneficial.size() > 50) {
			beneficial = beneficial.subList(0, 50);
		}

		Map<String, String> patternToBitCode = new HashMap<>();
		for (int j = 0; j < beneficial.size(); j++) {
			patternToBitCode.put(beneficial.get(j).pattern, getBitCode(j));
		}

		// ---- Compute compressed size ----
		long compressedBits = 0;
		int dictionaryBits = 0;

		for (PatternInfo p : beneficial) {
			String code = patternToBitCode.get(p.pattern);
			dictionaryBits += BITS_FOR_LENGTH + (p.pattern.length() * 2) + 4 + code.length();
		}

		for (String seq : sequences) {
			String remainingSequence = seq;

			for (PatternInfo p : beneficial) {
				String code = patternToBitCode.get(p.pattern);

				int occ = countOccurrencesAllowOverlapInString(remainingSequence, p.pattern);
				compressedBits += (long) occ * code.length();

				remainingSequence = remainingSequence.replace(p.pattern, "");
			}

			compressedBits += (long) remainingSequence.length() * 2;
		}

		// ---- Output patterns ----
		List<PatternSupport> outPatterns = new ArrayList<>();
		Set<String> included = new HashSet<>();

		for (PatternInfo p : beneficial) {
			outPatterns.add(new PatternSupport(p.pattern, p.frequency));
			included.add(p.pattern);
		}

		if (outPatterns.size() < top) {
			for (Map.Entry<String, Integer> e : collectedPatterns.entrySet()) {
				if (!included.contains(e.getKey())) {
					outPatterns.add(new PatternSupport(e.getKey(), e.getValue()));

					if (outPatterns.size() >= top) {
						break;
					}
				}
			}
		}

		return new HMGResult(totalBases, originalBits, compressedBits, dictionaryBits, 0, outPatterns, 0);
	}

	/**
	 * Run SA workflow to search patterns and compute compression plan.
	 * 
	 * @param sequences input sequences
	 * @param top       number of top subsequences to target
	 * @return compression planning result
	 */
	private static HMGResult runSA(List<String> sequences, int top) {

		long totalBases = sequences.stream().mapToLong(String::length).sum();
		long originalBits = totalBases * 2L;

		Map<String, Character> sequenceToCodeMap = new HashMap<>();
		LinkedHashMap<String, Integer> collectedPatterns = new LinkedHashMap<>();

		double initialTemperature = 100.0;
		double coolingRate = 0.50;

		// Build substitution symbol space (all printable ASCII except A,C,T,G)
		char[] codes = NON_DNA_CODES;

		// === Simulated Annealing search phase ========
		while (collectedPatterns.size() < top) {

			String current = generateRandomDNA(2, 6);
			String best = current;

			int bestOcc = countOccurrencesNonOverlap(sequences, best);
			double temperature = initialTemperature;

			while (temperature > 1) {

				String neighbor = generateNeighbor(best);
				int newOcc = countOccurrencesNonOverlap(sequences, neighbor);

				if (newOcc > bestOcc) {
					best = neighbor;
					bestOcc = newOcc;
				} else if (Math.random() < Math.exp((newOcc - bestOcc) / temperature)) {
					best = neighbor;
					bestOcc = newOcc;
				}
				temperature *= coolingRate;
			}

			if (bestOcc > 0 && !collectedPatterns.containsKey(best)) {
				collectedPatterns.put(best, bestOcc);

				sequenceToCodeMap.putIfAbsent(best, codes[Math.max(0, collectedPatterns.size() - 1)]);
				replaceAllInPlace(sequences, best, String.valueOf(sequenceToCodeMap.get(best)));
			}
		}

		// === Score & filter patterns =========
		List<PatternInfo> sortedCandidates = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : collectedPatterns.entrySet()) {
			String pattern = entry.getKey();
			int frequency = entry.getValue();

			if (pattern.length() >= 3 && frequency >= 2) {

				int originalBitsPattern = pattern.length() * 2 * frequency;
				int huffmanBits = estimateHuffmanSize(frequency, totalBases);
				int dictionaryOverhead = (pattern.length() * 2) + 8 + ilog2(Math.max(1, frequency));

				int compressedSize = huffmanBits + dictionaryOverhead;
				int compressionBenefit = originalBitsPattern - compressedSize;

				double lengthScore = pattern.length() * 2.0;
				double frequencyScore = log2(Math.max(2, frequency));
				double huffmanScore = originalBitsPattern / Math.max(1.0, compressedSize);
				double combinedScore = lengthScore * frequencyScore * huffmanScore;

				if (combinedScore >= 12 && compressionBenefit > dictionaryOverhead * 2) {
					sortedCandidates
							.add(new PatternInfo(pattern, frequency, compressionBenefit, combinedScore, huffmanBits));
				}
			}
		}

		Collections.sort(sortedCandidates, (a, b) -> {
			int c = Double.compare(b.combinedScore, a.combinedScore);
			if (c != 0)
				return c;
			double ra = (double) b.compressionBenefit / Math.max(1, b.huffmanBits);
			double rb = (double) a.compressionBenefit / Math.max(1, a.huffmanBits);
			return Double.compare(ra, rb);
		});

		List<PatternInfo> beneficial = filterByPhases(sortedCandidates);
		if (beneficial.size() > 50)
			beneficial = beneficial.subList(0, 50);

		// === Assign bitcodes ========
		Map<String, String> patternToBitCode = new HashMap<>();
		for (int j = 0; j < beneficial.size(); j++) {
			patternToBitCode.put(beneficial.get(j).pattern, getBitCode(j));
		}

		// === Compute compressed size =======
		long compressedBits = 0;
		int dictionaryBits = 0;

		for (PatternInfo p : beneficial) {
			String code = patternToBitCode.get(p.pattern);
			dictionaryBits += BITS_FOR_LENGTH + (p.pattern.length() * 2) + 4 + code.length();
		}

		for (String seq : sequences) {
			String processed = seq;

			for (PatternInfo p : beneficial) {
				String code = patternToBitCode.get(p.pattern);
				int occ = countOccurrencesAllowOverlapInString(processed, p.pattern);

				compressedBits += (long) occ * code.length();
				processed = processed.replace(p.pattern, "");
			}

			compressedBits += (long) processed.length() * 2;
		}

		// === Create output patterns ====
		List<PatternSupport> outPatterns = new ArrayList<>();
		Set<String> included = new HashSet<>();

		for (PatternInfo p : beneficial) {
			outPatterns.add(new PatternSupport(p.pattern, p.frequency));
			included.add(p.pattern);
		}

		if (outPatterns.size() < top) {
			for (Map.Entry<String, Integer> e : collectedPatterns.entrySet()) {
				if (!included.contains(e.getKey())) {
					outPatterns.add(new PatternSupport(e.getKey(), e.getValue()));
					if (outPatterns.size() >= top)
						break;
				}
			}
		}
		return new HMGResult(totalBases, originalBits, compressedBits, dictionaryBits, 0, outPatterns, 0);
	}

	/** Create the NON DNA codes */
	private static char[] createNonDNACodes() {
		StringBuilder b = new StringBuilder();
		for (char c = 32; c < 127; c++) {
			if (c != 'A' && c != 'C' && c != 'T' && c != 'G') {
				b.append(c);
			}
		}
		return b.toString().toCharArray();
	}

	/** Pattern scoring container (internal). */
	private static class PatternInfo {
		final String pattern;
		final int frequency;
		final int compressionBenefit;
		final double combinedScore;
		final int huffmanBits;

		PatternInfo(String pattern, int frequency, int compressionBenefit, double combinedScore, int huffmanBits) {
			this.pattern = pattern;
			this.frequency = frequency;
			this.compressionBenefit = compressionBenefit;
			this.combinedScore = combinedScore;
			this.huffmanBits = huffmanBits;
		}
	}

	/**
	 * Filter patterns in phases based on length and benefit thresholds.
	 * 
	 * @param sortedPatterns sorted patterns
	 * @return selected patterns
	 */
	private static List<PatternInfo> filterByPhases(List<PatternInfo> sortedPatterns) {
		List<PatternInfo> beneficial = new ArrayList<>();

		for (PatternInfo pattern : sortedPatterns) {

			int length = pattern.pattern.length();

			if (length >= 6 && pattern.compressionBenefit > 100) {
				beneficial.add(pattern);
			} else if (length >= 4 && length < 6 && pattern.compressionBenefit > 150) {
				beneficial.add(pattern);
			} else if (length < 4 && pattern.compressionBenefit > 200) {
				beneficial.add(pattern);
			}
		}
		return beneficial;
	}

	/**
	 * Generate variable-length prefix-free bit code for a pattern index.
	 * 
	 * @param index pattern index
	 * @return bit string
	 */
	private static String getBitCode(int index) {
		if (index == 0)
			return "0";
		if (index == 1)
			return "1";

		int bitLength = (int) (Math.log(index) / LN2) + 1;
		StringBuilder code = new StringBuilder();
		for (int i = 0; i < bitLength - 1; i++) {
			code.append('1');
		}
		code.append('0');

		String binary = Integer.toBinaryString(index);
		code.append(binary.substring(1));

		return code.toString();
	}

	/**
	 * Count occurrences allowing overlap.
	 *
	 * @param sequences list of sequences
	 * @param target    target pattern
	 * @return total overlapping occurrences
	 */
	private static int countOccurrencesAllowOverlap(List<String> sequences, String target) {
		if (target == null || target.isEmpty())
			return 0;

		int totalOccurrences = 0;
		for (String sequence : sequences) {
			String s = sequence; // already trimmed earlier when loading; avoid trim in hot loops
			int from = 0;
			while (true) {
				int pos = s.indexOf(target, from);
				if (pos == -1)
					break;
				totalOccurrences++;
				from = pos + 1; // advance by 1 to allow overlap
			}
		}
		return totalOccurrences;
	}

	/**
	 * Count occurrences allowing overlap in a single string (efficient, no list
	 * allocation).
	 */
	private static int countOccurrencesAllowOverlapInString(final String sequence, final String target) {
		int total = 0;
		int from = 0;
		while (true) {
			int pos = sequence.indexOf(target, from);
			if (pos == -1)
				break;
			total++;
			from = pos + 1; // allow overlapping
		}
		return total;
	}

	/**
	 * Count occurrences non-overlapping.
	 * 
	 * @param sequences list of sequences
	 * @param target    pattern
	 * @return total non-overlapping occurrences
	 */
	private static int countOccurrencesNonOverlap(List<String> sequences, String target) {
		return sequences.stream().mapToInt(sequence -> {
			int count = 0, pos = 0;

			while ((pos = sequence.indexOf(target, pos)) != -1) {
				count++;
				pos += target.length();
			}

			return count;
		}).sum();
	}

	/**
	 * Generate random DNA string of length in [minLen, maxLen].
	 * 
	 * @param minLen min length (inclusive)
	 * @param maxLen max length (inclusive)
	 * @return DNA string
	 */
	private static String generateRandomDNA(int minLen, int maxLen) {
		int length = RANDOM.nextInt(maxLen - minLen + 1) + minLen;
		StringBuilder dna = new StringBuilder(length);

		for (int i = 0; i < length; i++) {
			dna.append(NUCLEOTIDES[RANDOM.nextInt(4)]);
		}

		return dna.toString();
	}

	/**
	 * Single-point mutation of a DNA string.
	 * 
	 * @param dna input DNA
	 * @return mutated DNA
	 */
	private static String applySinglePointMutation(String dna) {
		if (dna.isEmpty())
			return dna;

		int mutationPosition = RANDOM.nextInt(dna.length());
		char currentBase = dna.charAt(mutationPosition);
		char newBase;

		do {
			newBase = NUCLEOTIDES[RANDOM.nextInt(4)];
		} while (newBase == currentBase);

		StringBuilder mutated = new StringBuilder(dna);
		mutated.setCharAt(mutationPosition, newBase);
		return mutated.toString();
	}

	/**
	 * Single-point crossover between two DNA strings.
	 * 
	 * @param dna1 first parent
	 * @param dna2 second parent
	 * @return two offspring strings; empty if not possible
	 */
	private static String[] applySinglePointCrossover(String dna1, String dna2) {
		int minLength = Math.min(dna1.length(), dna2.length());

		if (minLength < 2) {
			return new String[] { "", "" };
		}

		int crossoverPoint = RANDOM.nextInt(minLength);

		String offspring1 = dna1.substring(0, crossoverPoint) + dna2.substring(crossoverPoint);
		String offspring2 = dna2.substring(0, crossoverPoint) + dna1.substring(crossoverPoint);

		return new String[] { offspring1, offspring2 };
	}

	/**
	 * Cycle crossover adapted for strings by position matching within common prefix
	 * length.
	 * 
	 * @param dna1 first parent
	 * @param dna2 second parent
	 * @return two offspring strings; empty if not possible
	 */
	private static String[] applyCycleCrossover(String dna1, String dna2) {
		int minLength = Math.min(dna1.length(), dna2.length());

		if (minLength < 2) {
			return new String[] { "", "" };
		}

		String prefix1 = dna1.substring(0, minLength);
		String prefix2 = dna2.substring(0, minLength);

		char[] offspring1 = prefix1.toCharArray();
		char[] offspring2 = prefix2.toCharArray();
		boolean[] visited = new boolean[minLength];

		int pos = 0;
		while (!visited[pos]) {
			visited[pos] = true;
			char value = dna2.charAt(pos);
			int nextPos = prefix1.indexOf(value);
			if (nextPos == -1)
				break;
			pos = nextPos;
		}

		for (int i = 0; i < minLength; i++)
			if (!visited[i]) {
				char t = offspring1[i];
				offspring1[i] = offspring2[i];
				offspring2[i] = t;
			}

		String remaining = "";

		if (dna1.length() > minLength) {
			remaining = dna1.substring(minLength);
		} else if (dna2.length() > minLength) {
			remaining = dna2.substring(minLength);
		}

		return new String[] { new String(offspring1) + remaining, new String(offspring2) + remaining };
	}

	/**
	 * Scramble mutation on a random substring of the DNA string.
	 * 
	 * @param dna input DNA
	 * @return mutated DNA
	 */
	private static String applyScrambleMutation(String dna) {
		if (dna.length() < 2) {
			return dna;
		}

		char[] a = dna.toCharArray();
		int subsetSize = RANDOM.nextInt(dna.length() - 1) + 2;
		int start = RANDOM.nextInt(dna.length() - subsetSize + 1);
		int end = start + subsetSize;

		for (int i = end - 1; i > start; i--) {
			int j = RANDOM.nextInt(i - start + 1) + start;
			char t = a[i];
			a[i] = a[j];
			a[j] = t;
		}

		return new String(a);
	}

	/**
	 * Generate a neighboring DNA string by mutating one base.
	 * 
	 * @param dna input DNA
	 * @return neighbor DNA
	 */
	private static String generateNeighbor(String dna) {
		return applySinglePointMutation(dna);
	}

	/** --- Compression helpers --- */

	/**
	 * Estimate Huffman encoded size (bits) for a symbol with given frequency over a
	 * corpus.
	 * 
	 * @param frequency  occurrences
	 * @param totalBases total bases across corpus
	 * @return estimated bits
	 */
	private static int estimateHuffmanSize(int frequency, long totalBases) {
		double probability = frequency / Math.max(1.0, (double) totalBases);
		int bitsNeeded = (int) Math.ceil(-Math.log(probability) / LN2);
		return bitsNeeded * frequency;
	}

	/**
	 * Replace all occurrences of token across list entries in-place.
	 * 
	 * @param list        list of strings (modified in place)
	 * @param token       token to replace
	 * @param replacement replacement value
	 */
	private static void replaceAllInPlace(List<String> list, String token, String replacement) {
		if (token.isEmpty())
			return;

		for (int i = 0; i < list.size(); i++) {

			String s = list.get(i);

			if (s.indexOf(token) == -1) {
				continue; // skip cheap if not present
			}
			// If replacement is single char, it may be faster to construct the new string
			// manually
			if (replacement.length() == 1 && token.length() == 1) {

				// quick path: replace chars without regex
				StringBuilder sb = new StringBuilder(s.length());
				char tgt = token.charAt(0);
				char rep = replacement.charAt(0);
				for (int k = 0; k < s.length(); k++) {
					char ch = s.charAt(k);
					sb.append(ch == tgt ? rep : ch);
				}
				list.set(i, sb.toString());

			} else {
				// fallback: use replace (which is fine when needed)
				list.set(i, s.replace(token, replacement));
			}
		}
	}

	/**
	 * Integer log2 ceiling helper.
	 * This is optimized using bit operations
	 * 
	 * @param x positive integer
	 * @return ceil(log2(x)) as int >= 0
	 */
	private static int ilog2(int x) {
		if (x <= 1)
			return 0;
		// compute ceil(log2(x))
		// if x is a power of two, result = 31 - leadingZeros(x)
		// else 32 - leadingZeros(x - 1)
		int v = x - 1;
		return 32 - Integer.numberOfLeadingZeros(v);
	}

	/**
	 * Computes log base 2 of a positive number.
	 *
	 * @param x the value to compute the logarithm for; must be > 0
	 * @return log₂(x)
	 * @throws IllegalArgumentException if x ≤ 0
	 */
	private static double log2(double v) {
		return Math.log(v) / LN2;
	}

	/**
	 * Write summary file in proper SPMF sequential pattern format.
	 *
	 * @param path             output file path
	 * @param patterns         list of sequential patterns with support (ps.pattern is a string)
	 * @param spmfStyleOutput  if true, output is in SPMF sequential pattern format
	 */
	private static void writeOutputFile(String path, List<PatternSupport> patterns, boolean spmfStyleOutput) {
	    try {
	        File f = new File(path);
	        File parent = f.getParentFile();
	        if (parent != null) parent.mkdirs();
	        

	        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {

	            if (spmfStyleOutput) {
	                // Write SPMF header using ASCII-based IDs
	                w.write("@CONVERTED_FROM_TEXT");
	                w.newLine();
	                // Collect unique characters
	                Set<Character> uniqueChars = new HashSet<>();
	                for (PatternSupport ps : patterns) {
	                    for (char c : ps.pattern.toCharArray()) {
	                        uniqueChars.add(c);
	                    }
	                }
	                // Write @ITEM=id=name lines
	                List<Character> sortedChars = new ArrayList<>(uniqueChars);
	                Collections.sort(sortedChars);
	                for (char c : sortedChars) {
	                    int id = (int) c; //
	                    w.write("@ITEM=" + id + "=" + c);
	                    w.newLine();
	                }

	                // Write sequential patterns
	                for (PatternSupport ps : patterns) {
	                    for (char c : ps.pattern.toCharArray()) {
	                        int id = (int) c; // ASCII-based ID
	                        w.write(id + " -1 ");
	                    }
	                    w.write("-2 #SUP: " + ps.support);
	                    w.newLine();
	                }

	            } else {
	                // Old style: just string of characters
	                for (PatternSupport ps : patterns) {
	                    w.write(ps.pattern + " #SUP: " + ps.support);
	                    w.newLine();
	                }
	            }
	        }
	    } catch (IOException e) {
	        System.err.println("Error writing output: " + e.getMessage());
	    }
	}
}
