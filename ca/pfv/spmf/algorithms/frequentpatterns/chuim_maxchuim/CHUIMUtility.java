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

package ca.pfv.spmf.algorithms.frequentpatterns.chuim_maxchuim;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validated input and reduced-dataset construction for C-HUIM and MaxC-HUIM.
 * Item utilities, TUs and their database total must fit nonnegative signed longs;
 * bounding all aggregates by the checked database TU prevents silent overflow.
 * This helper contains no closed/maximal search or output postprocessing.
 *
 * @author Philippe Fournier-Viger
 */
final class CHUIMUtility {
	/** Constructor */
	private CHUIMUtility() {
	}

	/** A validated row with numeric-sorted distinct item identifiers. */
	static final class Row {
		/** The distinct item identifiers, or fixed ranks after reduction. */
		final int[] items;
		/** The corresponding nonnegative utilities. */
		final long[] utilities;
		/** The original declared transaction utility. */
		final long tu;

		/**
		 * Create a row without changing its original transaction utility.
		 *
		 * @param items     the sorted identifiers or ranks
		 * @param utilities the corresponding utilities
		 * @param tu        the original transaction utility
		 */
		Row(int[] items, long[] utilities, long tu) {
			this.items = items;
			this.utilities = utilities;
			this.tu = tu;
		}
	}

	/** First-scan metadata using original transaction utilities. */
	static final class Metadata {
		/** The exact TWU of each present item. */
		final Map<Integer, Long> twu = new HashMap<>();
		/** The number of original transactions. */
		int transactions;
		/** The largest original transaction utility. */
		long maximumTU;
		/** The checked total of original transaction utilities. */
		long totalTU;
	}

	/** A streaming callback used by the two initialization scans. */
	interface Visitor {
		/**
		 * Process a validated original row.
		 *
		 * @param row the parsed original transaction
		 */
		void visit(Row row);
	}

	/**
	 * Reject canonical, symbolic-link and hard-link aliases before opening writers.
	 *
	 * @param first  the first file
	 * @param second the other file
	 * @throws IOException if file identity cannot be determined
	 */
	static void checkDifferentFiles(File first, File second) throws IOException {
		if (first.getCanonicalFile().equals(second.getCanonicalFile())
				|| (first.exists() && second.exists() && Files.isSameFile(first.toPath(), second.toPath()))) {
			throw new IllegalArgumentException("Input and output files must be distinct");
		}
	}

	/**
	 * Calculate item TWUs, original support-row count and the maximum TU.
	 *
	 * @param input the original utility database
	 * @return first-scan metadata
	 * @throws IOException if parsing or checked aggregation fails
	 */
	static Metadata metadata(Path input) throws IOException {
		Metadata result = new Metadata();
		scan(input, row -> {
			result.transactions = Math.incrementExact(result.transactions);
			result.maximumTU = Math.max(result.maximumTU, row.tu);
			result.totalTU = Math.addExact(result.totalTU, row.tu);
			for (int item : row.items) {
				result.twu.put(item, Math.addExact(result.twu.getOrDefault(item, 0L), row.tu));
			}
		});
		return result;
	}

	/**
	 * Filter globally low-TWU items and order the remainder by increasing TWU.
	 *
	 * @param metadata the first-scan original metadata
	 * @param minimum  the inclusive minimum utility
	 * @return original identifiers in ascending TWU order with numeric ties
	 */
	static int[] order(Metadata metadata, long minimum) {
		List<Integer> items = new ArrayList<>();
		for (Map.Entry<Integer, Long> entry : metadata.twu.entrySet()) {
			if (entry.getValue() >= minimum) {
				items.add(entry.getKey());
			}
		}
		items.sort((a, b) -> {
			int compare = Long.compare(metadata.twu.get(a), metadata.twu.get(b));
			return compare == 0 ? Integer.compare(a, b) : compare;
		});
		return items.stream().mapToInt(Integer::intValue).toArray();
	}

	/**
	 * Build RD with descending-TWU item paths and lexicographically sorted rows.
	 *
	 * @param input    the original input file
	 * @param order    the retained items in increasing mining order
	 * @param metadata the first-scan metadata for consistency checks
	 * @return nonempty revised rows in the paper's insertion order
	 * @throws IOException if parsing or scan consistency fails
	 */
	static List<Row> reduce(Path input, int[] order, Metadata metadata) throws IOException {
		Map<Integer, Integer> ranks = new HashMap<>();
		for (int i = 0; i < order.length; i++) {
			ranks.put(order[i], i);
		}
		List<Row> rows = new ArrayList<>();
		Metadata observed = new Metadata();
		scan(input, row -> {
			observed.transactions = Math.incrementExact(observed.transactions);
			observed.totalTU = Math.addExact(observed.totalTU, row.tu);
			long[] entries = new long[row.items.length];
			int size = 0;
			for (int i = 0; i < row.items.length; i++) {
				Integer rank = ranks.get(row.items[i]);
				if (rank != null) {
					entries[size++] = ((long) rank << 32) | i;
				}
			}
			if (size == 0) {
				return;
			}
			Arrays.sort(entries, 0, size);
			int[] items = new int[size];
			long[] utilities = new long[size];
			for (int i = 0; i < size; i++) {
				items[i] = (int) (entries[i] >>> 32);
				utilities[i] = row.utilities[(int) entries[i]];
			}
			rows.add(new Row(items, utilities, row.tu));
		});
		if (observed.transactions != metadata.transactions || observed.totalTU != metadata.totalTU) {
			throw new IOException("The input changed between initialization scans");
		}
		// Compare reversed item paths; shorter equal prefixes precede their extensions.
		rows.sort((a, b) -> {
			int length = Math.min(a.items.length, b.items.length);
			for (int i = 1; i <= length; i++) {
				int compare = Integer.compare(b.items[b.items.length - i], a.items[a.items.length - i]);
				if (compare != 0) {
					return compare;
				}
			}
			return Integer.compare(a.items.length, b.items.length);
		});
		return rows;
	}

	/**
	 * Scan SPMF utility rows while rejecting malformed data and overflowing totals.
	 *
	 * @param input   the original utility database
	 * @param visitor the callback receiving each validated row
	 * @throws IOException if reading, validation or checked arithmetic fails
	 */
	static void scan(Path input, Visitor visitor) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
			String line;
			int number = 0;
			while ((line = reader.readLine()) != null) {
				number++;
				if (number == 1 && line.startsWith("\ufeff")) {
					line = line.substring(1);
				}
				line = line.trim();
				if (line.isEmpty() || line.startsWith("@") || line.startsWith("#") || line.startsWith("%")) {
					continue;
				}
				try {
					String[] fields = line.split(":", -1);
					if (fields.length != 3 || fields[0].trim().isEmpty()) {
						throw new IllegalArgumentException("Expected items:TU:item utilities");
					}
					String[] ids = fields[0].trim().split("\\s+");
					String[] values = fields[2].trim().split("\\s+");
					if (ids.length != values.length) {
						throw new IllegalArgumentException("Item and utility counts differ");
					}
					long tu = Long.parseLong(fields[1].trim());
					long sum = 0;
					long[] packed = new long[ids.length];
					long[] utilities = new long[ids.length];
					for (int i = 0; i < ids.length; i++) {
						int item = Integer.parseInt(ids[i]);
						utilities[i] = Long.parseLong(values[i]);
						if (item < 0 || utilities[i] < 0) {
							throw new IllegalArgumentException("Negative item or utility");
						}
						sum = Math.addExact(sum, utilities[i]);
						packed[i] = ((long) item << 32) | i;
					}
					if (tu < sum || tu < 0) {
						throw new IllegalArgumentException("TU is below the item-utility sum");
					}
					Arrays.sort(packed);
					int[] items = new int[ids.length];
					long[] sorted = new long[ids.length];
					for (int i = 0; i < items.length; i++) {
						items[i] = (int) (packed[i] >>> 32);
						sorted[i] = utilities[(int) packed[i]];
						if (i > 0 && items[i] == items[i - 1]) {
							throw new IllegalArgumentException("Repeated item identifier");
						}
					}
					visitor.visit(new Row(items, sorted, tu));
				} catch (IllegalArgumentException | ArithmeticException exception) {
					throw new IOException(input + ": line " + number + ": " + exception.getMessage(), exception);
				}
			}
		}
	}
}
