package ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists;
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
 * Small helper that groups the id-list reuse pools of every IDList
 * implementation, so that an algorithm class does not need to know which
 * concrete IDList implementation was actually used by the database (bitmap,
 * fat bitmap, or standard map) in order to release pooled resources when it is
 * done running.
 *
 * Copyright Philippe Fournier-Viger, 2026
 */
public class IDListPools {

	/** Private constructor: this class is only meant to be used through its static methods. */
	private IDListPools() {
	}

	/**
	 * Empties the calling thread's pools for every pooled IDList implementation.
	 */
	public static void clearAll() {
		IDListBitmapPool.clear();
		IDListFatBitmapPool.clear();
		IDListStandardMapPool.clear();
	}
}