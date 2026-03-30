package ca.pfv.spmf.algorithms.sequentialpatterns.mapd_owsp;

import java.util.Map;

/**
 * QPattern class
 */
class QPattern {
	private String pattern;
	private Map<Integer, Integer> suffIndex;

	public Map<Integer, Integer> getSuffIndex() {
		return suffIndex;
	}

	public void setSuffIndex(Map<Integer, Integer> suffIndex) {
		this.suffIndex = suffIndex;
	}

	public String getPattern() {
		return pattern;
	}

	public void setPattern(String pattern) {
		this.pattern = pattern;
	}
}