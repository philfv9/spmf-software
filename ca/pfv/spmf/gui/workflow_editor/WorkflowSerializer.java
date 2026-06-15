package ca.pfv.spmf.gui.workflow_editor;

/*
 * Copyright (c) 2024 Philippe Fournier-Viger
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
 *
 * Do not remove license and copyright information
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes and deserializes a branching workflow tree to and from a plain-text file format using percent-encoding for field values.
 *
 * @author Philippe Fournier-Viger
 */
class WorkflowSerializer {

    /** The first line that identifies a valid workflow file. */
    static final String FILE_HEADER = "@FILETYPE=\"WORKFLOW\"";

    /** Keyword used to introduce a node record line. */
    private static final String KW_NODE   = "NODE";

    /** Keyword used to introduce an input file record line. */
    private static final String KW_INPUT  = "INPUT";

    /** Keyword used to introduce an output file record line. */
    private static final String KW_OUTPUT = "OUTPUT";

    /** Keyword used to introduce a parameter record line. */
    private static final String KW_PARAM  = "PARAM";

    /** Separator used between key and value within a field token. */
    private static final char KV_SEP = '=';

    /** Sentinel parent id value that marks a root node. */
    private static final int ROOT_PARENT_ID = 0;

    // -----------------------------------------------------------------------
    // Save
    // -----------------------------------------------------------------------

    /**
     * Writes the given list of root branch nodes to the given file in the workflow text format.
     *
     * @param roots    the root nodes of the workflow tree to save.
     * @param filePath the absolute path of the destination file.
     * @throws IOException if the file cannot be written.
     */
    static void save(List<BranchNode> roots, String filePath) throws IOException {
        List<BranchNode> ordered = new ArrayList<>();
        for (BranchNode r : roots) {
            r.collectAllNodes(ordered);
        }

        // Assign stable integer ids in DFS pre-order
        Map<BranchNode, Integer> idMap = new HashMap<>();
        int nextId = 1;
        for (BranchNode bn : ordered) {
            idMap.put(bn, nextId++);
        }

        try (BufferedWriter w = new BufferedWriter(new FileWriter(filePath))) {
            w.write(FILE_HEADER);
            w.newLine();

            for (BranchNode bn : ordered) {
                int id       = idMap.get(bn);
                int parentId = bn.isRoot() ? ROOT_PARENT_ID : idMap.get(bn.parent);
                GroupOfNodes g = bn.group;

                // NODE line
                w.write(KW_NODE
                        + " id" + KV_SEP + id
                        + " parentId" + KV_SEP + parentId
                        + " algorithm" + KV_SEP + percentEncode(g.nodeAlgorithm.name)
                        + " showInput" + KV_SEP + g.showInput
                        + " showOutput" + KV_SEP + g.showOutput);
                w.newLine();

                // INPUT line: only written when this node actually has a visible input node
                if (g.showInput && g.nodeInput != null) {
                    String inFile = g.nodeInput.inputFile != null ? g.nodeInput.inputFile : "";
                    String inName = g.nodeInput.name      != null ? g.nodeInput.name      : "";
                    w.write(KW_INPUT
                            + " id" + KV_SEP + id
                            + " file" + KV_SEP + percentEncode(inFile)
                            + " name" + KV_SEP + percentEncode(inName));
                    w.newLine();
                }

                // OUTPUT line
                String outFile = (g.nodeOutput != null && g.nodeOutput.outputFile != null)
                        ? g.nodeOutput.outputFile : "";
                String outName = (g.nodeOutput != null && g.nodeOutput.name != null)
                        ? g.nodeOutput.name : "";
                w.write(KW_OUTPUT
                        + " id" + KV_SEP + id
                        + " file" + KV_SEP + percentEncode(outFile)
                        + " name" + KV_SEP + percentEncode(outName));
                w.newLine();

                // PARAM lines
                String[] params = g.nodeAlgorithm.parameters;
                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        if (params[i] != null) {
                            w.write(KW_PARAM
                                    + " id" + KV_SEP + id
                                    + " index" + KV_SEP + i
                                    + " value" + KV_SEP + percentEncode(params[i]));
                            w.newLine();
                        }
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Load
    // -----------------------------------------------------------------------

    /**
     * Reads a workflow from the given file and returns the reconstructed list of root branch nodes.
     *
     * @param filePath the absolute path of the source file.
     * @return the list of root BranchNodes reconstructed from the file.
     * @throws IOException if the file cannot be read or its format is invalid.
     */
    static List<BranchNode> load(String filePath) throws IOException {
        // First pass: collect all raw records keyed by node id
        Map<Integer, NodeRecord> records = new HashMap<>();
        // Preserve insertion order so parents are always processed before children
        List<Integer> idOrder = new ArrayList<>();

        try (BufferedReader r = new BufferedReader(new FileReader(filePath))) {
            String line = r.readLine();
            if (line == null || !FILE_HEADER.equals(line.trim())) {
                throw new IOException(
                        "Not a valid workflow file. Missing header: " + FILE_HEADER);
            }

            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] tokens = line.split(" ");
                if (tokens.length == 0) continue;
                String keyword = tokens[0];

                if (KW_NODE.equals(keyword)) {
                    int id          = intField(tokens, "id");
                    int parentId    = intField(tokens, "parentId");
                    String alg      = stringField(tokens, "algorithm");
                    boolean showIn  = boolField(tokens, "showInput");
                    boolean showOut = boolField(tokens, "showOutput");

                    NodeRecord rec  = getOrCreate(records, idOrder, id);
                    rec.parentId    = parentId;
                    rec.algorithm   = alg;
                    rec.showInput   = showIn;
                    rec.showOutput  = showOut;

                } else if (KW_INPUT.equals(keyword)) {
                    int id       = intField(tokens, "id");
                    String file  = stringField(tokens, "file");
                    String name  = stringField(tokens, "name");
                    NodeRecord rec  = getOrCreate(records, idOrder, id);
                    rec.inputFile   = file;
                    rec.inputName   = name;

                } else if (KW_OUTPUT.equals(keyword)) {
                    int id       = intField(tokens, "id");
                    String file  = stringField(tokens, "file");
                    String name  = stringField(tokens, "name");
                    NodeRecord rec  = getOrCreate(records, idOrder, id);
                    rec.outputFile  = file;
                    rec.outputName  = name;

                } else if (KW_PARAM.equals(keyword)) {
                    int id       = intField(tokens, "id");
                    int index    = intField(tokens, "index");
                    String val   = stringField(tokens, "value");
                    NodeRecord rec = getOrCreate(records, idOrder, id);
                    rec.params.put(index, val);
                }
            }
        }

        // Second pass: build BranchNode objects in id order (parents always come first)
        Map<Integer, BranchNode> branchMap = new HashMap<>();
        List<BranchNode> roots = new ArrayList<>();

        for (int id : idOrder) {
            NodeRecord rec = records.get(id);

            // Only create a NodeFileInput when the node really has a visible input node.
            // Child nodes receive their input from the parent's output file and must
            // have nodeInput=null and showInput=false, matching createNewBranchNode(false).
            NodeFileInput inNode = null;
            if (rec.showInput) {
                inNode = new NodeFileInput("", 0, 0);
                inNode.name      = rec.inputName  != null ? rec.inputName  : "";
                inNode.inputFile = (rec.inputFile != null && !rec.inputFile.isEmpty())
                        ? rec.inputFile : null;
            }

            NodeAlgorithm algNode = new NodeAlgorithm(
                    rec.algorithm != null ? rec.algorithm : "Algorithm", 0, 0);

            // Reconstruct parameters array; always initialize to avoid NullPointerException.
            if (!rec.params.isEmpty()) {
                int maxIndex = rec.params.keySet().stream()
                        .mapToInt(i -> i).max().orElse(-1);
                String[] paramsArray = new String[maxIndex + 1];
                for (Map.Entry<Integer, String> e : rec.params.entrySet()) {
                    paramsArray[e.getKey()] = e.getValue();
                }
                algNode.parameters = paramsArray;
            } else {
                // No parameters were saved, so initialize to empty array
                algNode.parameters = new String[0];
            }

            NodeFileOutput outNode = new NodeFileOutput(
                    (rec.outputName != null && !rec.outputName.isEmpty())
                            ? rec.outputName : "output.txt", 0, 0);
            outNode.name       = rec.outputName != null ? rec.outputName : "";
            outNode.outputFile = (rec.outputFile != null && !rec.outputFile.isEmpty())
                    ? rec.outputFile : null;

            GroupOfNodes group  = new GroupOfNodes(inNode, algNode, outNode);
            group.showInput     = rec.showInput;
            group.showOutput    = rec.showOutput;

            BranchNode bn = new BranchNode(group, null);
            branchMap.put(id, bn);

            if (rec.parentId == ROOT_PARENT_ID) {
                roots.add(bn);
            } else {
                BranchNode parentBn = branchMap.get(rec.parentId);
                if (parentBn == null) {
                    throw new IOException(
                            "Workflow file references unknown parent id " + rec.parentId
                            + " for node id " + id + ". File may be corrupt.");
                }
                parentBn.addChild(bn);
            }
        }

        return roots;
    }

    // -----------------------------------------------------------------------
    // Parsing helpers
    // -----------------------------------------------------------------------

    /**
     * Returns or creates the NodeRecord for the given id, registering it in the order list if new.
     *
     * @param records  the map of id to NodeRecord.
     * @param idOrder  the insertion-order list of ids.
     * @param id       the node id to look up or create.
     * @return the existing or newly created NodeRecord.
     */
    private static NodeRecord getOrCreate(Map<Integer, NodeRecord> records,
                                           List<Integer> idOrder, int id) {
        if (!records.containsKey(id)) {
            records.put(id, new NodeRecord());
            idOrder.add(id);
        }
        return records.get(id);
    }

    /**
     * Extracts the integer value of the named field from the token array.
     *
     * @param tokens the whitespace-split tokens of one line.
     * @param key    the field name to search for.
     * @return the parsed integer value.
     * @throws IOException if the field is missing or its value cannot be parsed.
     */
    private static int intField(String[] tokens, String key) throws IOException {
        String raw = findField(tokens, key);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IOException(
                    "Cannot parse integer for field '" + key + "': " + raw);
        }
    }

    /**
     * Extracts the boolean value of the named field from the token array.
     *
     * @param tokens the whitespace-split tokens of one line.
     * @param key    the field name to search for.
     * @return the parsed boolean value.
     * @throws IOException if the field is missing.
     */
    private static boolean boolField(String[] tokens, String key) throws IOException {
        return "true".equalsIgnoreCase(findField(tokens, key));
    }

    /**
     * Extracts the string value of the named field from the token array and percent-decodes it.
     *
     * @param tokens the whitespace-split tokens of one line.
     * @param key    the field name to search for.
     * @return the decoded string value, never null.
     * @throws IOException if the field is missing or decoding fails.
     */
    private static String stringField(String[] tokens, String key) throws IOException {
        return percentDecode(findField(tokens, key));
    }

    /**
     * Scans the token array for a token of the form key=value and returns the value portion.
     *
     * @param tokens the whitespace-split tokens of one line.
     * @param key    the field name to search for.
     * @return the raw value string.
     * @throws IOException if no matching token is found.
     */
    private static String findField(String[] tokens, String key) throws IOException {
        String prefix = key + KV_SEP;
        for (String token : tokens) {
            if (token.startsWith(prefix)) {
                return token.substring(prefix.length());
            }
        }
        throw new IOException("Missing field '" + key + "' in line: "
                + String.join(" ", tokens));
    }

    // -----------------------------------------------------------------------
    // Percent-encoding helpers
    // -----------------------------------------------------------------------

    /**
     * Percent-encodes a value string so it contains no whitespace and is safe as a single token.
     * Only the characters that would break parsing are encoded: '%' (must be first),
     * space, and any ASCII control characters below 0x21 or equal to 0x7F.
     * All other characters, including backslashes and Unicode, are left unchanged.
     *
     * @param value the raw value to encode, may be null.
     * @return the encoded string, or an empty string if value is null.
     */
    static String percentEncode(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%') {
                // Encode '%' first so later encoded sequences are not double-processed
                sb.append("%25");
            } else if (c == ' ') {
                sb.append("%20");
            } else if (c < 0x21 || c == 0x7F) {
                // Encode other whitespace and control characters
                sb.append(String.format("%%%02X", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Decodes a percent-encoded string produced by percentEncode.
     *
     * @param value the encoded token value, may be null.
     * @return the original decoded string, or an empty string if value is null.
     * @throws IOException if a percent sequence is malformed.
     */
    static String percentDecode(String value) throws IOException {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == '%') {
                if (i + 2 >= value.length()) {
                    throw new IOException(
                            "Malformed percent-encoding at position " + i + " in: " + value);
                }
                String hex = value.substring(i + 1, i + 3);
                try {
                    int decoded = Integer.parseInt(hex, 16);
                    sb.append((char) decoded);
                } catch (NumberFormatException e) {
                    throw new IOException(
                            "Invalid percent-escape '%" + hex + "' in: " + value);
                }
                i += 3;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Internal record
    // -----------------------------------------------------------------------

    /**
     * Transient data holder used during file parsing to accumulate fields for one node.
     */
    private static class NodeRecord {

        /** The id of the parent node, or ROOT_PARENT_ID for roots. */
        int parentId = ROOT_PARENT_ID;

        /** The algorithm name. */
        String algorithm = null;

        /** Whether the input node is visible. */
        boolean showInput = false;

        /** Whether the output node is visible. */
        boolean showOutput = false;

        /** The input file path, only meaningful when showInput is true. */
        String inputFile = null;

        /** The input file display name, only meaningful when showInput is true. */
        String inputName = null;

        /** The output file path. */
        String outputFile = null;

        /** The output file display name. */
        String outputName = null;

        /** The parameter values keyed by zero-based index. */
        Map<Integer, String> params = new HashMap<>();
    }
}