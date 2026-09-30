package ca.pfv.spmf.experimental.patternexplorer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */
/**
 * The exploration history: an ordered list of query states with a cursor.
 * The states from the root to the last one form the breadcrumb; the cursor
 * can move back and forward, and any state can be revisited instantly.
 * 
 * @author Philippe Fournier-Viger
 */
public class ExplorationHistory {

	private final List<ExplorerQuery> states = new ArrayList<>();
	private int current;

	/** @param root the initial state */
	public ExplorationHistory(ExplorerQuery root) {
		states.add(root);
		current = 0;
	}

	/** @return the current query */
	public ExplorerQuery current() {
		return states.get(current);
	}

	/** @return all states (root first) */
	public List<ExplorerQuery> getStates() {
		return Collections.unmodifiableList(states);
	}

	/** @return index of the current state */
	public int getCurrentIndex() {
		return current;
	}

	/**
	 * Push a new state after the current one (states after the cursor are
	 * discarded).
	 * 
	 * @return false if the new state equals the current one
	 */
	public boolean push(ExplorerQuery query) {
		if (query.equals(current())) {
			return false;
		}
		while (states.size() > current + 1) {
			states.remove(states.size() - 1);
		}
		states.add(query);
		current = states.size() - 1;
		return true;
	}

	public boolean canGoBack() {
		return current > 0;
	}

	public boolean canGoForward() {
		return current < states.size() - 1;
	}

	public void back() {
		if (canGoBack()) {
			current--;
		}
	}

	public void forward() {
		if (canGoForward()) {
			current++;
		}
	}

	/** Move the cursor to a state (keeps the whole history) */
	public void goTo(int index) {
		if (index >= 0 && index < states.size()) {
			current = index;
		}
	}

	/** Clear the history except the root */
	public void reset() {
		ExplorerQuery root = states.get(0);
		states.clear();
		states.add(root);
		current = 0;
	}

	/**
	 * @return a short label describing how the current state differs from the
	 *         previous one, e.g. "+ milk" or "without + bread"
	 */
	public static String stepLabel(ExplorerQuery previous, ExplorerQuery current) {
		if (current.isUnrestricted()) {
			return "All patterns";
		}
		Set<String> prevLabels = labels(previous);
		Set<String> curLabels = labels(current);
		List<String> added = new ArrayList<>();
		for (String label : curLabels) {
			if (!prevLabels.contains(label)) {
				added.add(label);
			}
		}
		if (!added.isEmpty()) {
			return String.join("  ", added);
		}
		List<String> removed = new ArrayList<>();
		for (String label : prevLabels) {
			if (!curLabels.contains(label)) {
				removed.add(label);
			}
		}
		if (!removed.isEmpty()) {
			return "without " + String.join(", ", removed);
		}
		return "\u2026";
	}

	private static Set<String> labels(ExplorerQuery query) {
		Set<String> set = new LinkedHashSet<>();
		for (ExplorerQuery.Restriction r : query.getRestrictions()) {
			set.add(r.getLabel());
		}
		return set;
	}
}