/* This file is copyright (c) 2026 Philippe Fournier-Viger
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
 *
 * Do not remove copyright and license information.
 */

package ca.pfv.spmf.algorithms.frequentpatterns.huo;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A PUON-list whose records identify internal item nodes of a TPUT.
 * Records aggregate utility and support at their smallest-item anchor node;
 * exact utility/TU cohorts preserve means of ratios and top-ms bounds.
 * Two-item lists use ancestor paths, while larger sibling lists join equal
 * anchor identifiers with one common-prefix subtraction.
 *
 * @author Philippe Fournier-Viger
 */
final class PUONList {
	
	/** The itemset in ascending fixed support-rank order. */
	final int[] items;
	
	/** The PUON records ordered by their internal anchor-node identifier. */
	final List<Element> elements = new ArrayList<>();
	
	/** The number of original supporting transactions, including multiplicities. */
	int support;
	
	/** The exact transaction-weighted utility used for backward hashing. */
	BigInteger twu = BigInteger.ZERO;
	
	/** The cached actual mean utility occupancy. */
	double fOcc;
	
	/** The cached conservative weak upper bound for proper forward extensions. */
	double fWubocc;
	
	/** The weighted bound values for groups that have a proper forward extension. */
	double[] listUbRemRel;
	
	/** The multiplicity of each value in the weighted bound vector. */
	int[] boundMultiplicities;
	
	/** The number of original transactions represented in TS of this itemset. */
	int extensionSupport;
	
	/** Whether the cached occupancy and weak bound have been computed. */
	boolean statisticsComputed;
	
	/** Whether Strategy 6 marked this branch as non-closed. */
	boolean isPrunedByLPS;

	/** An aggregate PUON record with exact arithmetic companions at one item node. */
	static final class Element {
		
		/** The internal item node for the smallest item of this itemset. */
		final TPUTTree.Node node;
		
		/** The globally unique anchor-node identifier, not a terminal identifier. */
		final int nid;
		
		/** The exact sum of itemset utilities over the original supporting rows. */
		final BigInteger nu;
		
		/** The exact sum of utilities of all globally later retained items. */
		final BigInteger nru;
		
		/** The exact sum of utilities of the common itemset prefix. */
		final BigInteger npu;
		
		/** The number of original transactions represented at this node. */
		final int nsup;
		
		/** The exact sum of original transaction utilities, never a ratio denominator. */
		final BigInteger tu;
		
		/** The per-cohort values aligned with the anchor node's utility cohorts. */
		final List<Value> values;

		/**
		 * Summarize an internal-node record without collapsing different utility ratios.
		 *
		 * @param node   the smallest-item anchor node
		 * @param values the exact values in the anchor's stable cohort order
		 */
		Element(TPUTTree.Node node, List<Value> values) {
			this.node = node;
			nid = node.nid;
			this.values = values;
			int count = 0;
			BigInteger utility = BigInteger.ZERO;
			BigInteger remaining = BigInteger.ZERO;
			BigInteger prefix = BigInteger.ZERO;
			BigInteger total = BigInteger.ZERO;
			for (Value value : values) {
				BigInteger weight = BigInteger.valueOf(value.cohort.nsup);
				count = Math.addExact(count, value.cohort.nsup);
				utility = utility.add(BigInteger.valueOf(value.nu).multiply(weight));
				remaining = remaining.add(BigInteger.valueOf(value.nru).multiply(weight));
				prefix = prefix.add(BigInteger.valueOf(value.npu).multiply(weight));
				total = total.add(BigInteger.valueOf(value.cohort.tu).multiply(weight));
			}
			nsup = count;
			nu = utility;
			nru = remaining;
			npu = prefix;
			tu = total;
		}
	}

	/** The exact per-row utility values shared by one weighted anchor-node cohort. */
	static final class Value {
		/** The original TU, multiplicity and exact ancestor-utility path. */
		final TPUTTree.Cohort cohort;
		/** The itemset utility in each original row of this cohort. */
		final long nu;
		/** The remaining utility beyond the last item in each original row. */
		final long nru;
		/** The utility of the itemset without its last item in each original row. */
		final long npu;
		/** The number of retained later items, including zero-utility items. */
		final int remainingItems;

		/**
		 * Retain exact occupancy companions independently of aggregate node summaries.
		 *
		 * @param cohort         the stable exact utility/TU cohort
		 * @param nu             the itemset utility in each original row
		 * @param nru            the remaining utility in each original row
		 * @param npu            the common-prefix utility in each original row
		 * @param remainingItems the number of retained later items
		 */
		Value(TPUTTree.Cohort cohort, long nu, long nru, long npu, int remainingItems) {
			this.cohort = cohort;
			this.nu = nu;
			this.nru = nru;
			this.npu = npu;
			this.remainingItems = remainingItems;
		}
	}

	/** A qualifying closed itemset retained without its working PUON-list. */
	static final class Pattern {
		/** The immutable itemset in support-rank order. */
		final int[] items;
		/** The exact number of supporting original transactions. */
		final int support;
		/** The actual mean utility occupancy. */
		final double occupancy;

		/**
		 * Snapshot a proven closed itemset without retaining its working records.
		 *
		 * @param list the evaluated closed PUON-list
		 */
		Pattern(PUONList list) {
			items = list.items.clone();
			support = list.support;
			occupancy = Math.max(0, Math.min(1, list.fOcc));
		}
	}

	/**
	 * Create a PUON-list for a support-rank-ordered itemset.
	 *
	 * @param items the immutable ascending-rank itemset
	 */
	PUONList(int[] items) {
		this.items = items;
	}

	/**
	 * Add an ordered record and update exact support and TWU summaries.
	 *
	 * @param element the next internal-node record
	 */
	void add(Element element) {
		elements.add(element);
		support = Math.addExact(support, element.nsup);
		twu = twu.add(element.tu);
	}

	/**
	 * Obtain the last item rank of this nonempty itemset.
	 *
	 * @return the last support rank
	 */
	int lastItem() {
		return items[items.length - 1];
	}

	/**
	 * Join sibling lists using Definitions 16-17 of the 2022 paper with exact TU companions.
	 *
	 * @param right the later sibling with the same common prefix
	 * @return the complete PUON-list for the union
	 */
	PUONList join(PUONList right) {
		int[] union = Arrays.copyOf(items, items.length + 1);
		union[items.length] = right.lastItem();
		PUONList result = new PUONList(union);
		if (items.length == 1) {
			for (Element left : elements) {
				TPUTTree.Node ancestor = left.node.parent;
				while (ancestor.parent != null && ancestor.rank < right.lastItem()) {
					ancestor = ancestor.parent;
				}
				if (ancestor.rank != right.lastItem()) {
					continue;
				}
				List<Value> values = new ArrayList<>(left.values.size());
				for (Value value : left.values) {
					TPUTTree.Cohort other = value.cohort.atDepth(ancestor.depth);
					values.add(new Value(value.cohort, Math.addExact(value.nu, other.utility),
							Math.subtractExact(other.pathUtility, other.utility), value.nu, ancestor.depth - 1));
				}
				result.add(new Element(left.node, values));
			}
			return result;
		}
		int leftIndex = 0;
		int rightIndex = 0;
		while (leftIndex < elements.size() && rightIndex < right.elements.size()) {
			Element left = elements.get(leftIndex);
			Element other = right.elements.get(rightIndex);
			if (left.nid < other.nid) {
				leftIndex++;
			} else if (left.nid > other.nid) {
				rightIndex++;
			} else {
				List<Value> values = new ArrayList<>(left.values.size());
				for (int i = 0; i < left.values.size(); i++) {
					Value value = left.values.get(i);
					Value next = other.values.get(i);
					long utility = Math.addExact(Math.subtractExact(value.nu, value.npu), next.nu);
					values.add(new Value(value.cohort, utility, next.nru, value.nu, next.remainingItems));
				}
				result.add(new Element(left.node, values));
				leftIndex++;
				rightIndex++;
			}
		}
		return result;
	}

	/**
	 * Cache mean occupancy and Definition 9's multiplicity-weighted proper-extension bound.
	 *
	 * @param minimumSupport the inclusive absolute support threshold
	 */
	void computeStatistics(int minimumSupport) {
		if (statisticsComputed) {
			return;
		}
		HUOUtility.Sum occupancy = new HUOUtility.Sum();
		int boundSize = 0;
		for (Element element : elements) {
			for (Value value : element.values) {
				occupancy.add((value.nu / (double) value.cohort.tu) * value.cohort.nsup);
				// TS depends on item presence, not positive remaining utility.
				if (value.remainingItems != 0) {
					extensionSupport = Math.addExact(extensionSupport, value.cohort.nsup);
					boundSize++;
				}
			}
		}
		fOcc = support == 0 ? 0 : occupancy.value() / support;
		if (extensionSupport >= minimumSupport) {
			listUbRemRel = new double[boundSize];
			boundMultiplicities = new int[boundSize];
			int index = 0;
			for (Element element : elements) {
				for (Value value : element.values) {
					if (value.remainingItems != 0) {
						listUbRemRel[index] = HUOUtility.upperRatio(Math.addExact(value.nu, value.nru), value.cohort.tu);
						boundMultiplicities[index++] = value.cohort.nsup;
					}
				}
			}
			sortBounds();
			int needed = minimumSupport;
			double sum = 0;
			for (int i = boundSize - 1; needed > 0; i--) {
				int take = Math.min(needed, boundMultiplicities[i]);
				double term = listUbRemRel[i] == 0 ? 0 : Math.nextUp(listUbRemRel[i] * take);
				sum = HUOUtility.upperAdd(sum, term);
				needed -= take;
			}
			fWubocc = HUOUtility.upperDivide(sum, minimumSupport);
		}
		statisticsComputed = true;
	}

	/**
	 * Sort the weighted bound vector in ascending order without expanding duplicate rows.
	 */
	private void sortBounds() {
		int size = listUbRemRel.length;
		for (int i = size / 2 - 1; i >= 0; i--) {
			siftDown(i, size);
		}
		for (int end = size - 1; end > 0; end--) {
			swapBounds(0, end);
			siftDown(0, end);
		}
	}

	/**
	 * Restore a max-heap over an active prefix of the weighted bound vector.
	 *
	 * @param parent the root of the disturbed subtree
	 * @param size   the exclusive end of the heap
	 */
	private void siftDown(int parent, int size) {
		while (parent < size / 2) {
			int child = 2 * parent + 1;
			if (child + 1 < size && listUbRemRel[child + 1] > listUbRemRel[child]) {
				child++;
			}
			if (listUbRemRel[parent] >= listUbRemRel[child]) {
				return;
			}
			swapBounds(parent, child);
			parent = child;
		}
	}

	/**
	 * Swap a bound and its multiplicity together.
	 *
	 * @param left  the first vector index
	 * @param right the second vector index
	 */
	private void swapBounds(int left, int right) {
		double value = listUbRemRel[left];
		listUbRemRel[left] = listUbRemRel[right];
		listUbRemRel[right] = value;
		int count = boundMultiplicities[left];
		boundMultiplicities[left] = boundMultiplicities[right];
		boundMultiplicities[right] = count;
	}

	/**
	 * Compare the actual weighted occupancy with an exact decimal threshold.
	 *
	 * @param threshold the exact minimum mean utility occupancy
	 * @return true if the exact mean is at least the threshold
	 */
	boolean exactOccupancyAtLeast(BigDecimal threshold) {
		BigInteger numerator = BigInteger.ZERO;
		BigInteger denominator = BigInteger.ONE;
		for (Element element : elements) {
			for (Value value : element.values) {
				BigInteger divisor = BigInteger.valueOf(value.cohort.tu);
				BigInteger gcd = denominator.gcd(divisor);
				BigInteger left = divisor.divide(gcd);
				BigInteger right = denominator.divide(gcd);
				BigInteger utility = BigInteger.valueOf(value.nu).multiply(BigInteger.valueOf(value.cohort.nsup));
				numerator = numerator.multiply(left).add(utility.multiply(right));
				denominator = denominator.multiply(left);
				BigInteger reduce = numerator.gcd(denominator);
				numerator = numerator.divide(reduce);
				denominator = denominator.divide(reduce);
			}
		}
		BigInteger scale = BigInteger.TEN.pow(Math.max(0, threshold.scale()));
		BigInteger target = threshold.unscaledValue();
		if (threshold.scale() < 0) {
			target = target.multiply(BigInteger.TEN.pow(-threshold.scale()));
		}
		return numerator.multiply(scale).compareTo(target.multiply(denominator)
				.multiply(BigInteger.valueOf(support))) >= 0;
	}

	/**
	 * Construct a jumping closure when every frequent extension preserves support.
	 *
	 * @param children the complete set of frequent one-item forward extensions
	 * @return the union with all such extensions and its exact PUON records
	 */
	PUONList jumpingClosure(List<PUONList> children) {
		if (children.isEmpty()) {
			return this;
		}
		int[] union = Arrays.copyOf(items, items.length + children.size());
		for (int i = 0; i < children.size(); i++) {
			union[items.length + i] = children.get(i).lastItem();
		}
		PUONList closure = new PUONList(union);
		for (int i = 0; i < elements.size(); i++) {
			Element original = elements.get(i);
			List<Value> values = new ArrayList<>(original.values.size());
			for (int j = 0; j < original.values.size(); j++) {
				Value value = original.values.get(j);
				long utility = value.nu;
				long lastUtility = 0;
				for (PUONList child : children) {
					Value next = child.elements.get(i).values.get(j);
					lastUtility = Math.subtractExact(next.nu, value.nu);
					utility = Math.addExact(utility, lastUtility);
				}
				Value last = children.get(children.size() - 1).elements.get(i).values.get(j);
				values.add(new Value(value.cohort, utility, last.nru, Math.subtractExact(utility, lastUtility),
						last.remainingItems));
			}
			closure.add(new Element(original.node, values));
		}
		return closure;
	}

	/**
	 * Test inclusion between two rank-sorted itemsets without comparing utility values.
	 *
	 * @param smaller the possible subset
	 * @param larger  the possible superset
	 * @return true if every item of smaller belongs to larger
	 */
	static boolean isSubset(int[] smaller, int[] larger) {
		int position = 0;
		for (int item : larger) {
			if (position < smaller.length && smaller[position] == item) {
				position++;
			}
		}
		return position == smaller.length;
	}
}
