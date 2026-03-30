package ca.pfv.spmf.experimental.datastructures.cache;

/**
 * This class maintains a pool of Integer objects that can be reused to avoid
 * creating new Integer objects. This is for the case where we want to convert
 * int to Integers. The pool is implemented as an array. 
 * 
 * @author Philippe Fournier-Viger
 *
 */
public class IntToIntegerCache {

	/**
	 * An array to store a mapping from int to the corresponding Integers objects
	 */
	private Integer[] cache;
	
	private final int DEFAULT_SIZE = 200;
	

	public IntToIntegerCache() {
		super();
		cache = new Integer[DEFAULT_SIZE];
	}

	/** Initialize the pool with a given size (if known in advance) */
	public IntToIntegerCache(int size) {
		super();
		cache = new Integer[size+1];
	}

	/**
	 * Convert an int to an Integer object. This function use a pool to make sure
	 * that the same Integer object is reused if the same int object is used
	 * again.
	 * 
	 * @param number an int
	 * @return the Integer object
	 */
	public Integer getInteger(int number) {
		Integer value;
		if(number > cache.length-1) {
			Integer[] newCache = new Integer[number * 2 ];
			System.arraycopy(cache, 0, newCache, 0, cache.length);
			cache = newCache;
			
			value = Integer.valueOf(number);
			cache[number] = value;
		}
		else {
			value = cache[number];
			if(value == null) {
				value = Integer.valueOf(number);
				cache[number] = value;
			}
		}
		return value;
	}

}
