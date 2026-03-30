package ca.pfv.spmf.algorithms.frequentpatterns.lthui_miner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.tools.MemoryLogger;


/* This file is copyright (c) 2020  Yanjun Yang, Philippe Fournier-Viger
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
* This is an implementation of the "LTHUI-Miner" algorithm for locally trending high
* utility itemset mining as described in the conference paper : <br/>
* <br/>
*
* Fournier-Viger, P., Yang, Y., Lin, J. C.W., Frnda, J. (2020). Mining Locally 
* Trending High Utility Itemsets. Proc. 24th Pacific-Asia Conf. Knowledge Discovery 
* and Data Mining (PAKDD 2020), Springer, LNAI, pp.99-111.
* 
* @author Yanjun Yang, Philippe Fournier-Viger
* @see TrendUtilityList
*/
public class AlgoLTHUIMiner {
	/** the time at which the algorithm started */
	private long startTimestamp = 0;

	/** the time at which the algorithm ended */
	private long endTimestamp = 0;

	/** the number of locally trending high-utility itemsets generated */
	private int lthuiCount = 0;

	private int candidateCount = 0;

	/** Map to remember the TWU of each item */
	private Map<Integer, Integer> mapItemToTWU;
	
	/** to store the (t - avg(t)) [molecule] and sum(t - avg(t))^2 [denominator], the length 
	 * of the list is (winlen/binlen) + 1 */
	private ArrayList<Double> timeDiff;
	
	/** the minimum timestamp of the database, normally the timestamp of the first transaction */
	private long minTime;   
	

		
	/** the number of bins in the database */
	private int numBin;
	
	/** the number of sliding windows in the database */
	private int numWin;
	
	/** the number of bins in a sliding window */
	private int numBinOfWin;
	
	/** the number of transactions in the database */
	private long dbLen = 0;
	
	/** writer to write the output file */
	private BufferedWriter writer = null;

	/** the number of TU-list that was constructed */
	private int joinCount;

	/**
	 * buffer for storing the current itemset that is mined when performing
	 * mining the idea is to always reuse the same buffer to reduce memory
	 * usage.
	 */
	private static final int BUFFERS_SIZE = 300;
	private int[] itemsetBuffer = null;

	/** this class represent an item and its utility in a transaction */
	private class Pair {
		int item = 0;
		int utility = 0;
	}

	/** to store the timestamp of each transaction */
	private ArrayList<Long> timeTid = new ArrayList<Long>();

	/** to store the index of the bin corresponding to each transaction */
	private ArrayList<Integer> binIndexTid = new ArrayList<Integer>();
	
	/**
	 * Default constructor
	 */
	public AlgoLTHUIMiner() {
		// constructor
	}
	
	
	/**
	 * Run the algorithm
	 * 
	 * @param input
	 * 				The input file path.
	 * @param output
	 * 				The output file path.
	 * @param lminutil
	 * 				The local minimum utility threshold.
	 * @param winLen
	 * 				The length of a sliding window.
	 * @param binLen
	 * 				The length of a bin.
	 * @param minSlope
	 * 				The minimum slope threshold (increasing trend).
	 * @param databaseStartTimestamp
	 * 				The start timestamp of the database.
	 * @param outputIndex
	 * 				If true, then output period with the index of bins, 
	 * 				otherwise, output period with timestamp
	 * @throws IOException
	 * 				Exception if error while writing the file.
	 */
	public void runAlgorithm(String input, String output, int lminutil,
			int winLen, int binLen, double minSlope, long databaseStartTimestamp, boolean outputIndex) throws IOException {
		lthuiCount = 0;
		candidateCount = 0;
		
		/** the maximum timestamp of the database, normally the timestamp of the last transaction */
		long maxTime;
		
		// reset maximum
		MemoryLogger.getInstance().reset();

		// initialize the buffer for storing the current itemset
		itemsetBuffer = new int[BUFFERS_SIZE];

		startTimestamp = System.currentTimeMillis();

		// the user directly sets the start timestamp
		if(databaseStartTimestamp >= 0) {
			this.minTime = databaseStartTimestamp;
		}
		
		// the number of bins in a sliding window 
		numBinOfWin = winLen / binLen;
		
		timeDiff = new ArrayList<Double>();
		
		// to fill in timeDiff and always reuse timeDiff when calculating the slope of a sliding window
		Double sum = 0.0;
		double tmpSum = 0.0;
		for(int i = 1; i <= numBinOfWin; i++)
			tmpSum += i;
		double avg = tmpSum / numBinOfWin;
		for(int i = 1; i <= numBinOfWin; i++) {
			timeDiff.add(i - avg);
			sum += Math.pow(i - avg, 2);
		}
		timeDiff.add(sum);
		
		writer = new BufferedWriter(new FileWriter(output));

		mapItemToTWU = new HashMap<Integer, Integer>();

		BufferedReader myInput = null;
		String thisLine;
		
		// PRE DATABASE PASS: store maxTime, numBin, timeTid and dbLen, 
		try {
			myInput = new BufferedReader(new InputStreamReader(
					new FileInputStream(new File(input))));
			long time = 0; 
			while ((thisLine = myInput.readLine()) != null) {
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#'
						|| thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}
				dbLen ++;
				String split[] = thisLine.split(":");
				// store timestamp for each transaction
				time = Long.parseLong(split[3]);
				// the user has not set the start timestamp
				if(dbLen == 1 && databaseStartTimestamp < 0)
					this.minTime = time;
				timeTid.add(time);
			}
			maxTime = time;
			numBin = (int) ((maxTime - minTime + 1) / binLen);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}
		// ignore the transactions at the end that cannot form a complete bin
		maxTime = minTime + numBin * binLen - 1;
		
		// FIRST DATABASE PASS: update dbLen; calculate binIndexTid and TWU of items.
		try {
			myInput = new BufferedReader(new InputStreamReader(
					new FileInputStream(new File(input))));
			// the index of current bin, initial to 0, indicating the first bin
			int indexBin = 0;
			// the start timestamp of the next bin
			long nextBinStart = 0;
			dbLen = 0;
			long time = 0; 
			while ((thisLine = myInput.readLine()) != null) {
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#'
						|| thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}
				String split[] = thisLine.split(":");
				String items[] = split[0].split(" ");
				int transactionUtility = Integer.parseInt(split[1]);
				time = Long.parseLong(split[3]);
				
				// ignore the transactions at the end that cannot form a complete bin
				if(time > minTime + numBin * binLen - 1)
					break;
				
				// for each item, we add the transaction utility to its TWU
				for (int i = 0; i < items.length; i++) {
					Integer item = Integer.parseInt(items[i]);
					Integer twu = mapItemToTWU.get(item);
					twu = (twu == null) ? transactionUtility : twu
							+ transactionUtility;
					mapItemToTWU.put(item, twu);
				}
				
				// calculate the start timestamp of the second bin in the database
				if(dbLen == 0) {
					nextBinStart = minTime + binLen * 1;
				}
				
				// find the index of the bin where the current transaction is located, and store the index
				while(time >= nextBinStart) {
					indexBin ++;
					nextBinStart += binLen;
				}
				if(time < nextBinStart) {
					binIndexTid.add(indexBin);
				}
				dbLen ++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}
		
		numWin = numBin - numBinOfWin + 1;
		
		// if the number of sliding window is less than 1, then stop the algorithm.
		if (numWin < 1) {
			return;
		}
		
		// CREATE A LIST TO STORE THE TU-LISTS OF ITEMS WITH TWU >= MIN_UTILITY. 
		List<TrendUtilityList> listOfTUList = new ArrayList<TrendUtilityList>();
		// CREATE A MAP TO STORE THE TU-LISTS FOR EACH ITEM.
		Map<Integer, TrendUtilityList> mapItemToTUList = new HashMap<Integer, TrendUtilityList>();
		for (Entry<Integer, Integer> entry : mapItemToTWU.entrySet()) {
			Integer item = entry.getKey();
			if (mapItemToTWU.get(item) >= lminutil) {
				TrendUtilityList uList = new TrendUtilityList(item, numBin, numWin);
				mapItemToTUList.put(item, uList);
				listOfTUList.add(uList);
			}
		}
		
		// SORT THE LIST OF HIGH TWU ITEMS IN ASCENDING ORDER
		Collections.sort(listOfTUList,
				new Comparator<TrendUtilityList>() {
					public int compare(TrendUtilityList o1, TrendUtilityList o2) {
						return compareItems(o1.item, o2.item);
					}
				});
		
		// SECOND DATABASE PASS: construct the TU-lists of 1-itemsets 
		// having utility >= lminutil (promising items)
		try {
			myInput = new BufferedReader(new InputStreamReader(
					new FileInputStream(new File(input))));
			int tid = 0;
			while ((thisLine = myInput.readLine()) != null) {
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#'
						|| thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}
				String split[] = thisLine.split(":");
				String items[] = split[0].split(" ");
				String utilityValues[] = split[2].split(" ");
				long time = Long.parseLong(split[3]);
				
				if(time > minTime + numBin * binLen - 1)
					break;
			
				int remainingUtility = 0;
				
				// Create revised transaction 
				List<Pair> revisedTransaction = new ArrayList<Pair>();
				for (int i = 0; i < items.length; i++) {
					Pair pair = new Pair();
					pair.item = Integer.parseInt(items[i]);
					pair.utility = Integer.parseInt(utilityValues[i]);
					// ================ PRUNINIG: Property1: pruning a low-TWU item in a database ================
					if (mapItemToTWU.get(pair.item) >= lminutil) {
						revisedTransaction.add(pair);
						remainingUtility += pair.utility;			
					}
				}
				
				Collections.sort(revisedTransaction, new Comparator<Pair>() {
					public int compare(Pair o1, Pair o2) {
						return compareItems(o1.item, o2.item);
					}
				});

				// Construct TU-lists for promising items according to revised transaction
				// for each item left in the transaction
				for (Pair pair : revisedTransaction) {
					// subtract the utility of this item from the remaining utility
					remainingUtility = remainingUtility - pair.utility;

					TrendUtilityList tuListOfItem = mapItemToTUList
							.get(pair.item);

					// Add a new Element to the utility list of this item
					// corresponding to this transaction
					Element element = new Element(tid, pair.utility, remainingUtility);

					tuListOfItem.addElement(element);
				}
				tid++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}
		
		// ----- to generate periods and bin utility information in 1-itemset TU-list -----
		for (TrendUtilityList tul : listOfTUList) {
			// scan the TU-list to get the bin utility information
			calBinInfos(tul);
			// scan the TU-list to get periods information
			findTrend(null, tul, lminutil, winLen, binLen, minSlope);
		}

		MemoryLogger.getInstance().checkMemory();

		// Mine the database recursively
		lthuiSearch(itemsetBuffer, 0, null, listOfTUList, lminutil,
				winLen, binLen, minSlope, outputIndex);

		MemoryLogger.getInstance().checkMemory();
		writer.close();
		endTimestamp = System.currentTimeMillis();
	}
	
	private int compareItems(int item1, int item2) {
		int compare = mapItemToTWU.get(item1) - mapItemToTWU.get(item2);
		// if the same, use the lexical order otherwise use the TWU.
		return (compare == 0) ? item1 - item2 : ((int) compare);
	}
	

	/**
	 * This is the recursive method to find all locally trending high utility itemsets. It writes
	 * the itemsets and their trending high utility periods to the output file.
	 * 
	 * @param prefix
	 * 				This is the current prefix. Initially, it is empty.
	 * @param prefixLength
	 * 				The current prefix length.
	 * @param pUL
	 * 				This is the TU-list of the prefix. Initially, it is empty.
	 * @param ULs
	 * 				The TU-lists corresponding to each extension of the
	 *            	prefix.
	 * @param lminutil
	 * 				The local minimum utility threshold.
	 * @param winLen
	 * 				The length of a sliding window.
	 * @param binLen
	 * 				The length of a bin.
	 * @param minSlope
	 * 				The minimum slope threshold.
	 * @param outputIndex
	 * 				If true, then output period with the index of bins, 
	 * 				otherwise, output period with timestamp
	 * @throws IOException
	 */
	private void lthuiSearch(int[] prefix, int prefixLength, TrendUtilityList pUL, List<TrendUtilityList> ULs, 
			int lminutil, int winLen, int binLen, double minSlope, boolean outputIndex)
			throws IOException {
		
		for (int i = 0; i < ULs.size(); i++) {
			TrendUtilityList X = ULs.get(i);

			// If trend periods of pX is not empty. we save the itemset: pX
			if (!X.trendPeriod.isEmpty()) {
				writeOut(prefix, prefixLength, X, binLen, outputIndex); 
			}
			
			// ======== PRUNINIG: Property2 : Pruning using the remaining utility in a database ==========
			if (!X.rutilPeriod.isEmpty() && X.sumUtils + X.sumRutils >= lminutil) {
				// This list will contain the TU-lists of pX extensions.
				List<TrendUtilityList> exULs = new ArrayList<TrendUtilityList>();
				candidateCount++;
				// after X according to the ascending order
				for (int j = i + 1; j < ULs.size(); j++) {
					TrendUtilityList Y = ULs.get(j);
					TrendUtilityList pXY = construct(pUL, X, Y);
					calBinInfos(pXY);
					findTrend(X, pXY, lminutil, winLen, binLen, minSlope);
					exULs.add(pXY);
					joinCount++;
				}
				// We create new prefix pX
				itemsetBuffer[prefixLength] = X.item;

				// We make a recursive call to discover all LTHUI with the prefix pX
				lthuiSearch(itemsetBuffer, prefixLength + 1, X, exULs, lminutil, winLen, binLen, minSlope, outputIndex);
			}
		}
	}

	/**
	 * This method constructs the TU-list of pXY
	 * 
	 * @param P
	 *            : the TU-list of prefix P.
	 * @param px
	 *            : the TU-list of pX
	 * @param py
	 *            : the TU-list of pY
	 * @return the TU-list of pXY
	 */
	private TrendUtilityList construct(TrendUtilityList P, TrendUtilityList px,
			TrendUtilityList py) {
		// create an empty TU-list for pXY
		TrendUtilityList pxyUL = new TrendUtilityList(py.item, numBin, numWin);
		// for each element in the TU-list of pX
		for (Element ex : px.elements) {
			// do a binary search to find element ey in py with tid = ex.tid
			Element ey = findElementWithTID(py, ex.tid);
			if (ey == null) {
				continue;
			}
			// if the prefix p is null
			if (P == null) {
				Element eXY = new Element(ex.tid, ex.utils + ey.utils,
						ey.rutils);
				pxyUL.addElement(eXY);

			} else {
				// find the element in the TU-list of p with the same tid
				Element e = findElementWithTID(P, ex.tid);
				if (e != null) {
					Element eXY = new Element(ex.tid, ex.utils + ey.utils
							- e.utils, ey.rutils);
					pxyUL.addElement(eXY);
				}
			}
		}
		// return the TU-list of pXY.
		return pxyUL;
	}

	/**
	 * Do a binary search to find the element with a given tid in a TU-list
	 * 
	 * @param tuList
	 *            the TU-list
	 * @param tid
	 *            the tid
	 * @return the element or null if none has the tid.
	 */
	private Element findElementWithTID(TrendUtilityList tuList, int tid) {
		List<Element> list = tuList.elements;

		int first = 0;
		int last = list.size() - 1;

		// the binary search
		while (first <= last) {
			int middle = (first + last) >>> 1; 

			if (list.get(middle).tid < tid) {
				first = middle + 1; 
			} else if (list.get(middle).tid > tid) {
				last = middle - 1; 
			} else {
				return list.get(middle);
			}
		}
		return null;
	}


	/**
	 * generate trending high utility periods and promising periods of itemset pX.
	 * 
	 * @param p
	 * 				prefix of pX
	 * @param pX
	 * 				the TU-list of target itemset
	 * @param lminutil
	 * 				local minimum threshold of utility
	 * @param winLen
	 * 				the length of a sliding window
	 * @param binLen
	 * 				the length of a bin
	 * @param minSlope
	 * 				the minimum slope of utility over time in a sliding window
	 */
	private void findTrend(TrendUtilityList p, TrendUtilityList pX, int lminutil, int winLen, int binLen, double minSlope) {

		int winEnd = numBinOfWin;
		
		int k = winLen / binLen;
		
		// zR, which represents the maximum proportion of the number of bins whose utility is 0 in a sliding window
		// 0.5 is the default value
		int zeroAble = k / 2;
		
		// to store the number of bins whose utility value is 0 in the current sliding window
		int countZero = 0;

		// let the sliding window slide on the sequence of bins
		for (int winStart = 0; winStart < numBin && winEnd - 1 < numBin; winStart ++, winEnd ++) {
			
			// the flag identifies whether the previous sliding window is a THUP, initial to false
			boolean trendPreFlag = false;
			// the flag identifies whether the previous sliding window is promising, initial to false
			boolean rutilPreFlag = false;
			
			// number of continuous sliding windows whose slope >= minslope
			int numSliding = 0;
			// sum of the slope of continuous sliding windows whose slope >= minslope
			double sumSlope = 0.0;
			
			// FIND THE FIRST CHECKED SLIDING WINDOW
			// sum of utility of itemset pX in the current sliding window 
			int sumUtil = 0;
			// sum of remaining utility of itemset pX in the current sliding window
			int sumRutil = 0;
			// the number of bins in the current sliding window
			int countBin = 0;
			// the number of bins whose utility value is 0 in the current sliding window
			countZero = 0;
			for (int i = winStart; i < numBin && countBin != numBinOfWin; i++) {
				if (countZero <= zeroAble) {
					if (countBin == 0) {
						winStart = i;
						// ======= PRUNING property3: pruning using the remaining utility in a sliding window ======
						if (p != null && p.winRemain.get(winStart) == false )
							continue;
					}
					if(pX.utilBin[i] == 0)
						countZero++;
					sumUtil += pX.utilBin[i];
					sumRutil += pX.rutilBin[i];
					countBin ++;
				}
				else {
					countBin = 0;
					countZero = 0;
					sumUtil = 0;
					sumRutil = 0;
					i = winStart; 
				}
			}
			
			// can not find a checked sliding window until the end of the bin sequence, then stop the for-loop
			if (countBin != numBinOfWin) 
				break;
			// have found a checked sliding window
			else { 
				if (sumUtil + sumRutil >= lminutil)
					rutilPreFlag = true;
				if (sumUtil >= lminutil) {
					double slope = calSlope(pX, winStart);
					if (slope >= minSlope) {
						numSliding ++;
						sumSlope += slope;						
						trendPreFlag = true;
					}
				}
			}
			
			// SLIDE THE WINDOW
			winEnd = winStart + numBinOfWin;
			int beginIndex = winStart, endIndex = winEnd, rBeginIndex = winStart, rEndIndex = winEnd;
			while (winStart < numBin && winEnd < numBin) {
				// ======= PRUNING property3: pruning using the remaining utility in a sliding window =======
				if (p != null && p.winRemain.get(winStart + 1) == false) {
					winStart ++;
					winEnd ++;
					break;
				}
				
				// update the number of empty bins in the sliding window
				if(pX.utilBin[winEnd] == 0)
					countZero++;
				if(pX.utilBin[winStart] == 0)
					countZero--;
				if(countZero > zeroAble)
					break;
				
				// sliding for left side of the sliding window
				sumUtil -= pX.utilBin[winStart];
				sumRutil -= pX.rutilBin[winStart];
				// sliding for right side of the sliding window
				sumUtil += pX.utilBin[winEnd];
				sumRutil += pX.rutilBin[winEnd];
				
				winStart ++;
				winEnd ++;
				
				double slope;
				// add THUP
				if (trendPreFlag) {
					if (sumUtil < lminutil || (slope = calSlope(pX, winStart)) < minSlope) {
						pX.trendPeriod.add(new Period(beginIndex, endIndex - 1));
						pX.trendSlope.add(sumSlope / numSliding);
						numSliding = 0;
						sumSlope = 0.0;
						trendPreFlag = false;
					} else {
						numSliding ++;
						sumSlope += slope;
						endIndex = winEnd;
					}
				} else {
					if (sumUtil >= lminutil && (slope = calSlope(pX, winStart)) >= minSlope) {
						beginIndex = winStart;
						endIndex = winEnd;
						
						numSliding ++;
						sumSlope += slope;
						trendPreFlag = true;
					}
				}
				// add rutilPeriod
				if (rutilPreFlag) {
					if (sumUtil + sumRutil < lminutil) {
						pX.rutilPeriod.add(new Period(rBeginIndex, rEndIndex - 1));
						for (int i = rBeginIndex; i <= (rEndIndex - 1) - numBinOfWin + 1; i++) {
							pX.winRemain.set(i);
						}
						rutilPreFlag = false;
					} else {
						rEndIndex = winEnd;
					}
				} else {
					if (sumUtil + sumRutil >= lminutil) {
						rBeginIndex = winStart;
						rEndIndex = winEnd;
						rutilPreFlag = true;
					}
				}
			}
			
			if (trendPreFlag) {
				pX.trendPeriod.add(new Period(beginIndex, endIndex - 1));
				pX.trendSlope.add(sumSlope / numSliding);
			}
			if (rutilPreFlag) {
				pX.rutilPeriod.add(new Period(rBeginIndex, rEndIndex - 1));
				for (int i = rBeginIndex; i <= (rEndIndex - 1) - numBinOfWin + 1; i++) {
					pX.winRemain.set(i);
				}
			}
		}
	}

	
	/**
	 * Calculate the slope of an itemset in a sliding window whose index of its start bin is winStart.
	 * 
	 * @param tul
	 * 			The TU-list of an itemset
	 * @param winStart
	 * 			the index of start bin of the sliding window
	 * @return
	 * 			slope of the itemset in the sliding window
	 */
	private double calSlope(TrendUtilityList tul, int winStart) {
		
		double slope = 0;
		double aveUtility = 0;
		for (int i = winStart; i < winStart + numBinOfWin; i ++) {
			aveUtility += tul.utilBin[i];
		}
		aveUtility = aveUtility / numBinOfWin;
		
		double molecule = 0;
		double denominator = timeDiff.get(timeDiff.size() - 1);
		if (Double.doubleToLongBits(denominator) == Double.doubleToLongBits(0.0))
			return slope;
		
		for (int i = winStart, j = 0; i < winStart + numBinOfWin; i ++, j ++) {
			molecule += (tul.utilBin[i] - aveUtility) * (timeDiff.get(j));
		}
		slope = (molecule) / (denominator);
		
		return slope;
	}
	
	
	/**
	 *  Calculate the utility and remaining utility of each bin in the database in terms of a TU-list
	 * @param tul
	 * 			  a TU-list for a LTHUI
	 */
	private void calBinInfos(TrendUtilityList tul) {
		// for each element in ult
		for (int i = 0; i < tul.elements.size(); i++) {
			// find the index of bin which the element locates in
			int indexBin = binIndexTid.get(tul.elements.get(i).tid);
			// update the utilBin and rutilBin in ult
			tul.utilBin[indexBin] += tul.elements.get(i).utils;
			tul.rutilBin[indexBin] += tul.elements.get(i).rutils;
		}
		
	}
	
	
	/**
	 * Method to write a locally trending high utility itemset and its trending 
	 * high utility periods to the output file.
	 * 
	 * @param prefix
	 * 				prefix to be wrote to the output file
	 * @param prefixLength
	 * 				the prefix length
	 * @param ult
	 * 				the TU-list of the LTHUI
	 * @param binLen
	 * 				the length of bins
	 * @param outputIndex
	 * 				If true, then output period with the index of bins, 
	 * 				otherwise, output period with timestamp
	 * @throws IOException
	 */
	private void writeOut(int[] prefix, int prefixLength, TrendUtilityList ult, int binLen, boolean outputIndex)
			throws IOException {
		lthuiCount++;

		StringBuilder buffer = new StringBuilder();
		// append the prefix
		for (int i = 0; i < prefixLength; i++) {
			buffer.append(prefix[i]);
			buffer.append(' ');
		}
		// append the last item
		buffer.append(ult.item);

		buffer.append(" #PERIOD-UTIL-SLOPE:");
		
		for (int i = 0; i < ult.trendPeriod.size(); i++) {
			Period p = ult.trendPeriod.get(i);
			if(outputIndex) {
				buffer.append(" [" + (p.beginIndex + 1)
						+ "," + (p.endIndex + 1)
						+ "] ");
			} else {
				buffer.append(" [" + (minTime + p.beginIndex * binLen)
						+ "," + (minTime + (p.endIndex + 1) * binLen - 1)
						+ "] ");
			}
			double slope = ult.trendSlope.get(i);
			int sum = 0;
			for (int j = p.beginIndex; j <= p.endIndex; j++) {
				sum += ult.utilBin[j];
			}
			buffer.append("(" + sum
					+ "," + slope
					+ ") ");
		}
		
		writer.write(buffer.toString());
		writer.newLine();
	}
	
	/**
	 * Print statistics about the latest execution to System.out.
	 */
	public void printStats() {
		System.out
				.println("=============  LTHUI-MINER ALGORITHM v2.44 - STATS =============");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp)
				+ " ms");
		System.out.println(" Memory ~ "
				+ MemoryLogger.getInstance().getMaxMemory() + " MB");
		System.out.println(" Locally Trending High-utility itemsets count : " + lthuiCount);
		System.out.println(" Join count : " + joinCount);
		System.out.println(" Candidate count : " + candidateCount);
		System.out
				.println("===================================================");
	}
}
