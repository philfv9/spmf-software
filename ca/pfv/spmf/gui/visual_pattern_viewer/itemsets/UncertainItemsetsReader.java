package ca.pfv.spmf.gui.visual_pattern_viewer.itemsets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 */
/**
 * Class to read uncertain itemsets from a file in SPMF format with their measures
 * @author Philippe Fournier-Viger
 */
public class UncertainItemsetsReader extends ItemsetsReader {

	public UncertainItemsetsReader(String filePath) throws IOException {
		super(filePath);
	}
	
	/**
	 * Parse a line containing an (uncertain) itemset in SPMF format,
	 * splitting on ") " so that "1 (0.1) 2 (0.4)" → ["1 (0.1", "2 (0.4)"].
	 */
	public List<String> parseItemset(String line, Map<String, String> itemMapping) {
	    List<String> itemsetsItems = new ArrayList<>();

	    int startPos = 0;
	    int endPos = 0;
	    while((endPos = line.indexOf(')', startPos)) != -1 ){
	    	String token = line.substring(startPos, endPos+1);

	    	itemsetsItems.add(token);
	    	startPos = endPos+1;
	    }

	    return itemsetsItems;
	}

}
