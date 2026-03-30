package ca.pfv.spmf.experimental.datastructures.cache;

import java.util.HashMap;
import java.util.Map;

/**
 * This class maintains a pool of Integer objects that can be reused to avoid
 * creating new Integer objects. This is for the case where we want to convert
 * String to Integers. The pool is implemented as a Map. This class can be
 * useful as an alternative to the cache of the Integer class. The cache in the
 * Integer clas is used by Integer.valueOf() but it is mostly limited to caching
 * numbers between -128 to +127 (may vary depending on the JVM.
 * 
 * @author Philippe Fournier-Viger, Zevin Shaul 2022
 *
 */
public class StringToIntegerCache {

	/**
	 * A map to store a mapping from String to the corresponding Integers objects
	 */
	private Map<String, Integer> mapStringToIntegers;

	public StringToIntegerCache() {
		super();
		mapStringToIntegers = new HashMap<String, Integer>();
	}

	/** Initialize the pool with a given size (if known in advance) */
	public StringToIntegerCache(int size) {
		super();
		mapStringToIntegers = new HashMap<String, Integer>(size);
	}

	/**
	 * Convert a String to an Integer object. This function use a pool to make sure
	 * that the same Integer object is reused if the same Integer object is created
	 * again.
	 * 
	 * @param string a String
	 * @return the Integer object
	 */
	public Integer getInteger(String string) {

		// Get the previous integer value
		Integer value = mapStringToIntegers.get(string);
		// If null, it means we have never seen that String before
		if (value == null) {
			// So we create a new integer and put it in the map
			// so that we can reuse that Integer object next time.
			value = Integer.valueOf(string);
			mapStringToIntegers.put(string, value);
		}
		return value;
	}

}
