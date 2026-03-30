/*
 * AlgoExportSPMFJSON.java
 *
 * Export all algorithms available in SPMF as a JSON file (spmf.json by default).
 *
 * Copyright (C) 2025 Philippe Fournier-Viger
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package ca.pfv.spmf.algorithmmanager.exportlist;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;

/**
 * Utility class to export all algorithms available in SPMF to a JSON file.
 *
 * <p>
 * It uses AlgorithmManager to obtain the list of algorithms. Because
 * AlgorithmManager exposes algorithm names via {@link AlgorithmManager#getListOfAlgorithmsAsString(boolean,boolean,boolean)}
 * (the list includes category headers), this exporter filters out category lines
 * and queries {@link AlgorithmManager#getDescriptionOfAlgorithm(String)} to obtain
 * each {@link DescriptionOfAlgorithm} instance and its metadata.
 * </p>
 *
 * @author Philippe Fournier-Viger (exporter), 2025
 */
public class AlgoExportSPMFJSON {

    /**
     * Run method to export all algorithms to a JSON file.
     *
     * This method follows the signature style of SPMF algorithm descriptors so it
     * can be invoked in a similar context. Parameters are ignored by this exporter.
     *
     * @param outputFile path to the JSON output file. If null or empty, "spmf.json" is used.
     * @throws Exception if an error occurs while accessing AlgorithmManager or writing the file.
     */
    public void runAlgorithm(String outputFile) throws Exception {
        String outPath = (outputFile == null || outputFile.trim().isEmpty()) ? "spmf.json" : outputFile;
        AlgorithmManager manager = AlgorithmManager.getInstance();

        List<String> namesAndCategories = manager.getListOfAlgorithmsAsString(true,true, true, true, true);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(indent(1)).append("\"exported_on\": \"").append(java.time.ZonedDateTime.now().toString()).append("\",\n");
        sb.append(indent(1)).append("\"algorithms\": [\n");

        boolean firstAlg = true;
        for (String entry : namesAndCategories) {
            // In the SPMF list, category entries look like " --- CATEGORY NAME --- "
            if (entry != null && entry.startsWith(" --- ")) {
                continue; // skip category header lines
            }
            String algoName = entry;
            DescriptionOfAlgorithm desc = manager.getDescriptionOfAlgorithm(algoName);
            if (desc == null) {
                // skip if cannot obtain description
                continue;
            }

            if (!firstAlg) {
                sb.append(",\n");
            }
            firstAlg = false;

            sb.append(indent(2)).append("{\n");
            // Basic fields
            appendJsonField(sb, 3, "name", desc.getName(), true);
            appendJsonField(sb, 3, "implementationAuthorNames", desc.getImplementationAuthorNames(), true);
            appendJsonField(sb, 3, "algorithmCategory", desc.getAlgorithmCategory(), true);
            appendJsonField(sb, 3, "documentationURL", desc.getURLOfDocumentation(), true);
            // algorithmType (enum)
            String algoType = (desc.getAlgorithmType() == null) ? null : desc.getAlgorithmType().toString();
            appendJsonField(sb, 3, "algorithmType", algoType, true);

            // input and output file types
            String[] inTypes = desc.getInputFileTypes();
            appendJsonStringArrayField(sb, 3, "inputFileTypes", inTypes, true);
            String[] outTypes = desc.getOutputFileTypes();
            appendJsonStringArrayField(sb, 3, "outputFileTypes", outTypes, true);

            // parameters
            DescriptionOfParameter[] params = desc.getParametersDescription();
            if (params == null) {
                sb.append(indent(3)).append("\"parameters\": null,\n");
                appendJsonField(sb, 3, "numberOfMandatoryParameters", Integer.toString(desc.getNumberOfMandatoryParameters()), false);
            } else {
                sb.append(indent(3)).append("\"parameters\": [\n");
                for (int i = 0; i < params.length; i++) {
                    DescriptionOfParameter p = params[i];
                    sb.append(indent(4)).append("{\n");
                    appendJsonField(sb, 5, "name", p.getName(), true);
                    appendJsonField(sb, 5, "example", p.getExample(), true);
                    // parameterType: show the class simple name (e.g. Integer, Double, String)
                    String typeStr = (p.getParameterType() == null) ? null : p.getParameterType().toString();
                    appendJsonField(sb, 5, "parameterType", typeStr, true);
                    appendJsonField(sb, 5, "isOptional", Boolean.toString(p.isOptional()), false);
                    sb.append(indent(4)).append("}");
                    if (i < params.length - 1) {
                        sb.append(",\n");
                    } else {
                        sb.append("\n");
                    }
                }
                sb.append(indent(3)).append("],\n");
                appendJsonField(sb, 3, "numberOfMandatoryParameters", Integer.toString(desc.getNumberOfMandatoryParameters()), false);
            }

            sb.append("\n").append(indent(2)).append("}");
        }

        sb.append("\n").append(indent(1)).append("]\n");
        sb.append("}\n");

        // Write to file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outPath))) {
            writer.write(sb.toString());
        } catch (IOException e) {
            throw new IOException("Error writing JSON file to " + outPath, e);
        }
    }

    /**
     * Helper to append a JSON field with a string value (or null).
     *
     * @param sb        the StringBuilder
     * @param indentLvl indentation level (number of tabs)
     * @param key       JSON key
     * @param value     JSON string value (may be null)
     * @param withComma whether to append a comma after the field
     */
    private static void appendJsonField(StringBuilder sb, int indentLvl, String key, String value, boolean withComma) {
        sb.append(indent(indentLvl)).append("\"").append(escapeJson(key)).append("\": ");
        if (value == null) {
            sb.append("null");
        } else {
            sb.append("\"").append(escapeJson(value)).append("\"");
        }
        if (withComma) {
            sb.append(",");
        }
        sb.append("\n");
    }

    /**
     * Helper to append a JSON array of strings.
     */
    private static void appendJsonStringArrayField(StringBuilder sb, int indentLvl, String key, String[] arr, boolean withComma) {
        sb.append(indent(indentLvl)).append("\"").append(escapeJson(key)).append("\": ");
        if (arr == null) {
            sb.append("null");
            if (withComma) sb.append(",");
            sb.append("\n");
            return;
        }
        sb.append("[");
        if (arr.length > 0) sb.append("\n");
        for (int i = 0; i < arr.length; i++) {
            sb.append(indent(indentLvl + 1));
            sb.append("\"").append(escapeJson(arr[i])).append("\"");
            if (i < arr.length - 1) sb.append(",");
            sb.append("\n");
        }
        if (arr.length > 0) sb.append(indent(indentLvl));
        sb.append("]");
        if (withComma) sb.append(",");
        sb.append("\n");
    }

    /**
     * Very small JSON string escaper.
     *
     * @param s input
     * @return escaped string
     */
    private static String escapeJson(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (ch < 0x20 || ch > 0x7E) {
                        // unicode escape
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Return indentation (4 spaces per level).
     */
    private static String indent(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append("    ");
        }
        return sb.toString();
    }
}
