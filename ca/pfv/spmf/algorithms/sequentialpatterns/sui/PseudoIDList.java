package ca.pfv.spmf.algorithms.sequentialpatterns.sui;

import java.util.ArrayList;
import java.util.List;

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
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (c) Philipe Fournier-Viger et al.
 *
 * Do not remove copyright and license information.
 */
/**
 * This class represents a Pseudo-IDList structure used in the SUI algorithm. A
 * Pseudo-IDList stores indices to a Data-IDList rather than actual position
 * data, avoiding data duplication for s-extension patterns.
 *
 * Each row in a Pseudo-IDList is a 3-tuple: {Local_id, Data_id, Start_index} -
 * Local_id: an incremental integer assigned to each transaction sequence in
 * this pseudo-IDList - Data_id: matches a corresponding data_id in the
 * referenced Data-IDList (row index) - Start_index: starting index in the
 * position list from which to retrieve the tail sublist.
 * 
 * @author Philippe Fournier-Viger
 */
public class PseudoIDList {

	/**
	 * A single row in the Pseudo-IDList containing {Local_id, Data_id, Start_index}
	 */
	public static class Row {
		/** Local identifier assigned incrementally */
		public int localId;
		/** Reference to a row in the Data-IDList by data_id */
		public int dataId;
		/** Starting index in the position list for tail sublist retrieval */
		public int startIndex;

		/**
		 * Constructor
		 * 
		 * @param localId    local identifier
		 * @param dataId     data id reference
		 * @param startIndex starting index
		 */
		public Row(int localId, int dataId, int startIndex) {
			this.localId = localId;
			this.dataId = dataId;
			this.startIndex = startIndex;
		}

		@Override
		public String toString() {
			return "(" + localId + ", " + dataId + ", " + startIndex + ")";
		}
	}

	/** The pattern associated with this Pseudo-IDList */
	private Pattern pattern;

	/**
	 * DIP (Data-IDList Pointer): points to the Data-IDList of the frequent
	 * 1-pattern which is the last item in the pattern P.
	 */
	private DataIDList dip;

	/** The collection of rows */
	private List<Row> rows;

	/** Support count (number of rows) */
	private int support;

	/**
	 * Constructor
	 * 
	 * @param pattern the associated pattern
	 * @param dip     pointer to the corresponding Data-IDList of the last frequent
	 *                1-pattern
	 */
	public PseudoIDList(Pattern pattern, DataIDList dip) {
		this.pattern = pattern;
		this.dip = dip;
		this.rows = new ArrayList<>();
		this.support = 0;
	}

	/**
	 * Constructor with estimated size
	 * 
	 * @param pattern       the associated pattern
	 * @param dip           pointer to the corresponding Data-IDList of the last
	 *                      frequent 1-pattern
	 * @param estimatedSize estimated number of rows
	 */
	public PseudoIDList(Pattern pattern, DataIDList dip, int estimatedSize) {
		this.pattern = pattern;
		this.dip = dip;
		this.rows = new ArrayList<>(estimatedSize);
		this.support = 0;
	}

	/**
	 * Add a row to this Pseudo-IDList
	 * 
	 * @param row the row to add
	 */
	public void addRow(Row row) {
		rows.add(row);
		support++;
	}

	/**
	 * Get all rows
	 * 
	 * @return list of rows
	 */
	public List<Row> getRows() {
		return rows;
	}

	/**
	 * Get the support (number of rows)
	 * 
	 * @return support count
	 */
	public int getSupport() {
		return support;
	}

	/**
	 * Get the DIP (Data-IDList Pointer)
	 * 
	 * @return the referenced Data-IDList
	 */
	public DataIDList getDip() {
		return dip;
	}

	/**
	 * Set the DIP
	 * 
	 * @param dip the Data-IDList to point to
	 */
	public void setDip(DataIDList dip) {
		this.dip = dip;
	}

	/**
	 * Get the associated pattern
	 * 
	 * @return pattern
	 */
	public Pattern getPattern() {
		return pattern;
	}

	/**
	 * Set the associated pattern
	 * 
	 * @param pattern the pattern
	 */
	public void setPattern(Pattern pattern) {
		this.pattern = pattern;
	}

	/**
	 * Retrieve the actual position list for a given row using the DIP. Returns the
	 * tail sublist starting from startIndex.
	 * 
	 * @param row the pseudo-IDList row
	 * @return the tail sublist of positions, or null if not found
	 */
	public List<Integer> retrievePositionList(Row row) {
		if (dip == null)
			return null;
		DataIDList.Row dataRow = dip.getRowByDataId(row.dataId);
		if (dataRow == null)
			return null;
		List<Integer> fullList = dataRow.positionList;
		if (row.startIndex >= fullList.size())
			return new ArrayList<>();
		return fullList.subList(row.startIndex, fullList.size());
	}

	/**
	 * Retrieve the TSID for a given row using the DIP.
	 * 
	 * @param row the pseudo-IDList row
	 * @return the TSID, or -1 if not found
	 */
	public int retrieveTSID(Row row) {
		if (dip == null)
			return -1;
		DataIDList.Row dataRow = dip.getRowByDataId(row.dataId);
		if (dataRow == null)
			return -1;
		return dataRow.tsid;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("PseudoIDList[").append(pattern).append("] supp=").append(support).append("\n");
		sb.append("  DIP -> ").append(dip != null ? dip.getPattern() : "null").append("\n");
		for (Row row : rows) {
			sb.append("  ").append(row).append("\n");
		}
		return sb.toString();
	}
}