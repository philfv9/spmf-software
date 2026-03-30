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
 * Represents a single contrast pattern result.
 * Immutable data transfer object.
 * 
 * @author Philippe Fournier-Viger
 */
public class ContrastResult {

    /** The pattern string */
    private final String pattern;
    
    /** Value from file A (may be null if pattern not in A) */
    private final Double valueA;
    
    /** Value from file B (may be null if pattern not in B) */
    private final Double valueB;
    
    /** The computed contrast value */
    private final double contrastValue;
    
    /** The type/method of contrast */
    private final String contrastType;

    /**
     * Constructor.
     * @param pattern the pattern string
     * @param valueA value from file A
     * @param valueB value from file B
     * @param contrastValue the computed contrast
     * @param contrastType the contrast type/method
     */
    public ContrastResult(String pattern, Double valueA, Double valueB,
            double contrastValue, String contrastType) {
        this.pattern = pattern;
        this.valueA = valueA;
        this.valueB = valueB;
        this.contrastValue = contrastValue;
        this.contrastType = contrastType;
    }

    /**
     * Check if pattern exists in file A.
     * @return true if pattern has value from A
     */
    public boolean existsInA() {
        return valueA != null;
    }

    /**
     * Check if pattern exists in file B.
     * @return true if pattern has value from B
     */
    public boolean existsInB() {
        return valueB != null;
    }

    /**
     * Check if pattern exists in both files.
     * @return true if pattern has values from both
     */
    public boolean existsInBoth() {
        return valueA != null && valueB != null;
    }

    /**
     * Check if this is an exclusive pattern (in one file only).
     * @return true if exclusive
     */
    public boolean isExclusive() {
        return (valueA == null) != (valueB == null);
    }

    // ==================== Getters ====================

    public String getPattern() {
        return pattern;
    }

    public Double getValueA() {
        return valueA;
    }

    public Double getValueB() {
        return valueB;
    }

    public double getContrastValue() {
        return contrastValue;
    }

    public String getContrastType() {
        return contrastType;
    }

    @Override
    public String toString() {
        return "ContrastResult{" +
            "pattern='" + pattern + '\'' +
            ", valueA=" + valueA +
            ", valueB=" + valueB +
            ", contrastValue=" + contrastValue +
            ", contrastType='" + contrastType + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        ContrastResult that = (ContrastResult) o;
        
        if (Double.compare(that.contrastValue, contrastValue) != 0) return false;
        if (!pattern.equals(that.pattern)) return false;
        if (valueA != null ? !valueA.equals(that.valueA) : that.valueA != null) return false;
        if (valueB != null ? !valueB.equals(that.valueB) : that.valueB != null) return false;
        return contrastType != null ? contrastType.equals(that.contrastType) : that.contrastType == null;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = pattern.hashCode();
        result = 31 * result + (valueA != null ? valueA.hashCode() : 0);
        result = 31 * result + (valueB != null ? valueB.hashCode() : 0);
        temp = Double.doubleToLongBits(contrastValue);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (contrastType != null ? contrastType.hashCode() : 0);
        return result;
    }
}