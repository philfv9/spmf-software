package ca.pfv.spmf.gui.pattern_diff_analyzer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Engine for computing contrast patterns between two pattern datasets.
 * Contains all contrast computation algorithms.
 * 
 * @author Philippe Fournier-Viger
 */
public class ContrastEngine {

    /** Results storage */
    private List<ContrastResult> results;
    
    /** Computation statistics */
    private long computationTimeMs;
    private int matchedPatternCount;
   
    /**
     * Default constructor.
     */
    public ContrastEngine() {
        this.results = new ArrayList<>();
    }

    /**
     * Compute contrast patterns between two datasets.
     * @param dataA the first pattern dataset
     * @param dataB the second pattern dataset
     * @param options the contrast options
     * @return list of contrast results
     */
    public List<ContrastResult> computeContrast(PatternData dataA, PatternData dataB, 
            ContrastOptions options) {
        
        long startTime = System.currentTimeMillis();
        results = new ArrayList<>();

        // Rebuild pattern index maps with current matching method
        dataA.rebuildPatternIndexMap(options.getMatchingMethod());
        dataB.rebuildPatternIndexMap(options.getMatchingMethod());

        // Get measure column indices
        int measureIndex1 = dataA.getColumnIndex(options.getMeasureName1());
        int measureIndex2 = dataB.getColumnIndex(options.getMeasureName2());

        // Get all unique patterns from both files
        Set<String> allPatterns = new LinkedHashSet<>();
        allPatterns.addAll(dataA.getPatternIndexMap().keySet());
        allPatterns.addAll(dataB.getPatternIndexMap().keySet());

        // Process based on contrast method
        String method = options.getContrastMethod();

        switch (method) {
            case "Exclusive in File 1":
            case "Disappearing Patterns (in A, not B)":
                computeExclusivePatterns(dataA, dataB, measureIndex1, true);
                break;
            case "Exclusive in File 2":
            case "Novelty Patterns (in B, not A)":
                computeExclusivePatterns(dataA, dataB, measureIndex2, false);
                break;
            case "Symmetric Difference":
                computeSymmetricDifference(dataA, dataB, measureIndex1, measureIndex2);
                break;
            case "Top-K Contrast":
                computeTopKContrast(dataA, dataB, allPatterns, measureIndex1, measureIndex2, options);
                break;
            default:
                computeStandardContrast(dataA, dataB, allPatterns, measureIndex1, measureIndex2, options);
                break;
        }

        // Calculate statistics
        computationTimeMs = System.currentTimeMillis() - startTime;
        matchedPatternCount = countMatchedPatterns(dataA, dataB);

        return results;
    }

    /**
     * Compute exclusive patterns (in one file but not the other).
     * @param dataA data from file A
     * @param dataB data from file B
     * @param measureIndex the measure column index
     * @param fromA true to find patterns in A not in B, false for opposite
     */
    private void computeExclusivePatterns(PatternData dataA, PatternData dataB,
            int measureIndex, boolean fromA) {
        
        Map<String, Integer> sourceMap = fromA 
            ? dataA.getPatternIndexMap() : dataB.getPatternIndexMap();
        Map<String, Integer> targetMap = fromA 
            ? dataB.getPatternIndexMap() : dataA.getPatternIndexMap();
        PatternData sourceData = fromA ? dataA : dataB;

        for (Map.Entry<String, Integer> entry : sourceMap.entrySet()) {
            if (!targetMap.containsKey(entry.getKey())) {
                int rowIndex = entry.getValue();
                String pattern = sourceData.getPattern(rowIndex);
                Double value = sourceData.getNumericValue(rowIndex, measureIndex);
                double contrastValue = value != null ? value : 0.0;

                results.add(new ContrastResult(
                    pattern,
                    fromA ? value : null,
                    fromA ? null : value,
                    contrastValue,
                    fromA ? "Exclusive in A" : "Exclusive in B"
                ));
            }
        }
    }

    /**
     * Compute symmetric difference patterns.
     * @param dataA data from file A
     * @param dataB data from file B
     * @param measureIndex1 measure index for file A
     * @param measureIndex2 measure index for file B
     */
    private void computeSymmetricDifference(PatternData dataA, PatternData dataB,
            int measureIndex1, int measureIndex2) {
        
        Map<String, Integer> mapA = dataA.getPatternIndexMap();
        Map<String, Integer> mapB = dataB.getPatternIndexMap();

        // Patterns only in file A
        for (Map.Entry<String, Integer> entry : mapA.entrySet()) {
            if (!mapB.containsKey(entry.getKey())) {
                int rowIndex = entry.getValue();
                String pattern = dataA.getPattern(rowIndex);
                Double value = dataA.getNumericValue(rowIndex, measureIndex1);
                
                results.add(new ContrastResult(
                    pattern, value, null,
                    value != null ? value : 0.0, "Only in A"));
            }
        }

        // Patterns only in file B
        for (Map.Entry<String, Integer> entry : mapB.entrySet()) {
            if (!mapA.containsKey(entry.getKey())) {
                int rowIndex = entry.getValue();
                String pattern = dataB.getPattern(rowIndex);
                Double value = dataB.getNumericValue(rowIndex, measureIndex2);
                
                results.add(new ContrastResult(
                    pattern, null, value,
                    value != null ? value : 0.0, "Only in B"));
            }
        }
    }

    /**
     * Compute top-K contrast patterns.
     * @param dataA data from file A
     * @param dataB data from file B
     * @param allPatterns set of all normalized patterns
     * @param measureIndex1 measure index for file A
     * @param measureIndex2 measure index for file B
     * @param options contrast options
     */
    private void computeTopKContrast(PatternData dataA, PatternData dataB,
            Set<String> allPatterns, int measureIndex1, int measureIndex2,
            ContrastOptions options) {
        
        int topK = options.getTopK();
        if (topK <= 0) {
            topK = 10;
        }

        List<ContrastResult> allResults = new ArrayList<>();
        Map<String, Integer> mapA = dataA.getPatternIndexMap();
        Map<String, Integer> mapB = dataB.getPatternIndexMap();

        for (String normalizedPattern : allPatterns) {
            Integer idxA = mapA.get(normalizedPattern);
            Integer idxB = mapB.get(normalizedPattern);

            Double valueA = null;
            Double valueB = null;
            String pattern = normalizedPattern;

            if (idxA != null) {
                pattern = dataA.getPattern(idxA);
                valueA = dataA.getNumericValue(idxA, measureIndex1);
            }
            if (idxB != null) {
                if (idxA == null) {
                    pattern = dataB.getPattern(idxB);
                }
                valueB = dataB.getNumericValue(idxB, measureIndex2);
            }

            double vA = (valueA != null) ? valueA
                : (options.isTreatMissingAsZero() ? 0.0 : Double.NaN);
            double vB = (valueB != null) ? valueB
                : (options.isTreatMissingAsZero() ? 0.0 : Double.NaN);

            if (!Double.isNaN(vA) && !Double.isNaN(vB)) {
                double contrast = Math.abs(vA - vB);
                allResults.add(new ContrastResult(pattern, valueA, valueB, contrast, "Top-K"));
            }
        }

        // Sort by contrast value descending
        allResults.sort((a, b) -> Double.compare(b.getContrastValue(), a.getContrastValue()));

        // Take top K
        int limit = Math.min(topK, allResults.size());
        for (int i = 0; i < limit; i++) {
            results.add(allResults.get(i));
        }
    }

    /**
     * Compute standard contrast (difference, ratio, etc.).
     * @param dataA data from file A
     * @param dataB data from file B
     * @param allPatterns set of all normalized patterns
     * @param measureIndex1 measure index for file A
     * @param measureIndex2 measure index for file B
     * @param options contrast options
     */
    private void computeStandardContrast(PatternData dataA, PatternData dataB,
            Set<String> allPatterns, int measureIndex1, int measureIndex2,
            ContrastOptions options) {
        
        String method = options.getContrastMethod();
        double threshold = options.getThreshold();
        double minGap = options.getMinGap();
        double maxGap = options.getMaxGap();
        
        Map<String, Integer> mapA = dataA.getPatternIndexMap();
        Map<String, Integer> mapB = dataB.getPatternIndexMap();

        for (String normalizedPattern : allPatterns) {
            Integer idxA = mapA.get(normalizedPattern);
            Integer idxB = mapB.get(normalizedPattern);

            // Skip if pattern doesn't exist in either file
            if (idxA == null && idxB == null) {
                continue;
            }

            Double valueA = null;
            Double valueB = null;
            String pattern = normalizedPattern;

            if (idxA != null) {
                pattern = dataA.getPattern(idxA);
                valueA = dataA.getNumericValue(idxA, measureIndex1);
            }
            if (idxB != null) {
                if (idxA == null) {
                    pattern = dataB.getPattern(idxB);
                }
                valueB = dataB.getNumericValue(idxB, measureIndex2);
            }

            // Handle missing values
            double vA = (valueA != null) ? valueA
                : (options.isTreatMissingAsZero() ? 0.0 : Double.NaN);
            double vB = (valueB != null) ? valueB
                : (options.isTreatMissingAsZero() ? 0.0 : Double.NaN);

            if (Double.isNaN(vA) || Double.isNaN(vB)) {
                continue;
            }

            Double contrastValue = null;
            boolean passes = false;

            switch (method) {
                case "Absolute Difference":
                    contrastValue = Math.abs(vA - vB);
                    passes = contrastValue >= threshold;
                    break;
                case "Directional: A > B":
                    contrastValue = vA - vB;
                    passes = contrastValue >= threshold;
                    break;
                case "Directional: B > A":
                    contrastValue = vB - vA;
                    passes = contrastValue >= threshold;
                    break;
                case "Relative Ratio (A/B)":
                    if (vB != 0) {
                        contrastValue = vA / vB;
                        passes = contrastValue >= threshold;
                    }
                    break;
                case "Relative Ratio (B/A)":
                    if (vA != 0) {
                        contrastValue = vB / vA;
                        passes = contrastValue >= threshold;
                    }
                    break;
                case "Minimum Interval Gap":
                    contrastValue = Math.abs(vA - vB);
                    passes = contrastValue >= minGap && contrastValue <= maxGap;
                    break;
                case "Fold Change":
                    if (vB != 0) {
                        contrastValue = vA / vB;
                        if (threshold > 0) {
                            passes = contrastValue >= threshold || contrastValue <= 1.0 / threshold;
                        } else {
                            passes = true;
                        }
                    }
                    break;
                default:
                    break;
            }

            if (passes && contrastValue != null) {
                results.add(new ContrastResult(pattern, valueA, valueB, contrastValue, method));
            }
        }
    }

    /**
     * Count patterns that exist in both files.
     * @param dataA data from file A
     * @param dataB data from file B
     * @return count of matched patterns
     */
    private int countMatchedPatterns(PatternData dataA, PatternData dataB) {
        int count = 0;
        Map<String, Integer> mapB = dataB.getPatternIndexMap();
        
        for (String pattern : dataA.getPatternIndexMap().keySet()) {
            if (mapB.containsKey(pattern)) {
                count++;
            }
        }
        return count;
    }

    // ==================== Getters ====================

    public List<ContrastResult> getResults() {
        return results;
    }

    public long getComputationTimeMs() {
        return computationTimeMs;
    }

    public int getMatchedPatternCount() {
        return matchedPatternCount;
    }

    public int getResultCount() {
        return results != null ? results.size() : 0;
    }
}