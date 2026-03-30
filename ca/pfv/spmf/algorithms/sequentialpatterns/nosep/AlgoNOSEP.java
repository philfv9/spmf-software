package ca.pfv.spmf.algorithms.sequentialpatterns.nosep;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
 * Implementation of the NOSEP algorithm. It was converted from C++ to Java.
 * 
 * @author Youxi Wu et al.
 */
public class AlgoNOSEP {

	/** if true, additional debugging information is output to the console */
	static final boolean DEBUGMODE = false;
	
	/** runtime of last execution */
	long runtime;
	
	/** memory of last execution */
	double maxMemory;

	/** the number of sequences */
	int sequenceCount;
	
	/** structure to store candidates */
	List<IInt> candidate = new ArrayList<IInt>();

	/** store*/
	int store;

	/** the minimum length */
	int minlen;

	/** the maximum length */
	int maxlen;

	/** the minimum gap */
	int mingap;

	/** the maximum gap */
	int maxgap;

	/** the minimum support */
	int minsup;

	/** ptn length */
	int ptnLen;

	/** Computation count */
	int computationCount;

	/** frequent pattern count */
	int frequentPatternCount;

	/** the current sequence */
	SeqDB curS;

	/** the sequence database */
	List<SeqDB> sDB;

	/** sub ptn */
	List<SubPTNStruct> subPtn;

	/** store frequent patterns */
	List<List<FreIInt>> freArr;

	/** Inner class representing a node */
	class Node {
		/** The corresponding position of node in sequence */
		int name;

		/** The position of mininum leaf node */
		int minLeave;

		/** The position of maxinum leaf node */
		int maxLeave;

		/** The position set of parents */
		List<Integer> parent = new ArrayList<>();

		/** The position set of children */
		List<Integer> children = new ArrayList<>();

		/** true is has used, false is has not used */
		boolean used = false;

		/** true is can reach leaves, false is not */
		boolean toleave = false;
	}

	/**
	 * Class representing a sequence database as used by NOSEP
	 */
	class SeqDB {
		/** the length of the sequence */
		int len; 
		/** sequence */
		List<Integer> s = new ArrayList<>();
		public int getLen() {
			return len;
		}
		public void setLen(int len) {
			this.len = len;
		}
		public List<Integer> getS() {
			return s;
		}
		public void setS(List<Integer> s) {
			this.s = s;
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + len;
			result = prime * result + ((s == null) ? 0 : s.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SeqDB other = (SeqDB) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (len != other.len)
				return false;
			if (s == null) {
				if (other.s != null)
					return false;
			} else if (!s.equals(other.s))
				return false;
			return true;
		}
		private AlgoNOSEP getOuterType() {
			return AlgoNOSEP.this;
		}
		
	}

	/**
	 * The Class IInt
	 */
	class IInt implements Comparable<IInt> {

		List<Integer> store = new ArrayList<>();
		int length = 0;

		public IInt() {
			store = new ArrayList<>();
			length = 0;
		}

		public IInt(List<Integer> store,int length) {
			this.store = store;
			this.length = length;
		}
		
		public List<Integer> getStore() {
			return store;
		}

		public void setStore(List<Integer> store) {
			this.store = store;
		}

		public int getLength() {
			return length;
		}

		public void setLength(int length) {
			this.length = length;
		}
		

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + length;
			result = prime * result + ((store == null) ? 0 : store.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			IInt other = (IInt) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (length != other.length)
				return false;
			if (store == null) {
				if (other.store != null)
					return false;
			} else if (!store.equals(other.store))
				return false;
			return true;
		}

		@Override
		public int compareTo(IInt another) {
			int i = 0;
		
			for (i = 0; i < this.length; i++) {
				if (!this.store.get(i).equals(another.store.get(i)))
					break;
			}
			if (i == this.length && i == another.length)
				return 0;
			else if (i == this.length)
				return -1;
			else if (store.get(i) > another.store.get(i))
				return 1;
			else
				return -1;
		}

		boolean lessTo(IInt ls, IInt rs) {
			for (int i = 0; i < (ls.length < rs.length ? ls.length : rs.length); i++)
				if (ls.store.get(i) < rs.store.get(i))
					return true;
			return (ls.length < rs.length) ? true : false;

		}

		public IInt addTo(int value) {
			store.add(length, value);
			length++;
			return this;
		}

		IInt equalTo(IInt another) {
			length = another.length;
			for (int i = 0; i < length; i++) {
				store.add(i, another.store.get(i));
			}
			return this;
		}

		int section(int position) {
			return store.get(position);
		}

		IInt evaluation(int value) {
			length = 1;
			store.add(0, value);
			return this;
		}

		boolean notEqual(IInt another) {
			return !this.equals(another);
		}

		public IInt substr(int start, int len) {
			IInt a = new IInt();
			a.length = len;
			for (int i = 0; i < len; i++) {
				a.store.add(i, this.store.get(i+start));
			}
			return a;
		}

		void display(StringBuilder builder) {
			for (int i = 0; i < (length - 1); i++)
				builder.append(store.get(i) + " -1 ");
			builder.append(store.get(length-1) + System.lineSeparator());
		}
		
		private AlgoNOSEP getOuterType() {
			return AlgoNOSEP.this;
		}

	}
	
	class FreIInt{
		IInt iiInt = new IInt();
		int support;
		public FreIInt(IInt iiInt,int support) {
			this.iiInt = iiInt;
			this.support = support;
		}
		public void display(StringBuilder builder) {
			for (int i = 0; i < (iiInt.length - 1); i++)
				builder.append(iiInt.store.get(i) + " -1 ");
			builder.append(iiInt.store.get(iiInt.length-1) +" -1 #SUP: "+this.support+ System.lineSeparator());
		}
		
	}

	/** Class representing an occurrence */
	class Occurrence {
		List<Integer> position = new ArrayList<>();
	}

	/** Class representing a start-end min-max structure */
	class SubPTNStruct { // a[0,3]c => start[min,max]end
		// char start,end;
		int start;
		int end;
		int min;
		int max;
	}

	/**
	 * find the first position of cand in the level of freArr by binary search
	 * 
	 * @param level
	 * @param cand
	 * @param low
	 * @param high
	 * @return
	 */
	int binarySearch(int level, IInt cand, int low, int high) {
		int mid;
		int start;
		if (low > high) {
			return -1;
		}
		while (low <= high) {
			mid = (high + low) / 2;
			int result = cand.compareTo(freArr.get(level - 1).get(mid).iiInt.substr(0, level - 1)); // To avoid multiple calls the
																							// same function
			if (result == 0) {
				// find start
				int slow = low;
				int shigh = mid;
//				int flag = -1;
				if (cand.compareTo(freArr.get(level - 1).get(low).iiInt.substr(0, level - 1)) == 0) {
					start = low;
				} else {
					while (slow < shigh) {

						start = (slow + shigh) / 2;
						int sresult = cand.compareTo(freArr.get(level - 1).get(start).iiInt.substr(0, level - 1));
						if (sresult == 0) // Only two cases of ==0 and >0
						{
							shigh = start;
//							flag = 0;
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

	/**
	 * Generate candidates
	 * 
	 * @param level the level of candidates
	 */
	void generateCandidate(int level) {
		int size = 0;
		if (freArr.size() > (level - 1)) {
			size = freArr.get(level - 1).size();
		}
		int start = 0;
		candidate = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			IInt r = new IInt();
			IInt q = new IInt();
			r.equalTo(freArr.get(level - 1).get(i).iiInt.substr(1, level - 1)); // suffix pattern of freArr[level-1][i]
			q.equalTo(freArr.get(level - 1).get(start).iiInt.substr(0, level - 1)); // prefix pattern of freArr[level-1][start]
			if (q.notEqual(r)) {
				start = binarySearch(level, r, 0, size - 1);
			}
			if (start < 0 || start >= size) // if not exist, begin from the first
				start = 0;
			else {
				q = new IInt();
				q.equalTo(freArr.get(level - 1).get(start).iiInt.substr(0, level - 1));
				while (q.equals(r)) {
					IInt cand = new IInt(); // special
					cand.equalTo(freArr.get(level - 1).get(i).iiInt.substr(0, level));
					int value = freArr.get(level - 1).get(start).iiInt.store.get(level-1); // special
					cand.addTo(value);
					candidate.add(cand);
					start = start + 1;
					if (start >= size) {
						start = 0;
						break;
					}
					q = new IInt();
					q.equalTo(freArr.get(level - 1).get(start).iiInt.substr(0, level - 1));
				}
			}
		}
	}

	/**
	 * Mine frequent items
	 */
	void mineFrequentItems() {
		Map<IInt, Integer> counter = new LinkedHashMap<>();
		for (int t = 0; t < sequenceCount; t++) {
			List<Integer> list = new ArrayList<>();
			curS = sDB.get(t);
			for (Integer integer : curS.s) {
				list.add(integer);
			}
			for (int i = 0; i < list.size(); i++) {
				int sss = list.get(i);
				List<Integer> ll = new ArrayList<>();
				ll.add(sss);
				IInt mine = new IInt(ll,1);
//				mine.length = 1;
//				mine.store.add(curS.s.get(i));
				if (counter.get(mine) == null) {
					counter.put(mine, 1);
				} else {
					counter.put(mine, counter.get(mine) + 1);
				}
			}
		}
		
		
		ArrayList<Entry<IInt, Integer>> entryArrayList = new ArrayList<>(counter.entrySet());

        Collections.sort(entryArrayList, new Comparator<Map.Entry<IInt, Integer>>() {
            @Override
            public int compare(Map.Entry<IInt, Integer> o1, Map.Entry<IInt, Integer> o2) {
                return o1.getKey().store.get(0)-o2.getKey().store.get(0);//正序
            }
        });

		for (Map.Entry<IInt, Integer> entry : entryArrayList) {
			if (entry.getValue() >= minsup) {
				IInt cand = new IInt();
				cand.equalTo(entry.getKey());
				if (freArr == null || freArr.size() <1) {
					List<FreIInt> llList = new ArrayList<>();
					FreIInt freIInt = new FreIInt(cand, entry.getValue());
					llList.add(freIInt);
					freArr.add(llList);
				}else {
					FreIInt freIInt = new FreIInt(cand, entry.getValue());
					freArr.get(0).add(freIInt); // add to freArr[0]
				}
			}
		}
		
	}
	

	/**
	 * Create a NetTree
	 * 
	 * @param nettree a ArrayList for storing the net tree
	 */
	void createNetTree(List<Node>[] nettree) {
		for (int i = 0; i < (ptnLen + 1); i++)
			nettree[i].clear(); // initialize nettree
		int[] start;
		start = new int[ptnLen + 1];
		for (int i = 0; i < (ptnLen + 1); i++)
			start[i] = 0;
		for (int i = 0; i < curS.len; i++) {
			Node anode = new Node();
			anode.name = i;
			anode.parent.clear();
			anode.children.clear();
			anode.maxLeave = anode.name;
			anode.minLeave = anode.name;
			anode.used = false;
			// store root
			if (subPtn.get(0).start == curS.s.get(i)) {
				int len = nettree[0].size();
				while (nettree[0].size() < (len + 1)) {
					nettree[0].add(new Node());
				}
				anode.toleave = true;
				nettree[0].set(len, anode);

			}

			for (int j = 0; j < ptnLen; j++) {
				if (subPtn.get(j).end == curS.s.get(i)) {
					// Look for parents from the layer above.
					int prevLength = nettree[j].size();
					if (prevLength == 0) {
						break;
					}
					// update start
					for (int k = start[j]; k < prevLength; k++) {
						if ((i - nettree[j].get(k).name - 1) > subPtn.get(j).max) {
							start[j]++; // greater than max, cursor moves rearward
						}
					}
					// compare gap constraint
					if ((i - nettree[j].get(prevLength - 1).name - 1) > subPtn.get(j).max) {
						continue;
					}
					if ((i - nettree[j].get(start[j]).name - 1) < subPtn.get(j).min) {
						continue;
					}

					int len = nettree[j + 1].size();
					while (nettree[j + 1].size() < (len + 1)) {
						nettree[j + 1].add(new Node());
					}
					Node anode1 = new Node();
					anode1.name = i;
					anode1.parent.clear();
					anode1.children.clear();
					anode1.maxLeave = anode.name;
					anode1.minLeave = anode.name;
					anode1.used = false;
					anode1.toleave = true;
					anode = anode1;
					nettree[j + 1].set(len, anode);

					for (int k = start[j]; k < prevLength; k++) {
						if ((i - nettree[j].get(k).name - 1) < subPtn.get(j).min) {
							break;
						}
						// Meet gap constraint
						// builds the relationship between father and son
						int nc = nettree[j].get(k).children.size();
						while (nettree[j].get(k).children.size() < (nc + 1)) {
							nettree[j].get(k).children.add(0);
						}
						nettree[j].get(k).children.set(nc, len);

						int np = nettree[j + 1].get(len).parent.size();
						while (nettree[j + 1].get(len).parent.size() < (np + 1)) {
							nettree[j + 1].get(len).parent.add(0);
						}
						nettree[j + 1].get(len).parent.set(np, k);

					}
				}
			}
		}

	}

	/**
	 * Update the NetTree
	 * 
	 * @param nettree an array of ArrayList of nodes
	 */
	void updateNetTree(List<Node>[] nettree) {
		for (int i = (ptnLen - 1); i >= 0; i--) {
			for (int j = (nettree[i].size() - 1); j >= 0; j--) {
				boolean flag = true;
				int size = nettree[i].get(j).children.size();
				for (int k = 0; k < size; k++) {
					int child = nettree[i].get(j).children.get(k);
					if (k == 0) {
						nettree[i].get(j).minLeave = nettree[i + 1].get(child).minLeave;
					}
					if (k == (size - 1)) {
						nettree[i].get(j).maxLeave = nettree[i + 1].get(child).maxLeave;
					}
					if (nettree[i + 1].get(child).used == false) {
						flag = false;
					}
				}
				// For nodes that do not arrive at leave,marking for the used=true
				nettree[i].get(j).used = flag;
				if (flag == true) {
					nettree[i].get(j).maxLeave = nettree[i].get(j).name;
					nettree[i].get(j).minLeave = nettree[i].get(j).name;
					nettree[i].get(j).toleave = false;
				}
			}
		}
	}

	/**
	 * Update a NetTree
	 * 
	 * @param nettree the NetTree
	 * @param occin   An occurrence
	 */
	void updateNetTreePC(List<Node>[] nettree, Occurrence occin) {
		// The advantage of the algorithm is do not have to traverse the entire nettree
		// and just set the affected node as line way
		for (int level = ptnLen; level > 0; level--) {
			int position = occin.position.get(level);
			int num = nettree[level].size();
			for (; position < num; position++) {
				// find a node that is not used backwards and break
				if (nettree[level].get(position).used == false)
					break;
				// the number of parents
				int len = nettree[level].get(position).parent.size();
				// int name=nettree[level][position].name ;
				for (int i = 0; i < len; i++) {
					int parent = nettree[level].get(position).parent.get(i);
					/*
					 * if(parent >= nettree[level-1].size()){ continue; }
					 */
					int cs = nettree[level - 1].get(parent).children.size();
					// parent node have been used or cannot reach leaf node
					if (nettree[level - 1].get(parent).used == true)
						continue;
					if (cs == 1) // one child
					{
						nettree[level - 1].get(parent).used = true;
						nettree[level - 1].get(parent).toleave = false;
					} else {
						int kk = 0;
						for (; kk < cs; kk++) {
							int child = nettree[level - 1].get(parent).children.get(kk);
							/*
							 * if(child >= nettree[level].size()){ continue; }
							 */
							if (nettree[level].get(child).used == false)
								break;
						}
						if (kk == cs) {
							nettree[level - 1].get(parent).used = true;
							nettree[level - 1].get(parent).toleave = false;
						}
					}
				}
			}
		}
	}

	/**
	 * Non-over length
	 * 
	 * @param rest the rest
	 */
	void nonoverlength(int rest) {
		List<Node>[] nettree = new ArrayList[ptnLen + 1];
		for (int i = 0; i < nettree.length; i++) {
			if (nettree[i] == null) {
				nettree[i] = new ArrayList<Node>();
			}
		}
		createNetTree(nettree);
		updateNetTree(nettree);
		store = 0;
		for (int position = 0; position < nettree[0].size(); position++) {
			if (nettree[0].get(position).toleave == false) {
				// false is cannot reach root
				continue;
			}
			int root = nettree[0].get(position).name;
			int a = nettree[0].get(position).maxLeave - root + 1;
			int b = nettree[0].get(position).minLeave - root + 1;
			if (!(( a <= maxlen) || (b <= maxlen))) // THIS LINE MODIFIED BY PHILIPPE TO FIX MINLEN BUG - 2024-17
			{
				nettree[0].get(position).used = true;
				nettree[0].get(position).toleave = false;
				continue;
			}
			Occurrence occin = new Occurrence();
			// occin.position.resize(ptn_len+1);
			while (occin.position.size() < (ptnLen + 1)) {
				occin.position.add(0);
			}
			occin.position.set(0, position);
			nettree[0].get(position).used = true;
			nettree[0].get(position).toleave = false;
			// Looking down for the most left child
			int j = 1;
			for (; j < (ptnLen + 1); j++) {
				int parent = occin.position.get(j - 1); // The position of the parent in nettree
				int cs = nettree[j - 1].get(parent).children.size(); // The number of children of the current node
				int t = 0;
				for (; t < cs; t++) {
					int child = nettree[j - 1].get(parent).children.get(t); // The position of the most left child
					/*
					 * if(child >= nettree[j].size()){ continue; }
					 */
					int a1 = nettree[j].get(child).maxLeave - root + 1;
					int b1 = nettree[j].get(child).minLeave - root + 1;
					if (nettree[j].get(child).used == false
							&& ((a1 <= maxlen) || (b1 <= maxlen))) { //THIS LINE MODIFIED BY PHILIPPE TO FIX MINLEN BUG - 2024-17
						occin.position.set(j, child); //
//						int value = nettree[j].get(child).name;
						nettree[j].get(child).used = true;
						nettree[j].get(child).toleave = false;
						break;
					}
				}
				if (t == cs) {
					for (int kk = 0; kk < j; kk++) {
						int pos = occin.position.get(kk);
						nettree[kk].get(pos).used = false;
						nettree[kk].get(pos).toleave = true;
					}
					break;
				}
			}
			if (j == (ptnLen + 1)) {
				store++;
				if (store > rest) {
					return;
				}
				updateNetTreePC(nettree, occin);
			}
			// memset(&occin,0,sizeof(occin));
			for (int i = 0; i < occin.position.size(); i++) {
				occin.position.set(i, 0);
			}
		}
	}

	/**
	 * Compute the support NetGap
	 * 
	 * @param p    the parameter p
	 * @param rest the parameter rest
	 * @return and integer
	 */
	int netGap(IInt p, int rest) {
		ptnLen = 0;
		subPtn = new ArrayList<>();
		for (int i = 0; i < (p.length - 1); i++) {
			SubPTNStruct ptn = new SubPTNStruct();
			ptn.start = p.store.get(i);
			ptn.end = p.store.get(i+1);
			ptn.min = mingap;
			ptn.max = maxgap;
			subPtn.add(ptnLen, ptn);
			
			ptnLen++;
		}

		if ((ptnLen + 1) > curS.len) {
			return 0;
		}
		nonoverlength(rest);
		return store;
	}

	/**
	 * Read the input file
	 * 
	 * @param filePath the input file path
	 */
	void readInputFile(String filePath) {
		try {
			File file = new File(filePath);
			BufferedReader br = new BufferedReader(new FileReader(file));// 构造一个BufferedReader类来读取文件

			int j = 0;
			int max = 0;
			int lines = 0;
			String buffer = null;
			while ((buffer = br.readLine()) != null ) {
				 // if the line is  a comment, is  empty or is a
                // kind of metadata
                if (buffer.isEmpty() == true
                        || buffer.charAt(0) == '#' || buffer.charAt(0) == '%'
                        || buffer.charAt(0) == '@') {
                    continue;
                }

				String[] valueStr = buffer.trim().split(" -1 ");
				
				List<Integer> sTemp = new ArrayList<>();
				for (j = 0; j < (valueStr.length - 1); j++) {
					String ssString = valueStr[j];
					int aaa = Integer.parseInt(ssString);
					
					sTemp.add(aaa);
//					sDB.get(lines).s.set(j, aaa);
				}
				SeqDB seq = new SeqDB();
				seq.s = sTemp;
				sDB.add(seq);
				sDB.get(lines).len = j;
				if (max < j) {
					max = j;
				}
				lines++;
//				if (lines == 77512) {
//					break;
//				}
				
			}
			br.close();

			sequenceCount = lines;
			if (DEBUGMODE) {
				System.out.println("max: " + max + "\t lines: " + lines);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Run the algorithm
	 * 
	 * @param filePath   the input file path
	 * @param outputPath the output file path
	 * @param minlen     the minimum length
	 * @param maxlen     the maximum length
	 * @param mingap     the minimum gap
	 * @param maxgap     the maximum gap
	 * @param minsup     the minimum support
	 * @throws IOException if error writing to file
	 */
	public void runAlgorithm(String filePath, String outputPath, int minlen2, int maxlen2, int mingap2, int maxgap2,
			int minsup2) throws IOException {

		runtime = System.currentTimeMillis();
		MemoryLogger.getInstance().reset();
		minlen = minlen2;
		maxlen = maxlen2;
		mingap = mingap2;
		maxgap = maxgap2;
		minsup = minsup2;
		if (DEBUGMODE) {
			System.out.println("minlen:" + minlen + " , maxlen:" + maxlen);
			System.out.println("mingap:" + mingap + " , maxgap:" + maxgap);
			System.out.println("minsup:" + minsup);
		}

		computationCount = 0;
		frequentPatternCount = 0;
		sequenceCount = 0;

		// Readint the input file
		curS = new SeqDB();
		sDB = new ArrayList<SeqDB>();

		subPtn = new ArrayList<SubPTNStruct>(); // pattern p[i]
		
		freArr = new ArrayList<>(); // store frequent patterns

		readInputFile(filePath);

		mineFrequentItems();
		int fLevel = 1;
		generateCandidate(fLevel);

		while (candidate.size() != 0) {

			for (int i = 0; i < candidate.size(); i++) {
				int occnum = 0; // the support num of pattern
				int rest = 0;

				IInt p = new IInt();
				p.equalTo(candidate.get(i));
				computationCount++;
				for (int t = 0; t < sequenceCount; t++) {
					rest = minsup - occnum;
					// if(strlen(sDB[t].S) > 0)
					if (sDB.get(t).len > 0) {
						// strcpy(S,sDB[t].S);
						curS = sDB.get(t);
						occnum += netGap(p, rest);// 超过rest就返回
					}
					
				}
				if (occnum >= minsup) {
					if (freArr.size() < (fLevel + 1)) {
						List<FreIInt> pTemp = new ArrayList<>();
						FreIInt freIInt = new FreIInt(p, occnum);
						pTemp.add(freIInt);
						freArr.add(pTemp);
					} else {
						FreIInt freIInt = new FreIInt(p, occnum);
						freArr.get(fLevel).add(freIInt);
					}
//					break;
				}
			}
			
			fLevel++;
			candidate.clear();
			generateCandidate(fLevel);
			
		}

		// Writer results to the output file
		BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputPath)));
		StringBuilder builder = new StringBuilder();

		for (int i = minlen-1; i < fLevel; i++) {  //  THIS LINE MODIFIED BY PHILIPPE TO FIX MINLEN BUG - 2024-17
			if (freArr.size() > i) {
				
				for (int j = 0; j < freArr.get(i).size(); j++) {
					freArr.get(i).get(j).display(builder);
//					System.out.println("i = "+ i  + "j = " + j);
//					System.out.println(builder.toString());
//				System.out.print(":"+freArr.get(i).get(j).support+"\t");
					frequentPatternCount++;
				}
			}
		}
		writer.write(builder.toString());
		writer.close();

		runtime = System.currentTimeMillis() - runtime;
		MemoryLogger.getInstance().checkMemory();
		maxMemory = MemoryLogger.getInstance().getMaxMemory();
	}

	/**
	 * Print stats about the last algorithm execution
	 */
	public void printStats() {
		System.out.println("=============  NOSEP v2.46 - STATS =============");
		System.out.println(" Number of patterns found: " + frequentPatternCount);
		System.out.println(" Total time ~ " + runtime + " ms");
		System.out.println(" Maximum memory usage : " + maxMemory + " mb");
		System.out.println(" Calculation count " + computationCount);
		System.out.println("===================================================");
	}

}
