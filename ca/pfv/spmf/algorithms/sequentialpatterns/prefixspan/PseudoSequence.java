package ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan;
/*
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
 * Copyright (c) Philipe Fournier-Viger et al.
 *
 * Do not remove copyright and license information.
 */
/**
 * Represents a projected sequence, referencing its original sequence and start index.
 * 
 * This class is used by the PrefixSpan and BIDE+ algorithms.
 * 
 *  @author Philipe-Fournier-Viger
 */
public class PseudoSequence {

	/** ID of the original sequence in the database. */
	protected int sequenceID;

	/** Index of the first item of this pseudo-sequence in the original sequence. */
	protected int indexFirstItem;

	/**
	 * Creates a pseudo-sequence from an original sequence ID and start index.
	 * 
	 * @param sequenceID     ID of the original sequence
	 * @param indexFirstItem index of the first item of the pseudo-sequence
	 */
	public PseudoSequence(int sequenceID, int indexFirstItem) {
		// remember the original sequence ID
		this.sequenceID = sequenceID;
		// remember the starting position in the original sequence
		this.indexFirstItem = indexFirstItem;
	}

	/**
	 * Returns the ID of the original sequence.
	 * 
	 * @return original sequence ID
	 */
	public int getOriginalSequenceID() {
		return sequenceID;
	}

	/**
	 * Returns the index of the first item of this pseudo-sequence.
	 * 
	 * @return index of the first item
	 */
	public int getIndexFirstItem() {
		return indexFirstItem;
	}

	/**
	 * Returns the sequence ID of this pseudo-sequence.
	 * 
	 * @return sequence ID
	 */
	public int getSequenceID() {
		return sequenceID;
	}
}