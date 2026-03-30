package ca.pfv.spmf.datastructures.bitsetpool;

import java.util.Arrays;
import java.util.BitSet;

/* This file is copyright (c) 2008-2024 Philippe Fournier-Viger
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
 * Pool of reusable BitSets to minimize allocation overhead in frequent
 * pattern mining algorithms. It is used by some algorithms such as GenMax.
 * 
 * @see BitSet
 * @author Philippe Fournier-Viger
 */
public class BitSetPool {
    /** Default pool size */
    private static final int DEFAULT_POOL_SIZE = 32;
    
    /** Pool of reusable BitSets */
    private BitSet[] pool;
    
    /** Tracks which pool slots are in use */
    private boolean[] inUse;
    
    /** Next free pool index hint */
    private int nextFree;
    
    /** Default BitSet size for pooled instances (0 means no fixed size) */
    protected final int bitSetSize;
    
    /** Initial pool size for reset */
    private final int initialPoolSize;
    
    /**
     * Creates a pool with default size and variable BitSet sizes.
     */
    public BitSetPool() {
        this(DEFAULT_POOL_SIZE, 0);
    }
    
    /**
     * Creates a pool with specified capacity and variable BitSet sizes.
     * 
     * @param poolSize number of BitSets in pool
     */
    public BitSetPool(int poolSize) {
        this(poolSize, 0);
    }
    
    /**
     * Creates a pool with specified capacity and BitSet size.
     * 
     * @param poolSize number of BitSets in pool
     * @param bitSetSize size for BitSet instances (0 for default/variable size)
     */
    public BitSetPool(int poolSize, int bitSetSize) {
        this.bitSetSize = bitSetSize;
        this.initialPoolSize = poolSize;
        this.pool = new BitSet[poolSize];
        this.inUse = new boolean[poolSize];
        this.nextFree = 0;
        
        for (int i = 0; i < poolSize; i++) {
            pool[i] = instantiateNewBitSet();
            inUse[i] = false;
        }
    }
    
    /**
     * Instantiate a new BitSet. Can be overridden by subclasses.
     * 
     * @return a new BitSet instance
     */
    protected BitSet instantiateNewBitSet() {
        if (bitSetSize > 0) {
            return new BitSet(bitSetSize);
        }
        return new BitSet();
    }
    
    /**
     * Obtain a BitSet that is unused from the pool or create a new one
     * if none are available.
     * 
     * @return a cleared BitSet ready for use
     */
    public BitSet acquire() {
        // Try to find free slot starting from hint
        for (int i = 0; i < pool.length; i++) {
            int index = (nextFree + i) % pool.length;
            if (!inUse[index]) {
                inUse[index] = true;
                nextFree = (index + 1) % pool.length;
                pool[index].clear();
                return pool[index];
            }
        }
        
        // Pool exhausted - create new temporary BitSet
        return instantiateNewBitSet();
    }
    
    /**
     * Releases a BitSet back to the pool (alias for releaseBitSet).
     * Silently ignores BitSets not from this pool.
     * 
     * @param bitSet the BitSet to release
     */
    public void release(BitSet bitSet) {
        releaseBitSet(bitSet);
    }
    
    /**
     * Put an unused BitSet in the pool so that it can be reused.
     * Silently ignores BitSets not from this pool.
     * 
     * @param bitSet the BitSet to release
     */
    public void releaseBitSet(BitSet bitSet) {
        if (bitSet == null) return;
        
        for (int i = 0; i < pool.length; i++) {
            if (pool[i] == bitSet) {
                inUse[i] = false;
                return;
            }
        }
        // Not from this pool - ignore (will be GC'd)
    }
    
    /**
     * Creates a dedicated BitSet not managed by the pool.
     * Useful for long-lived BitSets that shouldn't be recycled.
     * 
     * @return new BitSet with pool's default size
     */
    public BitSet createDedicated() {
        return instantiateNewBitSet();
    }
    
    /**
     * Gets pool utilization statistics.
     * 
     * @return number of currently acquired BitSets
     */
    public int getActiveCount() {
        int count = 0;
        for (boolean used : inUse) {
            if (used) count++;
        }
        return count;
    }
    
    /**
     * Resets the pool, marking all BitSets as available.
     * WARNING: Only call when certain no references exist!
     */
    public void reset() {
        Arrays.fill(inUse, false);
        nextFree = 0;
    }
    
    /**
     * Reset the pool and empty everything so that it can be used as a new pool.
     * Alias for reset() to maintain backward compatibility.
     */
    public void clear() {
        reset();
    }
    
    /**
     * Method for debugging that prints pool information in the console.
     */
    public void printPoolInformation() {
        System.out.println(" POOL: activeCount = " + getActiveCount() + 
                           " poolSize = " + pool.length + 
                           " Pool array = " + Arrays.toString(pool));
    }
}