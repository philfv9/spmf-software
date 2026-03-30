package ca.pfv.spmf.gui.pattern_diff_analyzer;
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
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * Configuration options for contrast pattern computation.
 * Uses builder pattern for easy construction.
 * 
 * @author Philippe Fournier-Viger
 */
public class ContrastOptions {

    /** Contrast method options */
    public static final String[] CONTRAST_METHODS = {
        "Absolute Difference",
        "Directional: A > B",
        "Directional: B > A",
        "Relative Ratio (A/B)",
        "Relative Ratio (B/A)",
        "Minimum Interval Gap",
        "Exclusive in File 1",
        "Exclusive in File 2",
        "Symmetric Difference",
        "Fold Change",
        "Top-K Contrast"
    };

    /** Pattern matching method options */
    public static final String[] PATTERN_MATCHING_METHODS = {
        "Exact itemset match",
        "Itemset match (ignore order)",
        "String match (raw)"
    };

    // ==================== Fields ====================

    /** The contrast computation method */
    private String contrastMethod;
    
    /** The pattern matching method */
    private String matchingMethod;
    
    /** Measure column name for file 1 */
    private String measureName1;
    
    /** Measure column name for file 2 */
    private String measureName2;
    
    /** Threshold value for filtering */
    private double threshold;
    
    /** Minimum gap for interval method */
    private double minGap;
    
    /** Maximum gap for interval method */
    private double maxGap;
    
    /** Top-K value */
    private int topK;
    
    /** Whether to treat missing values as zero */
    private boolean treatMissingAsZero;

    // ==================== Constructors ====================

    /**
     * Default constructor with sensible defaults.
     */
    public ContrastOptions() {
        this.contrastMethod = CONTRAST_METHODS[0];
        this.matchingMethod = PATTERN_MATCHING_METHODS[0];
        this.threshold = 0.0;
        this.minGap = 0.0;
        this.maxGap = Double.MAX_VALUE;
        this.topK = 10;
        this.treatMissingAsZero = true;
    }

    /**
     * Copy constructor.
     * @param other the options to copy
     */
    public ContrastOptions(ContrastOptions other) {
        this.contrastMethod = other.contrastMethod;
        this.matchingMethod = other.matchingMethod;
        this.measureName1 = other.measureName1;
        this.measureName2 = other.measureName2;
        this.threshold = other.threshold;
        this.minGap = other.minGap;
        this.maxGap = other.maxGap;
        this.topK = other.topK;
        this.treatMissingAsZero = other.treatMissingAsZero;
    }

    // ==================== Builder Pattern ====================

    /**
     * Create a new builder.
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for ContrastOptions.
     */
    public static class Builder {
        private final ContrastOptions options;

        public Builder() {
            this.options = new ContrastOptions();
        }

        public Builder contrastMethod(String method) {
            options.contrastMethod = method;
            return this;
        }

        public Builder matchingMethod(String method) {
            options.matchingMethod = method;
            return this;
        }

        public Builder measureName1(String name) {
            options.measureName1 = name;
            return this;
        }

        public Builder measureName2(String name) {
            options.measureName2 = name;
            return this;
        }

        public Builder threshold(double threshold) {
            options.threshold = threshold;
            return this;
        }

        public Builder minGap(double minGap) {
            options.minGap = minGap;
            return this;
        }

        public Builder maxGap(double maxGap) {
            options.maxGap = maxGap;
            return this;
        }

        public Builder topK(int topK) {
            options.topK = topK;
            return this;
        }

        public Builder treatMissingAsZero(boolean treat) {
            options.treatMissingAsZero = treat;
            return this;
        }

        public ContrastOptions build() {
            return new ContrastOptions(options);
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Check if the current method requires a threshold.
     * @return true if threshold is needed
     */
    public boolean requiresThreshold() {
        if (contrastMethod == null) return false;
        
        switch (contrastMethod) {
            case "Minimum Interval Gap":
            case "Top-K Contrast":
            case "Exclusive in File 1":
            case "Exclusive in File 2":
            case "Symmetric Difference":
                return false;
            default:
                return true;
        }
    }

    /**
     * Check if the current method requires gap parameters.
     * @return true if gap parameters are needed
     */
    public boolean requiresGapParameters() {
        return "Minimum Interval Gap".equals(contrastMethod);
    }

    /**
     * Check if the current method requires top-K parameter.
     * @return true if top-K is needed
     */
    public boolean requiresTopK() {
        return "Top-K Contrast".equals(contrastMethod);
    }

    /**
     * Check if the current method is an exclusive/difference method.
     * @return true if exclusive method
     */
    public boolean isExclusiveMethod() {
        if (contrastMethod == null) return false;
        
        switch (contrastMethod) {
            case "Exclusive in File 1":
            case "Exclusive in File 2":
            case "Symmetric Difference":
                return true;
            default:
                return false;
        }
    }

    // ==================== Getters and Setters ====================

    public String getContrastMethod() {
        return contrastMethod;
    }

    public void setContrastMethod(String contrastMethod) {
        this.contrastMethod = contrastMethod;
    }

    public String getMatchingMethod() {
        return matchingMethod;
    }

    public void setMatchingMethod(String matchingMethod) {
        this.matchingMethod = matchingMethod;
    }

    public String getMeasureName1() {
        return measureName1;
    }

    public void setMeasureName1(String measureName1) {
        this.measureName1 = measureName1;
    }

    public String getMeasureName2() {
        return measureName2;
    }

    public void setMeasureName2(String measureName2) {
        this.measureName2 = measureName2;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public double getMinGap() {
        return minGap;
    }

    public void setMinGap(double minGap) {
        this.minGap = minGap;
    }

    public double getMaxGap() {
        return maxGap;
    }

    public void setMaxGap(double maxGap) {
        this.maxGap = maxGap;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public boolean isTreatMissingAsZero() {
        return treatMissingAsZero;
    }

    public void setTreatMissingAsZero(boolean treatMissingAsZero) {
        this.treatMissingAsZero = treatMissingAsZero;
    }

    @Override
    public String toString() {
        return "ContrastOptions{" +
            "contrastMethod='" + contrastMethod + '\'' +
            ", matchingMethod='" + matchingMethod + '\'' +
            ", measureName1='" + measureName1 + '\'' +
            ", measureName2='" + measureName2 + '\'' +
            ", threshold=" + threshold +
            ", minGap=" + minGap +
            ", maxGap=" + maxGap +
            ", topK=" + topK +
            ", treatMissingAsZero=" + treatMissingAsZero +
            '}';
    }
}