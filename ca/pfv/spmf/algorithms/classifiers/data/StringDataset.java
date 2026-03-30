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
package ca.pfv.spmf.algorithms.classifiers.data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class containing the logic to read dataset in values separated by spaces.
 */
public class StringDataset extends Dataset implements Serializable{
    /**
	 * UID
	 */
	private static final long serialVersionUID = 3107045909585715L;

    /**
     * Name for the class in the dataset 
     */
	private String klass = null;

    /**
     * Constructor
     * 
     * @param path
     * @throws Exception
     */
    public StringDataset(String path, String className) throws Exception {
        super();

        klass = className;
        
        try {
            FileReader fileReader = new FileReader(path);
            BufferedReader bufferedReader = new BufferedReader(fileReader);

            // Read the header line
            String header = bufferedReader.readLine();

            // Obtains the name of the attributes from the header
            String[] nameAttributes = header.split(getSeparator());

            // Used to save the metadata information for all the attributes
            List<List<String>> metadataAttributes = new ArrayList<List<String>>();
            for (int i = 0; i < nameAttributes.length; i++) {
                metadataAttributes.add(new ArrayList<String>());
            }

            List<String[]> lines = new ArrayList<String[]>();
            // Read all the dataset, each different value is saved in metadata
            String line;
            while ((line = bufferedReader.readLine()) != null) {

                String[] values = line.split(getSeparator());

                if (line.isEmpty() || values.length <= 0)
                    continue;

                // For each value for this instance, save it in the metadata information
                for (int i = 0; i < values.length; i++) {
                    if (!metadataAttributes.get(i).contains(values[i]))
                        metadataAttributes.get(i).add(values[i]);
                }

                lines.add(values);
            }

            // Once all the different values have been saved, start iterating to find which
            // kind of attribute
            String[] klassValues = null;
            int klassIndexInFile = -1;
            for (int i = 0; i < metadataAttributes.size(); i++) {

                String nameAttribute = nameAttributes[i];
                String[] values = new String[metadataAttributes.get(i).size()];
                metadataAttributes.get(i).toArray(values);
                Arrays.sort(values);

                if (nameAttribute.contains(klass)) {
                	klassValues = values;
                	klassIndexInFile = i;
                } else {
                    this.addNominalAttribute(nameAttribute, values);
                }
            }
            if(klassValues == null) {
            	bufferedReader.close();
            	throw new Exception("The target attribute \"" + className + "\" that you have chosen does not exist.");
            }
            this.addKlass(klassValues);        

            // After setting metadata, dataset is stored
            for (int i = 0; i < lines.size(); i++) {
            	String[] lineX = lines.get(i);
            	
                // ==== new by Phil ====
                // Reorder the instance so that the target attribute is the last one
            	// before adding the instance to the in-memory dataset
                String[] reorderedLine = new String[lineX.length];
                int currentPosition = 0;
                for(int j=0; j< lineX.length; j++) {
                	if(j != klassIndexInFile) {
                		reorderedLine[currentPosition++] = lineX[j];
                	}
                }
                reorderedLine[reorderedLine.length-1] = lineX[klassIndexInFile];
                // ==== end new by Phil ====
                
            	this.getItemForAttributeValue(reorderedLine);
            }
            
            lines.clear();

            bufferedReader.close();
        } catch (Exception e) {
        	e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * Get the target attribute name
     * @return the name
     */
    public String getKlassName() {
    	return klass;
    }


    /**
     * Get the value separator (empty space)
     * @return the separator
     */
	protected String getSeparator() {
		return " ";
	}


}
