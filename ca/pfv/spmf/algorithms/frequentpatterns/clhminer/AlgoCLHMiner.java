package ca.pfv.spmf.algorithms.frequentpatterns.clhminer;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * Implementation of the CLH-Miner algorithm by Bay Vo et al.<br/>
 * <br/>
 * <br/>
 * 
 * The CLH-Miner algorithm was originally presented in this paper:<br/>
 * <br/>
 * <br/>
 * 
 * Fournier-Viger, P., Yang, Y., Lin, J. C.-W., Luna, J. M., Ventura, S. (2020).
 * Mining Cross-Level High Utility Itemsets. Proc. 33rd Intern. Conf. on
 * Industrial, Engineering and Other Applications of Applied Intelligent Systems
 * (IEA AIE 2020), Springer LNAI, pp. 858-871.
 * 
 * @author Bay Vo et al.
 */
public class AlgoCLHMiner {
	/** minimum utility */
	int minUtil;
	/** the utility lists */
	List<UtilityList> ListUls;
	/** the item count */
	int itemCount = 0;
	/** the generalized item count */
	int giCount = 0;
	/** the taxonomy depth */
	int taxDepth = 0;

	/** a map of item to utility list */
	static Map<Integer, UtilityList> mapItemToUtilityList;
	
	/** the start timestamp */
	long startTimestamp = 0;
	
	/** A map of item to TWU */
	Map<Integer, Double> mapItemToTWU;
	
	/** the time at which the algorithm ended */
	long endTimestamp = 0;
	
	/** the taxonomy */
	TaxonomyTree taxonomy;
	
	/** a buffer for an itemset */
	private int[] itemsetBuffer = null;
	
	/** the list of revised transactions */
	List<Pair> revisedTransaction;
	
	/** the dataset after removal */
	List<List<Pair>> datasetAfterRemove;
	
	/** the number of patterns found */
	int countHUI;
	
	/** the number of candidates */
	int candidate;
	
	/** Writer to write result to the output file */
	BufferedWriter writer;

	/**
	 * A pair of an item and a utility value
	 */
	class Pair {
		int item = 0;
		double utility = 0;
	}

	/**
	 * Run the algorithm
	 * @param minUtil minimum utility threshold
	 * @param inputPath an input file path 
	 * @param outputPath an output file path
	 * @param TaxonomyPath the path to a file containing a taxonomy
	 * @throws IOException if error when reading or writing to file
	 */
	public void runAlgorithm(int minUtil, String inputPath, String outputPath, String TaxonomyPath) throws IOException {

		writer = new BufferedWriter(new FileWriter(outputPath));

		this.minUtil = minUtil;
		candidate = 0;
		startTimestamp = System.currentTimeMillis();
		mapItemToTWU = new HashMap<Integer, Double>();
		taxonomy = new TaxonomyTree();
		taxonomy.ReadDataFromPath(TaxonomyPath);
		BufferedReader myInput = null;
		itemsetBuffer = new int[500];
		datasetAfterRemove = new ArrayList<List<Pair>>();
		countHUI = 0;
		Set<Integer> itemInDB = new HashSet<Integer>();
		String thisLine;

		// prepare the object for reading the file
		myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(inputPath))));
		// for each line (transaction) until the end of file
		while ((thisLine = myInput.readLine()) != null) {
			// if the line is a comment, is empty or is a
			// kind of metadata
			if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
					|| thisLine.charAt(0) == '@') {
				continue;
			}

			// split the transaction according to the : separator
			String split[] = thisLine.split(":");
			// the first part is the list of items
			String items[] = split[0].split(" ");
			// the second part is the transaction utility
			double transactionUtility = Double.parseDouble(split[1]);
			HashSet<Integer> setParent = new HashSet<Integer>();
			// for each item, we add the transaction utility to its TWU
			for (int i = 0; i < items.length; i++) {
				// convert item to integer
				Integer item = Integer.parseInt(items[i]);
				itemInDB.add(item);
				if (taxonomy.mapItemToTaxonomyNode.get(item) == null) {
					TaxonomyNode newNode = new TaxonomyNode(item);
					taxonomy.mapItemToTaxonomyNode.get(-1).addChildren(newNode);
					taxonomy.mapItemToTaxonomyNode.put(item, newNode);
				} else {
					TaxonomyNode parentNode = taxonomy.mapItemToTaxonomyNode.get(item).getParent();
					while (parentNode.getData() != -1) {
						setParent.add(parentNode.getData());
						parentNode = parentNode.getParent();
					}
				}

				// get the current TWU of that item
				Double twu = mapItemToTWU.get(item);
				// add the utility of the item in the current transaction to its twu
				twu = (twu == null) ? transactionUtility : twu + transactionUtility;
				mapItemToTWU.put(item, twu);
			}
			for (Integer parentItemInTransaction : setParent) {
				Double twu = mapItemToTWU.get(parentItemInTransaction);
				twu = (twu == null) ? transactionUtility : twu + transactionUtility;
				mapItemToTWU.put(parentItemInTransaction, twu);
			}
		}
		List<UtilityList> listOfUtilityLists = new ArrayList<UtilityList>();
		mapItemToUtilityList = new HashMap<Integer, UtilityList>();

		// For each item
		for (Integer item : mapItemToTWU.keySet()) {
			// if the item is promising (TWU >= minutility)
			if (mapItemToTWU.get(item) >= minUtil) {
				// create an empty Utility List that we will fill later.
				UtilityList uList = new UtilityList(item);
				mapItemToUtilityList.put(item, uList);
				// add the item to the list of high TWU items
				listOfUtilityLists.add(uList);

			}
		}

		Collections.sort(listOfUtilityLists, new Comparator<UtilityList>() {
			public int compare(UtilityList o1, UtilityList o2) {
				// compare the TWU of the items
				return compareItems(o1.item, o2.item);
			}
		});
		myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(inputPath))));
		int tid = 0;
		while ((thisLine = myInput.readLine()) != null) {
			// if the line is a comment, is empty or is a
			// kind of metadata
			if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
					|| thisLine.charAt(0) == '@') {
				continue;
			}
			String split[] = thisLine.split(":");
			// get the list of items
			String items[] = split[0].split(" ");
			// get the list of utility values corresponding to each item
			// for that transaction
			String utilityValues[] = split[2].split(" ");

			// Copy the transaction into lists but
			// without items with TWU < minutility

			double remainingUtility = 0;
			// long newTWU = 0; // NEW OPTIMIZATION
			double TU = Double.parseDouble(split[1]);
			// Create a list to store items
			List<Pair> revisedTransaction = new ArrayList<Pair>();
			// for each item

			HashMap<Integer, Double> mapParentToUtility = new HashMap<Integer, Double>();

			for (int i = 0; i < items.length; i++) {
				Double Utiliy = Double.parseDouble(utilityValues[i]);
				int item = Integer.parseInt(items[i]);
				TaxonomyNode nodeParent = taxonomy.mapItemToTaxonomyNode.get(item).getParent();
				while (nodeParent.getData() != -1) {
					Double utilityOfParent = mapParentToUtility.get(nodeParent.getData());
					if (utilityOfParent != null) {
						mapParentToUtility.put(nodeParent.getData(), utilityOfParent + Utiliy);
					} else {
						mapParentToUtility.put(nodeParent.getData(), Utiliy);
					}
					nodeParent = nodeParent.getParent();
				}
				Pair pair = new Pair();
				pair.item = item;
				pair.utility = Utiliy;
				if (mapItemToTWU.get(pair.item) >= minUtil) {
					revisedTransaction.add(pair);
					remainingUtility += pair.utility;
					// newTWU+=pair.utility;
				}
			}
			Collections.sort(revisedTransaction, new Comparator<Pair>() {
				public int compare(Pair o1, Pair o2) {
					return compareItems(o1.item, o2.item);
				}
			});
			double CountUtility = remainingUtility;
			for (int i = 0; i < revisedTransaction.size(); i++) {
				Pair pair = revisedTransaction.get(i);
				remainingUtility = remainingUtility - pair.utility;
				UtilityList utilityListOfItem = mapItemToUtilityList.get(pair.item);
				Element element = new Element(tid, pair.utility, remainingUtility, TU);
				utilityListOfItem.addElement(element);
			}
			for (Integer itemParent : mapParentToUtility.keySet()) {
				double CountUtilityOfEachItem = CountUtility;
				for (int i = 0; i < revisedTransaction.size(); i++) {
					Pair CurrentItem = revisedTransaction.get(i);
					if (CheckParent(itemParent, CurrentItem.item)) {
						CountUtilityOfEachItem -= CurrentItem.utility;
					} else {
						if (compareItems(itemParent, CurrentItem.item) > 0) {
							CountUtilityOfEachItem -= CurrentItem.utility;
						}
					}
				}
				UtilityList utilityListOfItem = mapItemToUtilityList.get(itemParent);
				if (utilityListOfItem != null) {
					Element element = new Element(tid, mapParentToUtility.get(itemParent), CountUtilityOfEachItem, TU);
					utilityListOfItem.addElement(element);
				}

			}
			datasetAfterRemove.add(revisedTransaction);
			tid++;
		}

		List<UtilityList> listUtilityLevel1 = new ArrayList<UtilityList>();
		for (UtilityList ul1 : listOfUtilityLists) {
			if (taxonomy.getMapItemToTaxonomyNode().get(ul1.item).getLevel() == 1) {
				listUtilityLevel1.add(ul1);
			}
			if (taxonomy.getMapItemToTaxonomyNode().get(ul1.item).getLevel() > 1) {
				break;
			}
		}

		itemCount = itemInDB.size();
		giCount = taxonomy.getGI() - 1;
		taxDepth = taxonomy.getMaxLevel();

		SearchTree(itemsetBuffer, 0, null, listUtilityLevel1);
		endTimestamp = System.currentTimeMillis();
		myInput.close();
		writer.close();

	}

	/**
	 * Depth first search
	 * @param prefix the prefix of the current itemset
	 * @param prefixLength the length of the prefix
	 * @param pUL the prefix's utility list
	 * @param ULs the utility lists of some extensions of the prefix
	 * @throws IOException 
	 */
	private void SearchTree(int[] prefix, int prefixLength, UtilityList pUL, List<UtilityList> ULs) throws IOException {
		for (int i = 0; i < ULs.size(); i++) {
			UtilityList X = ULs.get(i);
			candidate++;
			if (X.sumIutils > minUtil) {
				countHUI++;
				/*
				 * for (int j = 0; j < prefixLength; j++) { System.out.print(prefix[j]+" "); }
				 */
				for(int j = 0; j<prefixLength; j++) {
					writer.write(prefix[j] + " ");
				}
				writer.write(X.item + " #UTIL: " + X.sumIutils);
				writer.newLine();
			}
			List<UtilityList> exULs = new ArrayList<UtilityList>();
			for (int j = i + 1; j < ULs.size(); j++) {
				UtilityList Y = ULs.get(j);
				if (!CheckParent(Y.item, X.item)) {
					UtilityList exULBuild = construct(pUL, X, Y);
					if (exULBuild.GWU > minUtil) {
						exULs.add(exULBuild);
					}
				}
			}
			if (X.sumIutils + X.sumRutils > minUtil) {
				TaxonomyNode taxonomyNodeX = taxonomy.getMapItemToTaxonomyNode().get(X.item);
				List<TaxonomyNode> childOfX = taxonomyNodeX.getChildren();
				for (TaxonomyNode taxonomyNode : childOfX) {
					int Child = taxonomyNode.getData();
					UtilityList ULofChild = mapItemToUtilityList.get(Child);
					if (ULofChild != null) {
						UtilityList exULBuild = constructTax(pUL, ULofChild);
						X.AddChild(exULBuild);
					}
				}
				for (UtilityList childULs : X.getChild()) {
					if (childULs.GWU > minUtil) {
						ULs.add(childULs);
					}
				}
			}
			itemsetBuffer[prefixLength] = X.item;
			SearchTree(itemsetBuffer, prefixLength + 1, X, exULs);
		}

	}


	/**
	 * Construct a tax utility list
	 * @param P the utility list of a prefix itemset
	 * @param Child the utility of a child 
	 * @return the new utility list
	 */
	private UtilityList constructTax(UtilityList P, UtilityList Child) {

		if (P == null) {
			return Child;
		} else {
			UtilityList newULs = new UtilityList(Child.item);
			for (Element PElment : P.getElement()) {

				Element UnionChild = findElementWithTID(Child, PElment.tid);
				if (UnionChild != null) {
					List<Pair> trans = datasetAfterRemove.get(UnionChild.tid);
					double remainUtility = 0;
					for (int i = 0; i < trans.size(); i++) {
						Integer currentItem = trans.get(i).item;
						if (compareItems(currentItem, Child.item) > 0 && (!CheckParent(Child.item, currentItem))
								&& (!CheckParent(Child.item, currentItem))) {
							remainUtility += trans.get(i).utility;
						}
					}

					// Create new element
					Element newElment = new Element(UnionChild.tid, PElment.iutils + UnionChild.iutils, remainUtility,
							UnionChild.TU);
					// add the new element to the utility list of pXY
					newULs.addElement(newElment);
				}
			}
			// return the utility list of pXY.
			return newULs;
		}
	}

	/**
	 * Construct the utility list (normal case)
	 * @param P the utility list of a prefix P
	 * @param px the utility list of an extension PX of P with an item X
	 * @param py the utility list of an extension PY of P with an item Y
	 * @return the utility list of PXY
	 */
	private UtilityList construct(UtilityList P, UtilityList px, UtilityList py) {
		UtilityList pxyUL = new UtilityList(py.item);

		// for each element in the utility list of pX
		for (Element ex : px.elements) {
			// do a binary search to find element ey in py with tid = ex.tid
			Element ey = findElementWithTID(py, ex.tid);
			if (ey == null) {
				continue;
			}
			// if the prefix p is null
			if (P == null) {
				// Create the new element
				List<Pair> trans = datasetAfterRemove.get(ex.tid);
				double remainUtility = 0;
				for (int i = 0; i < trans.size(); i++) {
					Integer currentItem = trans.get(i).item;
					if (compareItems(currentItem, py.item) > 0 && (!CheckParent(px.item, currentItem))
							&& (!CheckParent(py.item, currentItem))) {
						remainUtility += trans.get(i).utility;
					}
				}
				Element eXY = new Element(ex.tid, ex.iutils + ey.iutils, remainUtility, ey.TU);
				// add the new element to the utility list of pXY
				pxyUL.addElement(eXY);

			} else {
				// find the element in the utility list of p wih the same tid
				Element e = findElementWithTID(P, ex.tid);
				if (e != null) {
					List<Pair> trans = datasetAfterRemove.get(e.tid);
					double remainUtility = 0;
					for (int i = 0; i < trans.size(); i++) {
						Integer currentItem = trans.get(i).item;
						if (compareItems(currentItem, py.item) > 0 && (!CheckParent(px.item, currentItem))
								&& (!CheckParent(py.item, currentItem))) {
							remainUtility += trans.get(i).utility;
						}
					}
					Element eXY = new Element(ex.tid, ex.iutils + ey.iutils - e.iutils, remainUtility, ey.TU);
					// add the new element to the utility list of pXY
					pxyUL.addElement(eXY);
				}
			}
		}
		// return the utility list of pXY.
		return pxyUL;
	}

	private Element findElementWithTID(UtilityList ulist, int tid) {
		List<Element> list = ulist.elements;

		// perform a binary search to check if the subset appears in level k-1.
		int first = 0;
		int last = list.size() - 1;

		// the binary search
		while (first <= last) {
			int middle = (first + last) >>> 1; // divide by 2

			if (list.get(middle).tid < tid) {
				first = middle + 1; // the itemset compared is larger than the subset according to the lexical order
			} else if (list.get(middle).tid > tid) {
				last = middle - 1; // the itemset compared is smaller than the subset is smaller according to the
									// lexical order
			} else {
				return list.get(middle);
			}
		}
		return null;
	}

	/**
	 * Comparator to sort items by order
	 * @param item1 the first item
	 * @param item2 the second item
	 * @return a value indicating the order (following the contract of the Comparator class in Java)
	 */
	private int compareItems(int item1, int item2) {
		int levelOfItem1 = taxonomy.getMapItemToTaxonomyNode().get(item1).getLevel();
		int levelOfItem2 = taxonomy.getMapItemToTaxonomyNode().get(item2).getLevel();
		if (levelOfItem1 == levelOfItem2) {
			int compare = (int) (mapItemToTWU.get(item1) - mapItemToTWU.get(item2));
			// if the same, use the lexical order otherwise use the TWU
			return (compare == 0) ? item1 - item2 : compare;
		} else {
			return levelOfItem1 - levelOfItem2;
		}
	}

	/**
	 * Check the parent
	 * @param item1 an item
	 * @param item2 another item
	 * @return true if item 2 is a parent of item 1. Otherwise faste
	 */
	private boolean CheckParent(int item1, int item2) {
		TaxonomyNode nodeItem1 = taxonomy.getMapItemToTaxonomyNode().get(item1);
		TaxonomyNode nodeItem2 = taxonomy.getMapItemToTaxonomyNode().get(item2);
		int levelOfItem1 = nodeItem1.getLevel();
		int levelOfItem2 = nodeItem2.getLevel();
		if (levelOfItem1 == levelOfItem2) {
			return false;
		} else {
			if (levelOfItem1 > levelOfItem2) {
				TaxonomyNode parentItem1 = nodeItem1.getParent();
				while (parentItem1.getData() != -1) {
					if (parentItem1.getData() == nodeItem2.getData()) {
						return true;
					}
					parentItem1 = parentItem1.getParent();
				}
				return false;
			} else {
				TaxonomyNode parentItem2 = nodeItem2.getParent();
				while (parentItem2.getData() != -1) {
					if (parentItem2.getData() == nodeItem1.getData()) {
						return true;
					}
					parentItem2 = parentItem2.getParent();
				}
				return false;
			}
		}
	}

	/**
	 * Print statistics about the algorithm execution
	 * 
	 * @throws IOException if error reading or writing to file
	 */
	public void printStats() throws IOException {
		System.out.println("=============  CLH-Miner v. 2.45 =============");
		System.out.println(" Runtime time ~ : " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ : " + MemoryLogger.getInstance().getMaxMemory() + " MB");
		System.out.println(" Cross level high utility itemsets (count): " + countHUI);
		System.out.println("   Number of items              : " + itemCount);
		System.out.println("   Number of generalized items             : " + giCount);
		System.out.println("   Taxonomy depth   : " + taxDepth);
		System.out.println("   Candidates (count): " + candidate);
		System.out.println("======================================");
	}
}
