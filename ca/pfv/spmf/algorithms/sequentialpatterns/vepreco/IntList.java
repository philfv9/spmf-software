package ca.pfv.spmf.algorithms.sequentialpatterns.vepreco;

import java.util.Arrays;
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
 * Copyright (c) Chen Hui
 *
 * Do not remove copyright and license information.
 */
/**
 * Minimal growable int list to avoid boxing overhead of ArrayList<Integer>.
 *
 * @author Chen Hui
 */
final class IntList {

    /** Internal array containing the list values. */
    int[] data;

    /** Number of valid values in data. */
    int size;

    /**
     * Creates an empty list with the given initial capacity.
     *
     * @param cap the initial capacity
     */
    IntList(int cap) {
        data = new int[Math.max(cap, 2)];
        size = 0;
    }

    /**
     * Adds a value at the end of this list.
     *
     * @param v the value to add
     */
    void add(int v) {
        if (size == data.length) {
            data = Arrays.copyOf(data, size * 2 + 2);
        }
        data[size++] = v;
    }

    /**
     * Returns the value at the given position.
     *
     * @param i the position
     * @return the value at that position
     */
    int get(int i) {
        return data[i];
    }

    /**
     * Returns the last value of this list.
     *
     * @return the last value
     */
    int last() {
        return data[size - 1];
    }

    /**
     * Returns the first value of this list.
     *
     * @return the first value
     */
    int first() {
        return data[0];
    }
}