package ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists;

import java.util.ArrayDeque;
/*
 * Copyright Philippe Fournier-Viger, 2026
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
 * A thread-local pool of reusable IDListBitmap instances.
 *
 * @author Philippe Fournier-Viger, 2026
 */
public class IDListBitmapPool {

	/** Per-thread stack of idle, ready-to-reuse IDListBitmap instances. */
	private static final ThreadLocal<ArrayDeque<IDListBitmap>> POOL =
			ThreadLocal.withInitial(ArrayDeque::new);

	/** Private constructor: this class is only meant to be used through its static methods. */
	private IDListBitmapPool() {
	}

	/**
	 * Retrieves a recycled, empty IDListBitmap from the calling thread's pool, or
	 * creates a new one if the pool is currently empty.
	 *
	 * @return an empty IDListBitmap ready to be filled by a join operation
	 */
	public static IDListBitmap acquire() {
		IDListBitmap recycled = POOL.get().poll();
		return recycled != null ? recycled : new IDListBitmap();
	}

	/**
	 * Clears the given IDListBitmap and returns it to the calling thread's pool
	 * so it can be reused by a future join operation.
	 *
	 * @param idList the id-list to recycle; ignored if null
	 */
	public static void release(IDListBitmap idList) {
		if (idList == null) {
			return;
		}
		idList.reset();
		POOL.get().push(idList);
	}

	/**
	 * Empties the calling thread's pool, releasing the held instances for
	 * garbage collection.
	 */
	public static void clear() {
		POOL.get().clear();
	}
}