package ca.pfv.spmf.experimental.patternexplorer;

import java.util.Collection;

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
 * Actions that the panels of the Interactive Pattern Explorer can trigger.
 * Implemented by {@link PatternExplorerViewer}. Every action produces a new
 * query state that is pushed on the exploration history.
 * 
 * @author Philippe Fournier-Viger
 */
public interface ExplorerController {

	/** @return the current query */
	ExplorerQuery getCurrentQuery();

	/** Replace the current query by the given one (pushed in the history) */
	void applyQuery(ExplorerQuery query);

	/** Add required items to the query */
	void requireItems(Collection<String> items);

	/** Add excluded items to the query */
	void excludeItems(Collection<String> items);

	/** Keep only patterns whose items are all among the given items */
	void restrictWithin(Collection<String> items);

	/** Apply a text search (blank text removes the text restriction) */
	void applyTextSearch(String text);

	/** Restrict to a given pattern size, or remove the restriction if already set */
	void toggleSize(int size);

	/** Restrict a numeric measure to a range */
	void setMeasureRange(String measure, double min, double max);

	/** Open the details dialog of a pattern */
	void showPatternDetails(int patternIndex);
}