package ca.pfv.spmf.algorithms.classifiers.data;
/* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* It was obtained from the LAC library under the GNU GPL license and adapted for SPMF.
* @Copyright original version LAC 2019   @copyright of modifications SPMF 2021
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
* 
*/
import java.io.Serializable;

/**
 * Attribute contained into the dataset
 */
public class Attribute implements Serializable {
    /**
	 * UID
	 */
	private static final long serialVersionUID = 5528200138881846736L;

	/**
     * Name of the attribute
     */
    private String name = null;

    /**
     * Values that this attribute can take
     */
    private String[] values;

    /**
     * Constructor
     * 
     * @param name of the attribute
     * @param data.type of the attribute
     */
    public Attribute(String name) {
        this.name = name;
    }

    /**
     * Constructor
     * 
     * @param name   of the attribute
     * @param values possible values which could take this attribute
     */
    public Attribute(String name, String[] values) {
    	this.name = name;
        this.values = values;
    }

    /**
     * Get the values for this attribute
     * 
     * @return the values for the attribute
     */
    public String[] getValues() {
        return this.values;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    public String toString() {
        if (this.values != null) {
            return "name= " + this.name + "  values= " + String.join(",", this.values);
        } else {
            return "name=  " + this.name;
        }
    }

    /**
     * Get the name of the attribute
     * 
     * @return the name of the attribute
     */
    public String getName() {
        return this.name;
    }
}
