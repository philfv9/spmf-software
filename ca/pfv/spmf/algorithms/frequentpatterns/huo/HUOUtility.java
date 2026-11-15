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

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Input and arithmetic helpers; no itemset enumeration or mining pruning. */
final class HUOUtility {
    /**
     * Prevent instantiation of the shared input and arithmetic helpers.
     */
    private HUOUtility() {
    }

    /** An item and its nonnegative utility. */
    static final class Item {
    	
        /** The numeric item identifier. */
        final int item;
        
        /** The exact nonnegative item utility. */
        final long utility;

        /**
         * Create a parsed item and its exact utility.
         * @param item the numeric item identifier
         * @param utility the nonnegative item utility
         */
        Item(int item, long utility) {
            this.item = item;
            this.utility = utility;
        }
    }

    /** One transaction, sorted by numeric item identifier. */
    static final class Transaction {
    	
        /** The transaction items sorted by numeric identifier. */
        final Item[] items;
        
        /** The positive original declared transaction utility. */
        final long total;

        /**
         * Create an original transaction with its declared denominator.
         * @param items the numeric-sorted distinct items
         * @param total the positive original transaction utility
         */
        Transaction(Item[] items, long total) {
            this.items = items;
            this.total = total;
        }
    }

    /** A reusable primitive rank/index buffer for one revised transaction. */
    static final class RevisedTransaction {
    	
        /** The sorted packed rank/original-item-index pairs. */
        long[] entries = new long[0];
        
        /** The number of retained entries in the current transaction. */
        int size;

        /**
         * Filter and sort a transaction without allocating ranked items or changing its TU.
         * @param transaction the original parsed transaction
         * @param ranks the retained items' fixed mining ranks
         */
        void revise(Transaction transaction, Map<Integer, Integer> ranks) {
            if (entries.length < transaction.items.length) {
                entries = new long[grownCapacity(entries.length, transaction.items.length)];
            }
            size = 0;
            for (int index = 0; index < transaction.items.length; index++) {
                Integer rank = ranks.get(transaction.items[index].item);
                if (rank != null) entries[size++] = (rank.longValue() << 32) | index;
            }
            Arrays.sort(entries, 0, size);
        }

        /**
         * Read the global mining rank of a revised transaction entry.
         * @param index the entry's index in the revised order
         * @return the nonnegative global mining rank
         */
        int rank(int index) {
            return (int) (entries[index] >>> 32);
        }

        /**
         * Read a revised entry's exact utility from the original transaction.
         * @param transaction the original transaction being revised
         * @param index the entry's index in the revised order
         * @return the original nonnegative item utility
         */
        long utility(Transaction transaction, int index) {
            return transaction.items[(int) entries[index]].utility;
        }
    }

    /** A reusable primitive scratch array for full-vector sorted bounds. */
    static final class BoundBuffer {
        /** The scratch values, of which only a caller-specified prefix is valid. */
        double[] values = new double[0];
        /** The number of backing arrays allocated after the initial empty array. */
        int growthCount;

        /**
         * Reserve a valid prefix without allocating when the existing capacity suffices.
         * @param size the number of values about to be overwritten
         * @return the reusable array with capacity at least size
         */
        double[] prepare(int size) {
            if (values.length < size) {
                values = new double[grownCapacity(values.length, size)];
                growthCount++;
            }
            return values;
        }
    }

    /** A streaming transaction callback. */
    interface Visitor {
        /**
         * Process one validated original transaction during a streaming scan.
         * @param transaction the validated parsed transaction
         * @param tid the original transaction identifier
         * @throws IOException if transaction processing fails
         */
        void visit(Transaction transaction, int tid) throws IOException;
    }

    /** Optional first-pass information for two-pass initialization. */
    static final class Metadata {
        /** The number of original transactions observed in the first scan. */
        int transactionCount;
        /** The original support count of every observed item. */
        final Map<Integer, Integer> support = new HashMap<>();
        /** The optional exact cross-transaction TWU map. */
        final Map<Integer, BigInteger> twu;

        /**
         * Create first-pass metadata with optional exact TWU accumulation.
         * @param calculateTWU whether to collect transaction-weighted utilities
         */
        Metadata(boolean calculateTWU) {
            twu = calculateTWU ? new HashMap<>() : null;
        }
    }

    /**
     * Stream validated SPMF utility transactions with contextual input errors.
     * @param input the UTF-8 input path
     * @param visitor the per-transaction consumer
     * @return the number of original transactions read
     * @throws IOException if reading, validation or transaction processing fails
     */
    static int scan(Path input, Visitor visitor) throws IOException {
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.startsWith("\ufeff")) line = line.substring(1);
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("%") || line.startsWith("@")) continue;
                Transaction transaction;
                try {
                    String[] fields = line.split(":", -1);
                    if (fields.length != 3) throw new IllegalArgumentException("Expected items:TU:utilities");
                    String[] items = tokens(fields[0]);
                    String[] utilities = tokens(fields[2]);
                    if (items.length == 0 || items.length != utilities.length) {
                        throw new IllegalArgumentException("A nonempty row needs one utility per item");
                    }
                    long total = Long.parseLong(fields[1].trim());
                    if (total <= 0) throw new IllegalArgumentException("Transaction utility must be positive (no 0/0)");
                    Item[] pairs = new Item[items.length];
                    long sum = 0;
                    for (int i = 0; i < items.length; i++) {
                        int item = Integer.parseInt(items[i]);
                        long utility = Long.parseLong(utilities[i]);
                        if (item < 0 || utility < 0) throw new IllegalArgumentException("Negative item IDs/utilities are unsupported");
                        sum = Math.addExact(sum, utility);
                        pairs[i] = new Item(item, utility);
                    }
                    Arrays.sort(pairs, Comparator.comparingInt(pair -> pair.item));
                    for (int i = 1; i < pairs.length; i++) {
                        if (pairs[i - 1].item == pairs[i].item) throw new IllegalArgumentException("Duplicate item " + pairs[i].item);
                    }
                    if (total < sum) throw new IllegalArgumentException("Transaction utility is smaller than the item utility sum");
                    transaction = new Transaction(pairs, total);
                } catch (IllegalArgumentException | ArithmeticException exception) {
                    throw new IOException(input + ": line " + lineNumber + ": " + exception.getMessage(), exception);
                }
                if (count == Integer.MAX_VALUE) throw new IOException("Too many transactions: " + input);
                visitor.visit(transaction, count++);
            }
        }
        return count;
    }

    /**
     * Split a whitespace-delimited field without creating an empty token.
     * @param field the input field
     * @return the field tokens, or an empty array for a blank field
     */
    private static String[] tokens(String field) {
        String text = field.trim();
        return text.isEmpty() ? new String[0] : text.split("\\s+");
    }

    /**
     * Collect original item supports and optional TWUs in one streaming scan.
     * @param input the original input path
     * @param calculateTWU whether to accumulate exact TWUs
     * @return the first-pass transaction and item metadata
     * @throws IOException if reading or validation fails
     */
    @SuppressWarnings("unused")
	static Metadata metadata(Path input, boolean calculateTWU) throws IOException {
        Metadata metadata = new Metadata(calculateTWU);
        metadata.transactionCount = scan(input, (transaction, tid) -> {
            for (Item pair : transaction.items) {
                metadata.support.merge(pair.item, 1, Math::addExact);
                if (calculateTWU) metadata.twu.merge(pair.item, BigInteger.valueOf(transaction.total), BigInteger::add);
            }
        });
        return metadata;
    }

    /**
     * Order retained items by ascending support or TWU with numeric-ID tie breaking.
     * @param metadata the original item metadata
     * @param minimumSupport the inclusive support threshold for retaining an item
     * @param byTWU whether to order by TWU rather than support
     * @return the retained item identifiers in their fixed mining order
     */
    static int[] order(Metadata metadata, int minimumSupport, boolean byTWU) {
        List<Integer> items = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : metadata.support.entrySet()) {
            if (entry.getValue() >= minimumSupport) items.add(entry.getKey());
        }
        Comparator<Integer> comparator = byTWU
                ? Comparator.comparing(item -> metadata.twu.get(item))
                : Comparator.comparingInt(item -> metadata.support.get(item));
        items.sort(comparator.thenComparingInt(Integer::intValue));
        return items.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Map every retained item to its fixed mining rank.
     * @param order the ordered retained item identifiers
     * @return the item-to-rank lookup table
     */
    static Map<Integer, Integer> ranks(int[] order) {
        Map<Integer, Integer> ranks = new HashMap<>();
        for (int rank = 0; rank < order.length; rank++) ranks.put(order[rank], rank);
        return ranks;
    }

    /**
     * Choose a geometric primitive-column capacity without overflowing integer arithmetic.
     * @param current the current column capacity
     * @param required the smallest capacity needed by the next append
     * @return a capacity no smaller than required
     */
    static int grownCapacity(int current, int required) {
        if (required < 0) throw new IllegalStateException("Too many indexed-list entries");
        return (int) Math.max(required, Math.min(Integer.MAX_VALUE, Math.max(1L, 2L * current)));
    }

    /**
     * Round the canonical decimal support product upward to an inclusive integer count.
     * @param ratio the relative support ratio
     * @param transactions the original transaction count
     * @return the positive absolute support threshold
     */
    static int minimumSupport(double ratio, int transactions) {
        return Math.max(1, BigDecimal.valueOf(ratio).multiply(BigDecimal.valueOf(transactions))
                .setScale(0, RoundingMode.CEILING).intValueExact());
    }

    /** A compensated sum for actual occupancy, not an upper bound. */
    static final class Sum {
        /** The current compensated floating-point sum. */
        private double sum;
        /** The compensation for rounding lost by the previous addition. */
        private double correction;

        /**
         * Accumulate an actual occupancy value using compensated summation.
         * @param value the actual value to add
         */
        void add(double value) {
            double adjusted = value - correction;
            double next = sum + adjusted;
            correction = (next - sum) - adjusted;
            sum = next;
        }

        /**
         * Read the current compensated occupancy sum.
         * @return the compensated sum
         */
        double value() {
            return sum;
        }
    }

    /**
     * Compute a conservative ratio including long-to-double conversion error.
     * @param numerator the nonnegative exact utility numerator
     * @param denominator the positive original transaction utility
     * @return an upward-rounded ratio, or zero for a zero numerator
     */
    static double upperRatio(long numerator, long denominator) {
        if (numerator == 0) return 0;
        return Math.nextUp(Math.nextUp((double) numerator) / Math.nextDown((double) denominator));
    }

    /**
     * Add nonnegative bound values with outward rounding.
     * @param a the first conservative bound
     * @param b the second conservative bound
     * @return an upward-rounded sum
     */
    static double upperAdd(double a, double b) {
        return a == 0 && b == 0 ? 0 : Math.nextUp(a + b);
    }

    /**
     * Average a conservative numerator with outward rounding.
     * @param numerator the nonnegative conservative numerator
     * @param denominator the positive exact support count
     * @return an upward-rounded quotient
     */
    static double upperDivide(double numerator, int denominator) {
        return numerator == 0 ? 0 : Math.nextUp(numerator / denominator);
    }

    /**
     * Sort transaction bounds and average the largest minimum-support values.
     * @param bounds the reusable array of conservative transaction bounds
     * @param size the number of valid bounds in the array prefix
     * @param minimumSupport the number of largest bounds to average
     * @return the conservative top-support average
     */
    static double topBound(double[] bounds, int size, int minimumSupport) {
        Arrays.sort(bounds, 0, size);
        double sum = 0;
        for (int i = 0; i < minimumSupport; i++) sum = upperAdd(sum, bounds[size - 1 - i]);
        return upperDivide(sum, minimumSupport);
    }

    /** Exact occupancy sum, used only very close to an output threshold. */
    static final class Fraction {
        /** The reduced exact sum numerator. */
        private BigInteger numerator = BigInteger.ZERO;
        /** The positive reduced exact sum denominator. */
        private BigInteger denominator = BigInteger.ONE;

        /**
         * Accumulate and reduce an exact occupancy fraction.
         * @param utility the exact itemset utility
         * @param transactionUtility the positive original transaction utility
         * @return this updated exact fraction sum
         */
        Fraction add(long utility, long transactionUtility) {
            BigInteger divisor = BigInteger.valueOf(transactionUtility);
            BigInteger gcd = denominator.gcd(divisor);
            BigInteger left = divisor.divide(gcd);
            BigInteger right = denominator.divide(gcd);
            numerator = numerator.multiply(left).add(BigInteger.valueOf(utility).multiply(right));
            denominator = denominator.multiply(left);
            BigInteger reduce = numerator.gcd(denominator);
            numerator = numerator.divide(reduce);
            denominator = denominator.divide(reduce);
            return this;
        }

        /**
         * Compare an exact mean occupancy with a canonical decimal threshold.
         * @param support the positive number of supporting transactions
         * @param threshold the exact decimal occupancy threshold
         * @return true if the exact mean meets or exceeds the threshold
         */
        boolean averageAtLeast(int support, BigDecimal threshold) {
            BigInteger target = threshold.unscaledValue();
            BigInteger scale = BigInteger.TEN.pow(threshold.scale());
            return numerator.multiply(scale).compareTo(target.multiply(denominator).multiply(BigInteger.valueOf(support))) >= 0;
        }
    }
}
