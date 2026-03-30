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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class representing a dataset for classiciation
 */
public abstract class Dataset {
	/**
	 * Array with all the instances
	 */
	protected List<Instance> instances;

	/**
	 * The list of attributes, each with a name and a list of possible values.pw
	 */
	protected List<Attribute> attributes;

	/**
	 * The values that the target class can take
	 */
	protected String[] targetClassValues;

	/**
	 * Map from class name (key) to the frequency of that class in the dataset
	 * (value)
	 */
	protected Map<Short, Long> mapClassToFrequency = null;

	/**
	 * Dictionary used to do the matching between the internal value in short, to
	 * the original value
	 */
	protected Map<Short, String> mapItemToString;

	/**
	 * Array to store the list of items for each attribute.
	 */
	protected List<List<Short>> listAttributeIndexToItems;

	/**
	 * The last ID that was given automatically to an item (as item are represented
	 * by a short value internally)
	 */
	protected short lastGivenID;

	/**
	 * The index of the target class in the attribute list (the last position)
	 */
	protected int indexKlass = -1;

	/**
	 * Variable indicating if the dataset has missing values or not
	 */
	protected boolean hasMissingValue = false;

	/** Dataset name (if any) */
	protected String name = "UNTITLED_DATA";

	/**
	 * Constructor
	 */
	public Dataset() {
		this.lastGivenID = 0; // So that it starts at 1
		this.mapClassToFrequency = null;
		this.mapItemToString = new HashMap<Short, String>();
		this.instances = new ArrayList<Instance>();
		this.attributes = new ArrayList<Attribute>();
		this.listAttributeIndexToItems = new ArrayList<List<Short>>();
	}

	/**
	 * Get the list of attributes (name and possible values) ** Excluding the target
	 * attribute **
	 * 
	 * @return the list of attributes
	 */
	public List<Attribute> getAttributes() {
		return attributes;
	}

	/**
	 * Get the number of classes
	 * 
	 * @return the number of classes
	 */
	public int getClassesCount() {
		return targetClassValues.length;
	}

	/**
	 * Get the list of target class values as strings
	 * 
	 * @return the list of target class values
	 */
	public String[] getTargetClassValues() {
		return targetClassValues;
	}

	/**
	 * Get the list of instances
	 * 
	 * @return the instances
	 */
	public List<Instance> getInstances() {
		return instances;
	}

	/**
	 * Adds an instance to the current dataset
	 * 
	 * @param attributeValues to be added into the dataset
	 * @throws Exception
	 */
	public void getItemForAttributeValue(String[] attributeValues) throws Exception {
		String klass = attributeValues[indexKlass].trim();

		Short internalRepresentationKlass = this.getItemRepresentingKlass(klass);
		Instance instance = new Instance(attributeValues.length);

		for (int i = 0, j = 0; i < attributeValues.length; i++) {
			if (i == this.indexKlass) {
				continue;
			}

			// ------ Nominal attribute
			short internalRepresentation = this.getItemRepresentingAttributeValue(j, attributeValues[i].trim());

			if (internalRepresentation < 0) {
				this.hasMissingValue = true;
			}
			instance.set(j, internalRepresentation);

			Short valueJ = instance.getAttributeValue(j);

			if (!this.mapItemToString.containsKey(valueJ)) {
				this.mapItemToString.put(valueJ, attributeValues[i].trim());
			}
			/// ------
			j++;
		}
		if (internalRepresentationKlass < 0)
			this.hasMissingValue = true;
		instance.setKlass(internalRepresentationKlass);

		if (!this.mapItemToString.containsKey(internalRepresentationKlass)) {
			this.mapItemToString.put(internalRepresentationKlass, klass);
		}

		this.getInstances().add(instance);
	}

	/**
	 * Get the internal representation from an attribute and its value
	 * 
	 * @param indexAttribute index of the attribute
	 * @param value          for the attribute
	 * @return the internal representation used to represent this value in this
	 *         attribute
	 * @throws Exception
	 */
	short getItemRepresentingAttributeValue(int indexAttribute, String value) throws Exception {
		Attribute attribute = this.getAttributes().get(indexAttribute);
		for (int i = 0; i < attribute.getValues().length; i++) {
			if (attribute.getValues()[i].equals(value))
				return this.listAttributeIndexToItems.get(indexAttribute).get(i);
		}
		return -1;
	}

	/**
	 * Get the internal representation for a specified class
	 * 
	 * @param value to obtain the internal representation
	 * @return the internal representation used to the specified parameter
	 */
	short getItemRepresentingKlass(String value) {
		List<Short> klassIndexes = this.listAttributeIndexToItems.get(this.indexKlass);
		for (int i = 0; i < klassIndexes.size(); i++) { // !** do not refactor
			if (targetClassValues[i].equals(value)) {
				return klassIndexes.get(i);
			}
		}
		return -1;
	}

	/**
	 * Add the metadata information for a new nominal attribute
	 * 
	 * @param name   the attribute name
	 * @param values the list of values for that attribute
	 */
	public void addNominalAttribute(String name, String[] values) {
		this.listAttributeIndexToItems.add(new ArrayList<Short>());
		int indexAttribute = this.getAttributes().size();
		for (short i = 1; i <= values.length; i++) {
			this.listAttributeIndexToItems.get(indexAttribute).add(++lastGivenID);
		}
		this.getAttributes().add(new Attribute(name, values));
	}

	/**
	 * Add the metadata information for a new numeric attribute
	 * 
	 * @param nameAttribute for the attribute
	 */
	public void addNumericAttribute(String nameAttribute) {
		// In this version, we just do the same as for a nominal attribute
		// because the classifiers dont support numeric attributes anyway
		Attribute attribute = new Attribute(nameAttribute);
		this.listAttributeIndexToItems.add(new ArrayList<Short>());
		this.getAttributes().add(attribute);
	}

	/**
	 * Get the internal representation for the k-class value
	 * 
	 * @param k position of the value
	 * @return the internal representation for the class
	 */
	public short getKlassAt(int k) {
		return this.listAttributeIndexToItems.get(indexKlass).get(k);
	}

	/**
	 * Get the list of all class values
	 * 
	 * @return the list of class values (as integers - the internal representation)
	 */
	public List<Short> getListOfClassValues() {
		return this.listAttributeIndexToItems.get(indexKlass);
	}

	/**
	 * Get the internal representation for the value specified
	 * 
	 * @param indexAttribute index of the attribute
	 * @param indexValue     index of the value in the attribute
	 * @return the internal representation for this value
	 */
	Short getItemAtOfAttributeAt(int indexAttribute, int indexValue) {
		return this.listAttributeIndexToItems.get(indexAttribute).get(indexValue);
	}

	/**
	 * Get the original value from the internal representation value
	 * 
	 * @param index internal representation
	 * @return the original value for this internal representation
	 */
	public String getStringCorrespondingToItem(short index) {
		return this.mapItemToString.get(index);
	}

	/**
	 * Get a mapping of the internal number representation of items to their string
	 * values
	 * 
	 * @return a map where key = item, value = string
	 */
	public Map<Short, String> getMapItemToString() {
		return mapItemToString;
	}

	/**
	 * Get internal representation for the class specified as parameter
	 * 
	 * @param klass original value to get internal representation
	 * @return the internal representation for this class
	 */
	int getItemOfKlass(String klass) {
		for (int i = 0; i < targetClassValues.length; i++) {
			if (klass.equals(targetClassValues[i]))
				return i;
		}
		return -1;
	}

	/**
	 * Adds the metadata information for the class
	 * 
	 * @param values all the possible values which could take
	 */
	void addKlass(String[] values) {
		this.listAttributeIndexToItems.add(new ArrayList<Short>());

		indexKlass = this.getAttributes().size();

		for (short i = 1; i <= values.length; i++) {
			this.listAttributeIndexToItems.get(indexKlass).add((short) ++lastGivenID);
		}
		targetClassValues = values;
	}

	/**
	 * Get the number of instances per class
	 * 
	 * @param klass to check the number of instances
	 * @return the number of instances for the specified class
	 */
	public long getNumberInstancesPerKlass(Short klass) {
		return this.mapClassToFrequency.getOrDefault(klass, 0l);
	}

	/**
	 * Get frequency by each class. Key are the internal representation, and the
	 * value is the frequency
	 * 
	 * @return the frequency by class
	 */
	public Map<Short, Long> getMapClassToFrequency() {
		if (mapClassToFrequency == null) {
			recalculateClassFrequencies();
		}
		// ****** MAKE A COPY OF THE MAP BECAUSE SOME CLASSIFIER LIKE CBA WILL MODIFY
		// THE MAP !!!!!! ***\\\
		// If we dont do that, it will affect results of other classifiers that are run
		// on the same dataset
		// after this classifier.
		return new HashMap<>(mapClassToFrequency);
	}

	/**
	 * Get the number of distinct items + classes
	 * 
	 * @return the number of distinct items + classes
	 * @throws Exception
	 */
	public int getDistinctItemsCount() {
		int total = this.getClassesCount();
		for (Attribute attribute : getAttributes()) { // Refactored
			total += attribute.getValues().length;
		}
		return total;
	}

	/**
	 * Get an attribute by one of its item
	 * 
	 * @param item the id of an item
	 * @return the attribute containing this value
	 */
	public Attribute getAttributeOfItem(Short item) {
		int indexAttribute = -1;
		boolean found = false;
		for (indexAttribute = 0; indexAttribute < getAttributes().size() && !found; indexAttribute++) {
			for (int j = 0; j < listAttributeIndexToItems.get(indexAttribute).size() && !found; j++) {
				if (listAttributeIndexToItems.get(indexAttribute).get(j) == item) {
					found = true;
				}
			}
		}
		return this.getAttributes().get(indexAttribute - 1);
	}

	/**
	 * Check if current dataset has missing values
	 * 
	 * @return true if some attribute has missing values
	 */
	public boolean hasMissingValue() {
		return this.hasMissingValue;
	}

	/**
	 * Convert an array of strings to an instance
	 * 
	 * @param values an array of values
	 * @return an Instance
	 * @throws Exception if error occurs
	 */
	// By Philippe
	public Instance stringToInstance(String[] values) throws Exception {
		Instance instance = new Instance(values.length);
		for (int i = 0; i < this.getAttributes().size(); i++) {
			Short val = this.getItemRepresentingAttributeValue(i, values[i]);
			instance.set(i, val);
		}
		return instance;
	}

	public void printStats() {
		System.out.println(" Number of attributes : " + this.getAttributes().size());
		System.out.println(" Number of records : " + this.getInstances().size());
		System.out.println(" Class value count: " + targetClassValues.length);
		System.out.println(" Class values: " + Arrays.toString(targetClassValues));
		for (int i = 0; i < this.getAttributes().size(); i++) {
			System.out.println(" Attribute  " + i + ": " + getAttributes().get(i));
		}
	}

	/**
	 * Print internal representation of the dataset
	 * 
	 * @throws Exception
	 */
	public void printInternalRepresentation() {
		System.out.println(" ---- Internal representation of dataset ---- ");
		System.out.println("index class: " + indexKlass);
		System.out.println(" -- Attributes -- ");
		for (Attribute attribute : getAttributes()) {
			System.out.println(attribute.getName());
		}
		System.out.println(" -- Class --");
		for (int i = 0; i < getClassesCount(); i++) {
			System.out.println(getKlassAt(i));
		}
		System.out.println(" -- Instances -- ");
		for (int i = 0; i < getInstances().size(); i++) {
			System.out.println(getInstances().get(i));
		}
	}

	public void printStringRepresentation() {
		System.out.println(" ---- String representation of dataset ---- ");
		System.out.println(" -- Attributes -- ");
		for (Attribute attribute : getAttributes()) {
			System.out.println(attribute.getName() + " " + Arrays.toString(attribute.getValues()));
		}
		System.out.println(" -- Class --");
		for (short i = 0; i < getClassesCount(); i++) {
			System.out.println(targetClassValues[i]);
		}
		System.out.println(" -- Instances -- ");
		for (Instance instance : getInstances()) {
			StringBuilder buffer = new StringBuilder();
			for (Short value : instance.getItems()) {
				buffer.append(getStringCorrespondingToItem(value));
				buffer.append(" ");
			}
			System.out.println(buffer.toString());
		}
	}

	/**
	 * Get the name of this dataset
	 * 
	 * @return the name
	 */
	String getName() {
		return name;
	}

	/**
	 * Set the name of this dataset
	 * 
	 * @param name a name as a String
	 */
	void setName(String name) {
		this.name = name;
	}

	protected void recalculateClassFrequencies() {
		this.mapClassToFrequency = new HashMap<Short, Long>();
		for (Instance instance : instances) {
			Short klass = instance.getKlass();
			Long val = mapClassToFrequency.get(klass);
			if (val == null) {
				mapClassToFrequency.put(klass, 1L);
			} else {
				mapClassToFrequency.put(klass, val + 1);
			}
		}
	}

}
