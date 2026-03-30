package ca.pfv.spmf.gui.pattern_diff_analyzer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
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
 * Exports contrast pattern results to various formats.
 * 
 * @author Philippe Fournier-Viger
 */
public class ContrastExporter {

    /** Decimal formatter for output */
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.####");

    /**
     * Default constructor.
     */
    public ContrastExporter() {
    }

    /**
     * Export results to SPMF format.
     * @param filePath the output file path
     * @param results the contrast results
     * @param options the contrast options used
     * @throws IOException if error writing file
     */
    public void exportToSPMFFormat(String filePath, List<ContrastResult> results,
            ContrastOptions options) throws IOException {
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writeHeader(writer, results, options);
            writeResults(writer, results);
        }
    }

    /**
     * Export results to CSV format.
     * @param filePath the output file path
     * @param results the contrast results
     * @param options the contrast options used
     * @throws IOException if error writing file
     */
    public void exportToCSV(String filePath, List<ContrastResult> results,
            ContrastOptions options) throws IOException {
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            // Write CSV header
            writer.write("Pattern,Value_A,Value_B,Contrast,Type");
            writer.newLine();
            
            // Write each result
            for (ContrastResult result : results) {
                StringBuilder sb = new StringBuilder();
                sb.append(escapeCSV(result.getPattern())).append(",");
                sb.append(formatValue(result.getValueA())).append(",");
                sb.append(formatValue(result.getValueB())).append(",");
                sb.append(DECIMAL_FORMAT.format(result.getContrastValue())).append(",");
                sb.append(escapeCSV(result.getContrastType()));
                writer.write(sb.toString());
                writer.newLine();
            }
        }
    }

    /**
     * Export results to HTML format.
     * @param filePath the output file path
     * @param results the contrast results
     * @param options the contrast options used
     * @throws IOException if error writing file
     */
    public void exportToHTML(String filePath, List<ContrastResult> results,
            ContrastOptions options) throws IOException {
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            // HTML header
            writer.write("<!DOCTYPE html>\n<html>\n<head>\n");
            writer.write("<title>Contrast Pattern Results</title>\n");
            writer.write("<style>\n");
            writer.write("table { border-collapse: collapse; width: 100%; }\n");
            writer.write("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
            writer.write("th { background-color: #4CAF50; color: white; }\n");
            writer.write("tr:nth-child(even) { background-color: #f2f2f2; }\n");
            writer.write(".exclusive { background-color: #ffe6e6; }\n");
            writer.write(".topk { background-color: #ffffe6; }\n");
            writer.write("</style>\n</head>\n<body>\n");
            
            // Title and info
            writer.write("<h1>Contrast Pattern Results</h1>\n");
            writer.write("<p><strong>Method:</strong> " + escapeHTML(options.getContrastMethod()) + "</p>\n");
            writer.write("<p><strong>Measure A:</strong> " + escapeHTML(options.getMeasureName1()) + "</p>\n");
            writer.write("<p><strong>Measure B:</strong> " + escapeHTML(options.getMeasureName2()) + "</p>\n");
            writer.write("<p><strong>Total Patterns:</strong> " + results.size() + "</p>\n");
            
            // Table
            writer.write("<table>\n");
            writer.write("<tr><th>Pattern</th><th>Value A</th><th>Value B</th>");
            writer.write("<th>Contrast</th><th>Type</th></tr>\n");
            
            for (ContrastResult result : results) {
                String rowClass = "";
                if (result.getContrastType().contains("Exclusive") || 
                    result.getContrastType().contains("Only")) {
                    rowClass = " class=\"exclusive\"";
                } else if (result.getContrastType().contains("Top-K")) {
                    rowClass = " class=\"topk\"";
                }
                
                writer.write("<tr" + rowClass + ">");
                writer.write("<td>" + escapeHTML(result.getPattern()) + "</td>");
                writer.write("<td>" + formatValue(result.getValueA()) + "</td>");
                writer.write("<td>" + formatValue(result.getValueB()) + "</td>");
                writer.write("<td>" + DECIMAL_FORMAT.format(result.getContrastValue()) + "</td>");
                writer.write("<td>" + escapeHTML(result.getContrastType()) + "</td>");
                writer.write("</tr>\n");
            }
            
            writer.write("</table>\n");
            writer.write("</body>\n</html>");
        }
    }

    /**
     * Write SPMF format header.
     */
    private void writeHeader(BufferedWriter writer, List<ContrastResult> results,
            ContrastOptions options) throws IOException {
        
        writer.write("# Contrast Pattern Results");
        writer.newLine();
        writer.write("# Generated by SPMF Contrast Pattern Comparator");
        writer.newLine();
        writer.write("# Method: " + options.getContrastMethod());
        writer.newLine();
        writer.write("# Measure A: " + options.getMeasureName1());
        writer.newLine();
        writer.write("# Measure B: " + options.getMeasureName2());
        writer.newLine();
        writer.write("# Pattern Matching: " + options.getMatchingMethod());
        writer.newLine();
        writer.write("# Total Contrast Patterns: " + results.size());
        writer.newLine();
        writer.newLine();
    }

    /**
     * Write results in SPMF format.
     */
    private void writeResults(BufferedWriter writer, List<ContrastResult> results) 
            throws IOException {
        
        for (ContrastResult result : results) {
            StringBuilder sb = new StringBuilder();
            sb.append(result.getPattern());
            sb.append(" #VALUE_A ");
            sb.append(formatValue(result.getValueA()));
            sb.append(" #VALUE_B ");
            sb.append(formatValue(result.getValueB()));
            sb.append(" #CONTRAST ");
            sb.append(DECIMAL_FORMAT.format(result.getContrastValue()));
            sb.append(" #TYPE ");
            sb.append(result.getContrastType());
            writer.write(sb.toString());
            writer.newLine();
        }
    }

    /**
     * Format a value for output.
     */
    private String formatValue(Double value) {
        return value != null ? DECIMAL_FORMAT.format(value) : "N/A";
    }

    /**
     * Escape a string for CSV output.
     */
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Escape a string for HTML output.
     */
    private String escapeHTML(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}