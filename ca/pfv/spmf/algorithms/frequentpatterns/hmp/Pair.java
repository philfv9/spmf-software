package ca.pfv.spmf.algorithms.frequentpatterns.hmp;

class Pair{

	int[] pattern;
	int count;
	
	/**
	 * @param sparseTriangularMatrix
	 */
	Pair() {
	}
	public Pair(int[] patternArray, int countPattern) {
		pattern= patternArray;
		this.count = countPattern;
	}
}