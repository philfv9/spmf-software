/* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* @copyright SPMF 2021
*
* SPMF is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* SPMF is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with SPMF.  If not, see <http://www.gnu.org/licenses/>.
*/
package ca.pfv.spmf.algorithms.classifiers.data;

import java.io.Serializable;

/**
 * Class containing the logic to read dataset in values separated by commas .
 * (CSV file = Comma Separated Values file)
 * @author Philippe Fournier-Viger
 */
public class CSVDataset extends StringDataset implements Serializable{
    /**
	 * UID
	 */
	private static final long serialVersionUID = 3107046569585715L;

    /**
     * Constructor
     * 
     * @param path
     * @throws Exception */
    public CSVDataset(String path, String className) throws Exception {
        super(path, className);
        
    }
    
    /**
     * Get the value separator (comma)
     * @return the separator
     */
	protected  String getSeparator() {
		return "[,]";
	}
}
