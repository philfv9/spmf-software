package ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.IDList;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.creators.IdListCreator;
import ca.pfv.spmf.datastructures.collections.map.AMapIntToObject;

/*
 * Copyright Antonio Gomariz Peñalver 2013
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
 * 
 * Do not remove copyright and license.
 */
/**
 * Implementation of a sequence database.
 *
 * @author agomariz
 */
public class SequenceDatabase {

	/** Creator used to instantiate and populate id-lists for patterns. */
	private IdListCreator idListCreator;

	/** Maps each item to its equivalence class. */
	private AMapIntToObject<EquivalenceClass> frequentItems = new AMapIntToObject<EquivalenceClass>();

	/** The sequences that compose this database. */
	private List<Sequence> sequences = new ArrayList<Sequence>();

	/**
	 * Constructs an empty sequence database using the given id-list creator.
	 * 
	 * @param idListCreator the id-list creator to use for building patterns'
	 *                      id-lists
	 */
	public SequenceDatabase(IdListCreator idListCreator) {
		this.idListCreator = idListCreator;
	}

	/**
	 * Loads a database from the file at the given path, keeping only items meeting
	 * minSupport.
	 * 
	 * @param path       Path of the file where the database is stored
	 * @param minSupport Minimum relative support threshold
	 * @throws IOException if an I/O error occurs, or if the file is malformed (in
	 *                     which case the exception message identifies the offending
	 *                     line instead of silently loading a partial database)
	 */
	public void loadFile(String path, double minSupport) throws IOException {
		try (BufferedReader myInput = new BufferedReader(new FileReader(path))) {
			String thisLine;
			int lineNumber = 0;
			while ((thisLine = myInput.readLine()) != null) {
				lineNumber++;
				if (thisLine.isEmpty()) {
					continue;
				}
				char first = thisLine.charAt(0);
				if (first != '#' && first != '%' && first != '@') {
					// trim() + split on one-or-more whitespace so that a trailing
					// newline, a stray double space, etc. never produces an empty
					// token that would blow up on charAt(0) inside addSequence().
					String[] tokens = thisLine.trim().split("\\s+");
					try {
						addSequence(tokens);
					} catch (RuntimeException e) {
						// Fail loudly and specifically instead of printing a stack
						// trace and silently mining a half-loaded database.
						throw new IOException(
								"Malformed sequence database at line " + lineNumber + ": \"" + thisLine + "\"", e);
					}
				}
			}
		}

		int support = (int) Math.ceil((minSupport * sequences.size())-0.0000001);

		AMapIntToObject<EquivalenceClass>.AEntryIterator it = frequentItemsEntryIterator();
		while (it.hasNext()) {
			AMapIntToObject<EquivalenceClass>.Entry<EquivalenceClass> entry = it.next();
			EquivalenceClass equivalenceClass = entry.getValue();
			IDList idList = equivalenceClass.getIdList();
			if (idList.getSupport() < support) {
				it.remove();
			} else {
				idList.setAppearingSequences(equivalenceClass.getClassIdentifier());
				// The support is already known here: cache it on the pattern so output
				// never needs to fall back to a full BitSet.cardinality() popcount.
				equivalenceClass.getClassIdentifier().setCachedSupport(idList.getSupport());
			}
		}

		reduceDatabase();
	}

	/**
	 * Adds a sequence to the database by parsing its token array.
	 * 
	 * @param tokens Array of string tokens representing one sequence line
	 */
	public void addSequence(String[] tokens) {
		// Sequence ids are 1-based and equal to the position the sequence will end
		// up at in `sequences` (i.e. sequences.size() once appended below), since
		// this is the same numbering every other reader of a sequence id relies on
		// (see class javadoc and Sequence's javadoc for why that stays valid later).
		int seqId = sequences.size() + 1;
		Sequence sequence = new Sequence();

		Itemset itemset = new Itemset();
		int itemsetIndex = 1;

		for (String token : tokens) {
			if (token.isEmpty()) {
				continue;
			}
			char first = token.charAt(0);

			if (first == '<') {
				// timestamp tokens in the file are ignored; position index is used instead
			} else if (first == '-') {
				if (token.length() == 2 && token.charAt(1) == '1') {
					sequence.addItemset(itemset);
					itemset = new Itemset();
					itemsetIndex++;
				} else {
					// End-of-sequence marker (standard SPMF format: "-2"). Every
					// well-formed itemset is already closed by a "-1" above, but if
					// a malformed line reaches "-2" with an itemset still open,
					// flush it instead of silently discarding it.
					if (itemset.size() > 0) {
						sequence.addItemset(itemset);
						itemset = new Itemset();
					}
					sequences.add(sequence);
				}
			} else {
				int itemId = Integer.parseInt(token);

				EquivalenceClass clase = frequentItems.get(itemId);
				if (clase == null) {
					IDList idlist = idListCreator.create();
					Pattern pattern = new Pattern(itemId, false);
					clase = new EquivalenceClass(pattern, idlist);
					frequentItems.put(itemId, clase);
				}

				// Cache the id-list reference to avoid repeated getIdList() calls
				IDList idList = clase.getIdList();
				idListCreator.addAppearance(idList, seqId, itemsetIndex);
				itemset.addItem(itemId);
			}
		}
	}

	/**
	 * Returns the string representation of this database.
	 * 
	 * @return the string representation
	 */
	@Override
	public String toString() {
		StringBuilder r = new StringBuilder();
		for (int i = 0; i < sequences.size(); i++) {
			// sequence ids are 1-based positions within `sequences` (see class javadoc)
			r.append(i + 1);
			r.append(":  ");
			r.append(sequences.get(i).toString());
			r.append('\n');
		}
		return r.toString();
	}

	/**
	 * Returns the number of sequences in this database.
	 * 
	 * @return the number of sequences
	 */
	public int size() {
		return sequences.size();
	}

	/**
	 * Returns the list of sequences of this database.
	 * 
	 * @return the list of sequences
	 */
	public List<Sequence> getSequences() {
		return sequences;
	}

	/**
	 * Returns a typed, removal-capable iterator over the entries of the frequent
	 * items map.
	 * 
	 * @return the typed entry iterator
	 */
	@SuppressWarnings("unchecked")
	private AMapIntToObject<EquivalenceClass>.AEntryIterator frequentItemsEntryIterator() {
		return (AMapIntToObject<EquivalenceClass>.AEntryIterator) frequentItems.iterator();
	}

	/**
	 * Returns a sorted list of equivalence classes for all frequent items found.
	 * 
	 * @return sorted list of equivalence classes
	 */
	public List<EquivalenceClass> frequentItems() {
		List<EquivalenceClass> result = new ArrayList<EquivalenceClass>();
		AMapIntToObject<EquivalenceClass>.AEntryIterator it = frequentItemsEntryIterator();
		while (it.hasNext()) {
			result.add(it.next().getValue());
		}
		Collections.sort(result);
		return result;
	}

	/**
	 * Returns the map of frequent items to their equivalence class.
	 * 
	 * @return the map of frequent items
	 */
	public AMapIntToObject<EquivalenceClass> getFrequentItems() {
		return frequentItems;
	}

	/**
	 * Returns a sorted list of equivalence classes for all frequent 2-patterns
	 * found.
	 * 
	 * @param minSupport Minimum absolute support
	 * @return list of frequent size-2 equivalence classes
	 */
	public List<EquivalenceClass> getSize2FrequentSequences(double minSupport) {
		List<EquivalenceClass> size2Patterns = getFrequentSize2Sequences(sequences, idListCreator);
		removeInfrequentItems(size2Patterns, minSupport);
		for (EquivalenceClass clase : size2Patterns) {
			IDList idList = clase.getIdList();
			idList.setAppearingSequences(clase.getClassIdentifier());
			// Cache the already-known support on the pattern (see loadFile() above for
			// the same rationale).
			clase.getClassIdentifier().setCachedSupport(idList.getSupport());
		}
		return size2Patterns;
	}

	/**
	 * Combined key for a size-2 candidate (itemA, itemB, equalRelation of itemB),
	 * used while mining size-2 candidates. The first element's own relation flag is
	 * never stored here because, by construction below, it is always false (a
	 * size-2 candidate always starts with a plain s-relation item), so including it
	 * in the key would be redundant
	 */
	private static final class PairKey {
		/** Leading item of the size-2 candidate (always an s-relation element). */
		private int itemA;
		/** Trailing item of the size-2 candidate. */
		private int itemB;
		/** equalRelation flag of the trailing item. */
		private boolean eqB;

		/**
		 * Constructs a key from the primitive components of a size-2 candidate.
		 * 
		 * @param itemA the leading item id
		 * @param itemB the trailing item id
		 * @param eqB   the trailing item's equalRelation flag
		 */
		private PairKey(int itemA, int itemB, boolean eqB) {
			this.itemA = itemA;
			this.itemB = itemB;
			this.eqB = eqB;
		}

		/**
		 * Overwrites this key's fields so it can be reused to probe the map without
		 * allocating a new key object.
		 * 
		 * @param itemA the leading item id
		 * @param itemB the trailing item id
		 * @param eqB   the trailing item's equalRelation flag
		 */
		private void set(int itemA, int itemB, boolean eqB) {
			this.itemA = itemA;
			this.itemB = itemB;
			this.eqB = eqB;
		}

		/**
		 * Creates an immutable snapshot of this key's current contents, suitable for
		 * being stored as a map key.
		 * 
		 * @return a new independent PairKey with the same contents as this one
		 */
		private PairKey snapshot() {
			return new PairKey(itemA, itemB, eqB);
		}

		/**
		 * Checks equality based on all three primitive fields.
		 * 
		 * @param obj the object to compare with
		 * @return true if obj is a PairKey with the same contents
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof PairKey)) {
				return false;
			}
			PairKey other = (PairKey) obj;
			return itemA == other.itemA && itemB == other.itemB && eqB == other.eqB;
		}

		/**
		 * Computes a hash code combining all three primitive fields.
		 * 
		 * @return the hash code
		 */
		@Override
		public int hashCode() {
			int h = 31 * itemA + itemB;
			return 31 * h + (eqB ? 1 : 0);
		}
	}

	/**
	 * Finds all frequent 2-patterns in the original database.
	 * 
	 * @param sequences     the list of sequences to scan
	 * @param idListCreator the id-list creator
	 * @return sorted list of all candidate size-2 equivalence classes
	 */
	private List<EquivalenceClass> getFrequentSize2Sequences(List<Sequence> sequences, IdListCreator idListCreator) {
		Map<PairKey, EquivalenceClass> totalMap = new HashMap<PairKey, EquivalenceClass>();
		// Local scratch probe key
		PairKey lookupKey = new PairKey(0, 0, false);

		for (int s = 0; s < sequences.size(); s++) {
			Sequence seq = sequences.get(s);
			// sequence ids are 1-based positions within `sequences` (see class javadoc);
			// this must stay numerically identical to the seqId used in addSequence()
			// above, since both get stored into the same id-lists.
			int seqId = s + 1;
			List<Itemset> itemsets = seq.getItemsets();
			int numItemsets = itemsets.size();

			for (int i = 0; i < numItemsets; i++) {
				Itemset currentItemset = itemsets.get(i);
				int currentSize = currentItemset.size();
				int currentPos = i + 1;

				for (int j = 0; j < currentSize; j++) {
					int itemA = currentItemset.get(j);

					// i-extensions: same itemset, equalRelation=true
					for (int k = j + 1; k < currentSize; k++) {
						int itemB = currentItemset.get(k);
						updateIdList(totalMap, lookupKey, itemA, itemB, true, seqId, currentPos, idListCreator);
					}
					// s-extensions: later itemsets, equalRelation=false
					for (int k = i + 1; k < numItemsets; k++) {
						Itemset nextItemset = itemsets.get(k);
						int nextSize = nextItemset.size();
						int nextPos = k + 1;
						for (int n = 0; n < nextSize; n++) {
							int itemB = nextItemset.get(n);
							updateIdList(totalMap, lookupKey, itemA, itemB, false, seqId, nextPos, idListCreator);
						}
					}
				}
			}
		}

		List<EquivalenceClass> result = new ArrayList<EquivalenceClass>(totalMap.values());
		Collections.sort(result);
		return result;
	}

	/**
	 * Updates the id-list for the 2-pattern (itemA, itemB with relation eqB),
	 * creating its equivalence class if absent.
	 * 
	 * @param totalMap      the flat map of candidate key to equivalence class
	 * @param lookupKey     reusable scratch key (caller-owned, mutated here)
	 * @param itemA         the leading item of the 2-pattern (always s-relation)
	 * @param itemB         the trailing item of the 2-pattern
	 * @param eqB           the trailing item's equalRelation flag
	 * @param sid           the sequence id
	 * @param tid           the itemset position index (1-based)
	 * @param idListCreator the id-list creator
	 */
	private void updateIdList(Map<PairKey, EquivalenceClass> totalMap, PairKey lookupKey, int itemA, int itemB,
			boolean eqB, int sid, int tid, IdListCreator idListCreator) {
		// Probe with the reusable scratch key first
		lookupKey.set(itemA, itemB, eqB);
		EquivalenceClass eq = totalMap.get(lookupKey);
		if (eq == null) {
			Pattern pattern = new Pattern(itemA, false).cloneAndAppend(itemB, eqB);
			IDList id = idListCreator.create();
			eq = new EquivalenceClass(pattern, id);
			// Only a genuinely new candidate needs its own immutable key snapshot for
			// storage.
			totalMap.put(lookupKey.snapshot(), eq);
		}
		idListCreator.addAppearance(eq.getIdList(), sid, tid);
	}

	/**
	 * Returns the subpattern derived from removing the item at the given index.
	 * 
	 * @param extension the source pattern
	 * @param index     the position of the item to remove
	 * @return the subpattern
	 */
	public Pattern getSubpattern(Pattern extension, int index) {
		int size = extension.size();
		int newSize = size - 1;
		int[] newItemIds = new int[newSize];
		boolean[] newEqualRelations = new boolean[newSize];
		int nextIndex = index + 1;

		// The removed item's relation to *its* predecessor. False when index==0
		// since there is no predecessor at all (removed item was the first one).
		boolean removedItemRelationToPredecessor = index != 0 && extension.getIthEqualRelation(index);

		int w = 0;
		for (int i = 0; i < size; i++) {
			if (i == index) {
				continue;
			}
			newItemIds[w] = extension.getIthItemId(i);
			if (i == nextIndex) {
				newEqualRelations[w] = removedItemRelationToPredecessor && extension.getIthEqualRelation(nextIndex);
			} else {
				newEqualRelations[w] = extension.getIthEqualRelation(i);
			}
			w++;
		}
		return new Pattern(newItemIds, newEqualRelations, newSize);
	}

	/**
	 * Removes infrequent size-2 patterns from the list and links survivors to their
	 * parent equivalence class.
	 * 
	 * @param size2Patterns the list of candidate size-2 equivalence classes
	 *                      (modified in place)
	 * @param minSupport    the minimum absolute support threshold
	 */
	private void removeInfrequentItems(List<EquivalenceClass> size2Patterns, double minSupport) {
		if (size2Patterns.isEmpty()) {
			return;
		}

		int currentLeadItemId = size2Patterns.get(0).getClassIdentifier().getIthItemId(0);
		EquivalenceClass currentLeadingClass = frequentItems.get(currentLeadItemId);

		int writeIdx = 0;
		for (int i = 0; i < size2Patterns.size(); i++) {
			EquivalenceClass clase = size2Patterns.get(i);
			int leadItemId = clase.getClassIdentifier().getIthItemId(0);

			if (leadItemId != currentLeadItemId) {
				currentLeadItemId = leadItemId;
				currentLeadingClass = frequentItems.get(currentLeadItemId);
			}

			if (clase.getIdList().getSupport() >= minSupport) {
				if (currentLeadingClass != null) {
					currentLeadingClass.addClassMember(clase);
				}
				size2Patterns.set(writeIdx, clase);
				writeIdx++;
			}
		}
		size2Patterns.subList(writeIdx, size2Patterns.size()).clear();
	}

	/**
	 * Removes infrequent items from every sequence in the database.
	 */
	private void reduceDatabase() {
		for (Sequence sequence : sequences) {
			for (int i = 0; i < sequence.size(); i++) {
				Itemset itemset = sequence.get(i);
				for (int j = 0; j < itemset.size(); j++) {
					int itemId = itemset.get(j);
					if (!frequentItems.containsKey(itemId)) {
						sequence.removeItem(i, j);
						j--;
					}
				}
				if (itemset.size() == 0) {
					sequence.remove(i);
					i--;
				}
			}
		}
	}

	/**
	 * Empties this database so the instance can be reused for a fresh loadfile()
	 * call.
	 */
	public void clear() {
		sequences.clear();
		frequentItems.clear();
	}
}