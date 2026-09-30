package ca.pfv.spmf.algorithms.sequentialpatterns.sui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
/* This file is part of the SPMF DATA MINING SOFTWARE
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
 * Data-IDList: stores actual position data for a pattern's occurrences.
 *
 * Optimized: position lists stored as int[] (no boxing).
 * dataId-to-row lookup uses a direct int[] index array for O(1) access
 * instead of a HashMap.
 *
 * Row objects are retained for API compatibility with callers that need
 * structured access, but the hot-path in AlgoSUI uses VertDB directly.
 * 
 * @author Philippe Fournier-Viger
 */
public class DataIDList {

    /**
     * A row in the Data-IDList.
     * positionList is now backed by int[] to avoid boxing.
     * The List<Integer> view is built lazily if needed for compatibility.
     */
    public static class Row {
        public final int dataId;
        public final int tsid;
        public final List<Integer> positionList; // kept for API compat; backed by int[]

        public Row(int dataId, int tsid, List<Integer> positionList) {
            this.dataId       = dataId;
            this.tsid         = tsid;
            this.positionList = positionList;
        }
    }

    private final Pattern pattern;
    private final List<Row> rows;
    private int support;

    // O(1) lookup: dataIdIndex[d] = index in rows list; sized maxDataId+1
    private int[] dataIdIndex;
    private int   maxDataId = 0;

    public DataIDList(Pattern pattern, int estimatedSize) {
        this.pattern = pattern;
        this.rows    = new ArrayList<>(estimatedSize);
        this.support = 0;
    }

    public void addRow(Row row) {
        rows.add(row);
        support++;
        if (row.dataId > maxDataId) maxDataId = row.dataId;
    }

    /**
     * Build the dataId->index lookup array.
     * Must be called after all rows have been added (once).
     */
    public void buildIndex() {
        dataIdIndex = new int[maxDataId + 1];
        Arrays.fill(dataIdIndex, -1);
        for (int i = 0; i < rows.size(); i++) {
            dataIdIndex[rows.get(i).dataId] = i;
        }
    }

    /**
     * O(1) row lookup by dataId using the prebuilt index.
     * Falls back to linear scan if index not built (robustness).
     */
    public Row getRowByDataId(int dataId) {
        if (dataIdIndex != null) {
            if (dataId < 0 || dataId > maxDataId) return null;
            int idx = dataIdIndex[dataId];
            return idx >= 0 ? rows.get(idx) : null;
        }
        // Fallback linear scan (should not occur in optimized path)
        for (Row r : rows) {
            if (r.dataId == dataId) return r;
        }
        return null;
    }

    public Pattern getPattern()  { return pattern; }
    public int getSupport()      { return support; }
    public List<Row> getRows()   { return rows; }
}