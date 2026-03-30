package ca.pfv.spmf.algorithms.sequentialpatterns.mapd_owsp;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.TreeMap;

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
 * Implementation of the OWSPMiner algorithm. It was converted from C++ to Java.
 * 
 * @author Youxi Wu et al.
 */
public class AlgoOWSPMiner {
	/** runtime **/
	double runtime = 0;

	/** max memory **/
	double maxMemory = 0;

	/** if true, additional debugging information is output to the console */
	final boolean DEBUGMODE = false;

	/** pattern count **/
	int freNum;

	/** number of calculations */
	int compnum = 0;

	/** the length of the sequence */
	int N = 0; 
	
	/** the sequence */
	char[] S = null; 
	
	/** character to be used for gap */
	char[] gapstr = null;
	
	/** Candidate mode **/
	int[] hxms = null;
	
	/** Frequent mode **/
	int[] pfms = null;
	
	/** minimum support **/
	int minsup = 1;
	
	/** Store frequent patterns by size*/
	List<String>[] freArr = null;
	
	/** Candidates */
	List<String> candidate = null;

	/** Number of occurrences */
	int num_occur;
	int pattern_len;
	int S_len;
	boolean bool_S[] = null;
	List<Pant_p> link_pan = null;
	
	/** Store frequent patterns */
	List<Integer> countNum = null; 

	/** For writing the output */
	private PrintWriter pw;

	/**
	 * Read the input file
	 * @param inputFile input file path
	 */
	public void read_file(String inputFile) {
		// StringBuilder result = new StringBuilder();
		try {
			// URL url = OWSP_Miner.class.getResource("data.txt");
			File file = new File(inputFile);
			BufferedReader br = new BufferedReader(new FileReader(file));// 构造一个BufferedReader类来读取文件
			int tempchar;
			int k = 0;
			
			while ((tempchar = br.read()) != -1) {		
				
				if (((char) tempchar) != '\r' && ((char) tempchar) != '\n') {
					// result.append((char)tempchar);
					S[k] = (char) tempchar;
					k++;
				}
				// result.append((char)tempchar);
			}
			br.close();
			// System.out.println(Arrays.toString(S));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Mine the frequent single items
	 */
	void min_freItem() {
		Map<String, Integer> counter = new TreeMap<>();
		String mine;
		for (int t = 0; t < 1; t++) {
			// strcpy(S,sDB[t].S);
			for (int i = 0; i < S.length; i++) {
				if (((S[i] >= 'a' && S[i] <= 'z') || (S[i] >= 'A' && S[i] <= 'Z')) && !gapContain(gapstr, S[i])) {
					mine = String.valueOf(S[i]);
					if (counter.get(mine) == null) {
						counter.put(mine, 1);
					} else {
						counter.put(mine, counter.get(mine) + 1);
					}
				}
			}
		}
		Iterator<Entry<String, Integer>> the_iterator = counter.entrySet().iterator();
		while (the_iterator.hasNext()) {
			Map.Entry entry = the_iterator.next();
			hxms[0]++;
			if ((int) entry.getValue() < minsup) {
				// Partial deletion of an element
				the_iterator.remove();
			} else {
				freArr[0].add((String) entry.getKey()); // add to freArr[0]
				if (DEBUGMODE) {
					System.out.print(entry.getKey());
					System.out.println(" #SUP" + entry.getValue());
				}
				pw.write(String.valueOf(entry.getKey()));
				pw.write(" #SUP:" + entry.getValue() + "\n");
				num_occur += (int) entry.getValue();
				freNum++;
				pfms[0]++;
			}
		}

	}

	/**
	 * Determines whether a character is included in the if wildcard character set
	 * 
	 * @param p_gapstr wildcard character set
	 * @param c        character
	 * @return
	 */
	boolean gapContain(char p_gapstr[], char c) {
		int i = 0;
		while (i < p_gapstr.length) {
			// printf("哈哈%c", *p_gapstr);
			if (p_gapstr[i] == c) {
				// printf("666");
				return true;
			}
			i++;
		}
		return false;
	}

	/**
	 * Generate all possible matching patterns for the next level based on the
	 * matching pattern of the previous level's length and store them in the
	 * candidate (arrangement and combination)
	 * 
	 * @param level
	 */
	void gen_candidate(int level) {

//		System.out.println(level);
		if(freArr[level - 1] == null) {
			freArr[level - 1] = new ArrayList<>();
		}
		int size = freArr[level - 1].size();
		int start = 0;

		for (int i = 0; i < size; i++) {
			String Q = "", R = "";
			R = freArr[level - 1].get(i).substring(1, level); // suffix pattern of freArr[level-1][i]
			Q = freArr[level - 1].get(start).substring(0, level - 1); // prefix pattern of freArr[level-1][start]

			if (!Q.equals(R)) {
				start = binary_search(level, R, 0, size - 1);
			}
			if (start < 0 || start >= size) // if not exist, begin from the first
				start = 0;
			else {
				Q = freArr[level - 1].get(start).substring(0, level - 1);
				while (Q.equals(R)) {
					String cand = freArr[level - 1].get(i).substring(0, level);
					cand = cand + freArr[level - 1].get(start).substring(level - 1, level);
					candidate.add(cand);
					start = start + 1;
					if (start >= size) {
						start = 0;
						break;
					}
					Q = freArr[level - 1].get(start).substring(0, level - 1);
				}
			}
		}

		/*
		 * System.out.println("candidate遍历开始"); for (int iii = 0; iii <
		 * candidate.size(); iii++) { System.out.println(candidate.get(iii)); }
		 * System.out.println("candidate遍历结束");
		 */
	}

	// find the first position of cand in the level of freArr by binary search
	int binary_search(int level, String cand, int low, int high) {
		int mid, start;
		if (low > high) {
			return -1;
		}
		while (low <= high) {
			mid = (high + low) / 2;
			int result = cand.compareTo(freArr[level - 1].get(mid).substring(0, level - 1)); // To avoid multiple calls
																								// the same function
			if (result == 0) {
				// find start
				int slow = low;
				int shigh = mid;
				int flag = -1;
				if (cand.compareTo(freArr[level - 1].get(low).substring(0, level - 1)) == 0) {
					start = low;
				} else {
					while (slow < shigh) {

						start = (slow + shigh) / 2;
						int sresult = cand.compareTo(freArr[level - 1].get(start).substring(0, level - 1));
						if (sresult == 0) // Only two cases of ==0 and >0
						{
							shigh = start;
							flag = 0;
						} else {
							slow = start + 1;
						}
					}
					start = slow;
				}
				return start;
			} else if (result < 0) {
				high = mid - 1;
			} else {
				low = mid + 1;
			}
		}
		return -1;
	}

	// Obtain support for a certain pattern
	int getStore(char pattern[]) {
		int store = 0;

		pattern_len = pattern.length;
		S_len = S.length;
		// char p[M]="agct";
		init_bool();
		Creat_ptn(pattern, pattern_len);

		store = no_que(S_len, pattern_len);
		// cout<<"store:"<<store<<endl;
		return store;

	}

	// Fill S [i] in the queue of p corresponding to S
	int no_que(int len_s, int len_p) {

		int myAmount = 0;
		// cout<<"S_len:"<<S_len<<endl;
		// cout<<"pattern_len:"<<pattern_len<<endl;
		// cout<<"len_s:"<<len_s<<endl;
		// cout<<"len_p:"<<len_p<<endl;
		// cout<<link_pan[0].name<<endl;
		// cout<<link_pan[1].name<<endl;
		for (int k = 0; k < S_len; k++) {
			// cout<<"S[k]:"<<S[k]<<endl;

			// cout<<"bool_S[k]:"<<bool_S[k]<<endl;
			int postion = 0;
			for (int m = pattern_len - 1; m > 0; m--)//
			{
				if (S[k] == link_pan.get(m).name && bool_S[k] == true
						&& link_pan.get(m).que_pan.size() < link_pan.get(m - 1).que_pan.size()) {

					// Temporary queue, used to delete intermediate nodes in the previous queue
					Queue<Integer> temp_pan = new LinkedList<>();
					// 上一队列大小
					int lqueSize = link_pan.get(m - 1).que_pan.size();

					// Current queue size
					int queSize = link_pan.get(m).que_pan.size();
					boolean gapstr_flag = true;
					boolean forover = false;
					for (int z = 0; z < lqueSize; z++) {

						if (z >= queSize) {
							if (forover == false) {
								// Perform weak matching judgment
								gapstr_flag = gap_ok(link_pan.get(m - 1).que_pan.peek(), k);
								if (gapstr_flag == true) {// 满足
									temp_pan.add(link_pan.get(m - 1).que_pan.peek());
									link_pan.get(m - 1).que_pan.poll();
									forover = true;
									// break;

								} else {// Not satisfied
									link_pan.get(m - 1).que_pan.poll();

									int currLevel = m - 2;// Current queue level
									if (currLevel >= 0) {
										// Delete all nodes below the current column
										for (int hij = currLevel; hij >= 0; hij--) {
											Queue<Integer> temp_q = new LinkedList<>();
											int curr_q_size = link_pan.get(hij).que_pan.size();//Current queue size
											for (int ci = 0; ci < curr_q_size; ci++) {
												if (ci == queSize) {
													link_pan.get(hij).que_pan.poll();
												} else {
													temp_q.add(link_pan.get(hij).que_pan.peek());
													link_pan.get(hij).que_pan.poll();
												}
											}
											link_pan.get(hij).que_pan = temp_q;
										}
									}
								}
							} else {
								temp_pan.add(link_pan.get(m - 1).que_pan.peek());
								link_pan.get(m - 1).que_pan.poll();
							}

						} else {// Copy before node
							temp_pan.add(link_pan.get(m - 1).que_pan.peek());
							link_pan.get(m - 1).que_pan.poll();
						}

					}

					// Get new previous node
					link_pan.get(m - 1).que_pan = temp_pan;

					if (forover == true) {
						link_pan.get(m).que_pan.add(k);
						postion = m;

						// break;
					} else {
						postion = m - 1;
						// break;
					}

				}

			}
			if (/* postion==0&& */S[k] == link_pan.get(0).name && bool_S[k] == true) {
				link_pan.get(0).que_pan.add(k);
			}

			if (link_pan.get(link_pan.size() - 1).que_pan.size() > 0) {

				for (int w = 0; w < link_pan.size(); w++) {
					bool_S[link_pan.get(w).que_pan.peek()] = false;

					link_pan.get(w).que_pan.poll();

				}

				num_occur++;
				myAmount++;

				// 队列清空
				if (link_pan.get(0).que_pan.size() > 0) {
					k = link_pan.get(0).que_pan.peek() - 1;
					for (int hxn = 0; hxn < link_pan.size(); hxn++) {
						Queue<Integer> no_pan = new LinkedList<>();
						link_pan.get(hxn).que_pan = no_pan;

					}
				}

				// num_occur++;

			}
		}
		for (int hxn = 0; hxn < link_pan.size(); hxn++) {
			Queue<Integer> no_pan = new LinkedList<>();
			link_pan.get(hxn).que_pan = no_pan;
		}

		return myAmount;

	}

	/**
	 * Determine if weak compatibility is met
	 * 
	 * @param s_start
	 * @param s_end
	 * @return
	 */
	boolean gap_ok(int s_start, int s_end) {
		boolean re = true;
		for (int coop = s_start + 1; coop < s_end; coop++) {
			re = false;
			for (int gi = 0; gi < gapstr.length; gi++) {
				if (gapstr[gi] == S[coop]) {
					re = true;
					break;
				}
			}
			if (re == false) {
				break;
			}
		}
		return re;

	}

	/**
	 * Fill P.name in Vector
	 * 
	 * @param p
	 * @param len_p
	 */
	void Creat_ptn(char[] p, int len_p) {
		link_pan.clear();
		for (int i = 0; i < len_p; i++) {
			Pant_p pan = new Pant_p();
			// memset(&pan,0,sizeof(pan));
			pan.name = p[i];
			link_pan.add(pan);
		}
	}

	/**
	 * Initialize the usage status array to mark the usage status of each character
	 */
	void init_bool() {
		for (int i = 0; i < S_len; i++) {
			bool_S[i] = true;
		}
	}

	/**
	 * Run the algorithm
	 * 
	 * @throws IOException if error reading or writing to file
	 */
	public void runAlgorithm(String inputFile, String outputFile, char[] weakCharacterSet, int minsup, int sequenceLength) throws IOException {

		MemoryLogger.getInstance().reset();
		long startTime = System.currentTimeMillis();
		
		// Parameters
		this.gapstr = weakCharacterSet; 
		this.minsup = minsup;
		this.N = sequenceLength;
		
		// Initialization
		freArr = new ArrayList[10000];
		freArr[0] = new ArrayList<>();
		bool_S = new boolean[N];
		link_pan = new ArrayList<>();
		countNum = new ArrayList<>(); 
		candidate = new ArrayList<>();
		S = new char[N]; 
		hxms = new int[1000];
		pfms = new int[1000];

		// create output file
		File filename = new File(outputFile);
		if (!filename.exists()) {
			filename.createNewFile();
		}
		FileOutputStream fos = null;
		pw = null;
		fos = new FileOutputStream(filename);
		pw = new PrintWriter(fos);
		// Scanner in = new Scanner(System.in);
//		myPw = pw;
		read_file(inputFile);
		/*
		 * System.out.println(S); System.out.println(S.length);
		 * System.out.println("please input gapstr:");
		 */
		// String gstr = in.nextLine();
		// 若通配符，你可以自定义若通配符的值
		// gapstr = gstr.toCharArray();
		// System.out.println(gapstr);
		// System.out.println(gapstr.length);
		long dwBeginTime = System.currentTimeMillis();
		min_freItem();
		int f_level = 1;
		gen_candidate(f_level);
		while (candidate.size() != 0) {

			for (int ai = 0; ai < candidate.size(); ai++) {
				int h_occnum = 0; // the support num of pattern

				String patt = candidate.get(ai);
				// System.out.println("计算模式：" + patt);
				hxms[f_level]++;
				char[] pattc = new char[10000];
				// strcpy(pattc, patt.c_str());
				pattc = patt.toCharArray();
				compnum++;

				h_occnum += getStore(/* S, */ pattc);

				if (h_occnum >= minsup) {
					if(freArr[f_level] == null) {
						freArr[f_level] = new ArrayList<String>();
					}
					freArr[f_level].add(patt);
					countNum.add(h_occnum);
					// cout<<patt<<endl;
					if (DEBUGMODE) {
						System.out.print(patt);
						System.out.println(" #SUP:" + h_occnum);
					}
					pw.write(patt);
					pw.write(" #SUP:" + h_occnum + "\n");
					freNum++;
					pfms[f_level]++;
				}

				/*
				 * for(int t = 0; t < NumbS; t++) { rest=minsup-occnum; if(strlen(sDB[t].S) > 0)
				 * { strcpy(S,sDB[t].S); occnum += netGap(p,rest); } if(occnum >= minsup) {
				 * freArr[f_level].push_back(p); break; } }
				 */
			}
			f_level++;
			candidate.clear();
			gen_candidate(f_level);
		}

		MemoryLogger.getInstance().checkMemory();

		long dwEndTime = startTime - System.currentTimeMillis();

		if (DEBUGMODE) {
			System.out.println("The number of frequent patterns:" + freNum);
			System.out.println("The number of calculation:" + compnum);
			System.out.println("Time cost:" + (dwEndTime - dwBeginTime));
		}
		runtime = (dwEndTime - dwBeginTime);
		maxMemory = MemoryLogger.getInstance().getMaxMemory();

		pw.close();
		fos.close();
	}

	/**
	 * Print stats about the last algorithm execution
	 */
	public void printStats() {
		System.out.println("=============  OWSPMiner v2.60 - STATS =============");
		System.out.println(" Number of patterns found: " + freNum);
		System.out.println(" Number of calcuations: " + compnum);
		System.out.println(" Total time ~ " + runtime + " ms");
		System.out.println(" Maximum memory usage : " + maxMemory + " mb");
		System.out.println("===================================================");
	}
}
