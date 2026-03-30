package ca.pfv.spmf.algorithms.sequentialpatterns.mapd_owsp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
/* This file is copyright (c) 2021 Youxi Wu et al.
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import ca.pfv.spmf.tools.MemoryLogger;

/* This file is copyright (c) 2018 by Youxi Wu et al.
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
 * Implementation of the MAPD algorithm. It was converted from C++ to Java.
 * 
 * @author Youxi Wu et al.
 */
public class AlgoMAPD {
	/** runtime **/
	double runtime = 0;

	/** max memory **/
	double maxMemory = 0;

	/** Pattern count **/
	int patternCount = 0;

	/** if true, additional debugging information is output to the console */
	final boolean DEBUGMODE = false;

	/** object to write the output file (if the user wants to write to a file) */
	BufferedWriter writer = null;

	/** frequent patterns */
	private LinkedList<QPattern> frequentPatterns;

	private Map<Integer, Long> ofsIndex = null;

	/** minimum gap */
	private int minGap;

	/** maximum gap */
	private int maxGap;

	private int proMaxLen = 31;

	/** the character set */
	private char[] charSet = null;

	/**
	 * Constructor
	 */
	public AlgoMAPD() {

	}

	/**
	 * Add a pattern to the queue;
	 * 
	 * @param pattern a pattern
	 */
	public void put(QPattern pattern) {
		frequentPatterns.addLast(pattern);
	}

	/**
	 * Remove and return a pattern from the queue;
	 * 
	 * @return a pattern
	 */
	public QPattern get() {
		return frequentPatterns.removeFirst();
	}

	/**
	 * Check if there are patterns
	 * 
	 * @return true if yes, otherwise false
	 */
	public boolean empty() {
		return frequentPatterns.isEmpty();
	}

	public void getNewLevels(Map<Integer, Integer>[] newLevels, long[] supports) {
		for (int i = 0; i < charSet.length; i++) {
			newLevels[i] = new HashMap<Integer, Integer>();
			supports[i] = 0;
		}
	}

	/*
	 * get length-1 frequent patterns;
	 */
	public int getLen_1Pats(String str, long[] supports, double threshold, StringBuffer buffer, int n)
			throws IOException {
		int strLen = str.length();
		Map<Integer, Integer>[] newLevels = new Map[charSet.length];
		getNewLevels(newLevels, supports);
		QPattern pt = null;
		for (int i = 0; i < strLen; i++) {
			for (int j = 0; j < charSet.length; j++) {
				if (str.charAt(i) == charSet[j]) {
					supports[j]++;
					newLevels[j].put(i, 1);
				}
			}
		}
		threshold = (strLen - (n - 1) * ((maxGap + minGap) / 2.0 + 1)) / strLen * threshold;
		for (int i = 0; i < charSet.length; i++) {
			double val = (supports[i] * 1.0 / strLen);
			if (val > threshold) {
				buffer.append(charSet[i]);
				pt = new QPattern();
				String temp = buffer.toString();
				pt.setPattern(temp);
				pt.setSuffIndex(newLevels[i]);
				put(pt);
				buffer.delete(0, 1);

				// ====== Output single items =====
				writer.write(temp + " #SUP: " + val);
				writer.newLine();
				if (DEBUGMODE) {
					System.out.println(" " + temp + " #SUP: " + val);
				}
				// =================================
			} else {
				newLevels[i].clear();
				newLevels[i] = null;

			}
		}
		newLevels = null;
		return frequentPatterns.size();
	}

	/*
	 * compute the offset number using incomplete Nettree;
	 */
	public double getOffSup(String str) {
		double offSup = 0L;
		int index = 0, flex = maxGap - minGap + 1;
		long oldOcur = 0;
		int strLen = str.length();
		Map<Integer, Long> newOfsIndex = new HashMap<Integer, Long>();
		Collection<Integer> keys = ofsIndex.keySet();
		for (Iterator<Integer> key = keys.iterator(); key.hasNext();) {
			index = key.next().intValue();
			for (int t = 1, j = index + minGap + 1; j < strLen && t <= flex; j++, t++) {
				oldOcur = ofsIndex.get(index).longValue();
				offSup += oldOcur;
				if (!newOfsIndex.containsKey(j)) {// if not contain new node;
					newOfsIndex.put(j, oldOcur);
				} else { // if contain new node;
					long newOcur = newOfsIndex.get(j) + oldOcur;
					newOfsIndex.put(j, newOcur);
				}
			}
		}
		ofsIndex.clear();
		ofsIndex = null;
		ofsIndex = newOfsIndex;
		return offSup;
	}

	/**
	 * Construct incomplete Nettrees of super-patterns of a sub-pattern and compute
	 * their numbers of supports;
	 */
	public void IN_Support(QPattern oldPattern, int strLen, String str, Map<Integer, Integer>[] newLevel,
			long[] newOcurs) {
		int index = 0, flex = maxGap - minGap + 1;
		int oldOcur = 0;
		Collection<Integer> keys = oldPattern.getSuffIndex().keySet();
		for (Iterator<Integer> key = keys.iterator(); key.hasNext();) {
			index = key.next().intValue();
			for (int t = 1, j = index + minGap + 1; j < strLen && t <= flex; j++, t++) {
				for (int c1 = 0; c1 < charSet.length; c1++) {
					if (str.charAt(j) == charSet[c1]) {
						oldOcur = oldPattern.getSuffIndex().get(index).intValue();
						newOcurs[c1] += oldOcur;
						if (!newLevel[c1].containsKey(j)) {// if not contain new node;
							newLevel[c1].put(j, oldOcur);
						} else { // if contain new node;
							int ocur = newLevel[c1].get(j);
							newLevel[c1].put(j, (ocur + oldOcur));
						}
						break;
					}
				}
			}
		}
	}

	/**
	 * Read the mined sequence from a file;
	 * 
	 * @param fileName the input file
	 */
	public String readData(String fileName) {
		String s = null;
		StringBuffer buff = new StringBuffer();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
			s = br.readLine();
			String strs[];
			while (s != null) {
				s = s.trim();
				strs = s.split("\\s+");
				for (int i = 0; i < strs.length; i++) {
					buff.append(strs[i]);
					strs[i] = null;
				}
				s = br.readLine();
			}
			s = buff.toString();
			strs = null;
			buff.delete(0, s.length());
			br.close();
			br = null;
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
			System.out.println("/n The file is not found!");
		} catch (IOException e2) {
			e2.printStackTrace();
			System.out.println("read or write makes an error!");
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		return s;
	}

	/**
	 * Mine frequent patterns of various lengths from the sequence;
	 * 
	 * @throws IOException
	 */
	public void mineFrequentPatterns(String str, int n, double threshold, double[] offSup, float[] pro)
			throws IOException {
		StringBuffer buff = new StringBuffer();
		int strLen = str.length();
		int patLen = 1;
		int frePatsNum = 0;
		long[] sups = new long[charSet.length];
		QPattern pt = null;
		frePatsNum = getLen_1Pats(str, sups, threshold, buff, n);
		int queueLen = frequentPatterns.size();
		int genPatNum = 0;
		int Num = frePatsNum;

		patternCount = Num;

		MemoryLogger.getInstance().checkMemory();

		Map<Integer, Integer>[] newLevels = new Map[charSet.length];
		double candiSup = 0.0;
		double freSup = 0.0;
		while (!empty()) {
			if (DEBUGMODE) {
				System.out.println("the number of frequent patterns of length-" + patLen + " is " + Num);
			}
			Num = 0;
			genPatNum = 0;
			patLen++;
			candiSup = offSup[patLen] * pro[patLen] * threshold;
			freSup = offSup[patLen] * threshold;
			for (int k = 1; k <= frePatsNum; k++) {
				pt = get();
				buff.append(pt.getPattern());
				getNewLevels(newLevels, sups);
				IN_Support(pt, strLen, str, newLevels, sups);
				for (int t = 0; t < charSet.length; t++) {
					// double r=sups[t]/offSup;
					double val = sups[t];
					if (val >= candiSup) {

						buff.append(charSet[t]);
						QPattern newPat = new QPattern();
						String temp = buff.toString();
						newPat.setPattern(temp);
						newPat.setSuffIndex(newLevels[t]);
						put(newPat);
						genPatNum++;
						buff.delete(patLen - 1, patLen);
						int queueCaptity = frequentPatterns.size();
						if (queueCaptity > queueLen) {
							queueLen = queueCaptity;
						}

						if (sups[t] >= freSup) {
							Num++;
							patternCount++;
//							System.out.println(" " + Num + " " + patternCount);
							// ====== Output pattern =====
							writer.write(temp + " #SUP: " + (val*1.0d / strLen));
							writer.newLine();
							if (DEBUGMODE) {
								System.out.println(" " + temp + " #SUP: " + val);
							}
							// =================================
						}

					} else {
						newLevels[t].clear();
						newLevels[t] = null;
					}
				}
				pt.setPattern(null);
				pt.getSuffIndex().clear();
				pt.setSuffIndex(null);
				pt = null;
				buff.delete(0, patLen - 1);
			}
			frePatsNum = genPatNum;
		}
		newLevels = null;
		if (DEBUGMODE) {
			System.out.println("the maximum capacity of the queue is " + queueLen);
		}
	}

	/**
	 * Run the algorithm
	 * 
	 * @throws IOException if error while writing the output file
	 */
	public void runAlgorithm(String filePath, String outputFile, int minGap, int maxGap, int n, double threshold,
			char[] charSet) throws IOException {
		long startTime = System.currentTimeMillis();
		MemoryLogger.getInstance().reset();

		// Parameters
		this.minGap = minGap;
		this.maxGap = maxGap;
		this.charSet = charSet;

		// Initialization
		patternCount = 0;
		writer = new BufferedWriter(new FileWriter(outputFile));
		ofsIndex = new HashMap<Integer, Long>();
		frequentPatterns = new LinkedList<QPattern>();

		String str = readData(filePath);
		if (str == null || str.equals("")) {
			System.out.println("data is a null string");
		}
		int strLen = str.length();
		if (DEBUGMODE) {
			System.out.println("the length of the sequence is " + str.length());
		}

		int l1 = (strLen + maxGap) / (maxGap + 1);
		if (n > l1)
			n = l1;
		if (proMaxLen < n * 2) {
			proMaxLen = n * 2 + 1;
		}
		double[] offSup = new double[proMaxLen];
		float[] pro = new float[proMaxLen];
		for (int i = 2; i <= n; i++) {
			pro[i] = (float) ((strLen - (n - 1) * ((maxGap + minGap) / 2.0 + 1))
					/ (strLen - (i - 1) * ((maxGap + minGap) / 2.0 + 1)));
		}
		for (int i = n + 1; i < proMaxLen; i++) {
			pro[i] = 1.0f;
		}
		for (int j = 0; j < strLen; j++) {
			ofsIndex.put(j, 1L);
		}
		for (int j = 2; j < proMaxLen; j++) {
			offSup[j] = getOffSup(str);
		}
		ofsIndex.clear();
		ofsIndex = null;

		MemoryLogger.getInstance().checkMemory();
		mineFrequentPatterns(str, n, threshold, offSup, pro);
		MemoryLogger.getInstance().checkMemory();

		writer.close();
		long endTime = System.currentTimeMillis();
		runtime = endTime - startTime;
		maxMemory = MemoryLogger.getInstance().getMaxMemory();
	}

	/**
	 * Print statistics about the last algorithm execution
	 */
	/**
	 * Print stats about the last algorithm execution
	 */
	public void printStats() {
		System.out.println("=============  MAPD v2.60 - STATS =============");
		System.out.println(" Number of patterns found: " + patternCount);
		System.out.println(" Total time ~ " + runtime + " ms");
		System.out.println(" Maximum memory usage : " + maxMemory + " mb");
		System.out.println("===================================================");
	}
}
