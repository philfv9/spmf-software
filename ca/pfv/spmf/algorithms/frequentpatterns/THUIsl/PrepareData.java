package ca.pfv.spmf.algorithms.frequentpatterns.THUIsl;

/* This file is copyright (c) 2024 Srikumar Krishnamoorthy
 * 
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
 
 
/**
 * PrepareData is a utility class for reading CSV datasets, extracting numeric,
 * categorical, and target columns, and writing them to binary matrices for ML pipelines.
 * 
 * Key features:
 * <ul>
 *   <li>Reads CSV files with optional headers and custom delimiters.</li>
 *   <li>Extracts integer, float, and categorical columns separately.</li>
 *   <li>Encodes target column numerically, mapping labels if non-numeric.</li>
 *   <li>Writes int, float, and string matrices to binary files with little-endian format.</li>
 *   <li>Maintains a mapping of column indices to names for reference.</li>
 *   <li>Supports configuration via a simple key=value file format.</li>
 * </ul>
 * 
 * Note: Binary format for strings includes a 4-byte little-endian length prefix
 * per column followed by UTF-8 encoded bytes.
 */

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;

import java.net.URL;

import java.io.*;
import java.net.URL;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class PrepareData {

	public String dsName="data";
    public int numIntCols = 0, numFloatCols = 0, numCatCols = 0, numRows = 0, numClasses = 0;

    public PrepareData() throws IOException{
		createOutputDirs();
	}

	public void createOutputDirs() throws IOException {
		//create output directories for writing intermediate outputs and final HUIs
		Path baseDir = Paths.get("outputs");
		
		Files.createDirectories(baseDir.resolve("feModels"));
		Files.createDirectories(baseDir.resolve("inpdata"));
		Files.createDirectories(baseDir.resolve("hui"));
	}

    static class Config {
        public String inputFile;
        public String outputFile;
		public String testFile;
		public String prefix;
        public boolean header;
        public String delimiter;
        public int targetColIndex;
        public List<Integer> skipColsIndices = new ArrayList<>();
        public List<Integer> numericIntColsIndices = new ArrayList<>();
        public List<Integer> numericFloatColsIndices = new ArrayList<>();
        public List<Integer> catColsIndices = new ArrayList<>();
        public int B;
        public boolean writeTransformParameters;
        public boolean missingValueImputation;

        public static Config fromFile(String path) throws Exception {
            Config cfg = new Config();
            List<String> lines = Files.readAllLines(Paths.get(path));

            for (String line : lines) {
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split("=", 2);
                String key = parts[0].trim();
                String value = parts[1].trim();

                switch (key) {
                    case "inputFile": cfg.inputFile = value; break;
                    case "outputFile": cfg.outputFile = value; break;
					case "testFile": cfg.testFile = value; break;
                    case "prefix": cfg.prefix = value; break;
                    case "header": cfg.header = Boolean.parseBoolean(value); break;
                    case "delimiter": cfg.delimiter = value; break;
                    case "targetColIndex": cfg.targetColIndex = Integer.parseInt(value); break;
                    case "skipColsIndices": cfg.skipColsIndices = parseIndices(value); break;
                    case "numericIntColsIndices": cfg.numericIntColsIndices = parseIndices(value); break;
                    case "numericFloatColsIndices": cfg.numericFloatColsIndices = parseIndices(value); break;
                    case "catColsIndices": cfg.catColsIndices = parseIndices(value); break;
                    case "B": cfg.B = Integer.parseInt(value); break;
                    case "writeTransformParameters": cfg.writeTransformParameters = Boolean.parseBoolean(value); break;
                    case "missingValueImputation": cfg.missingValueImputation = Boolean.parseBoolean(value); break;
                }
            }
            return cfg;
        }
        private static List<Integer> parseIndices(String s) {
            List<Integer> list = new ArrayList<>();
            if (s == null || s.trim().isEmpty()) return list;
            for (String part : s.split(",")) {
                list.add(Integer.parseInt(part.trim()));
            }
            return list;
        }
    }

    public static class CSVData {
        public String[] header;
        public List<String[]> rows;
    }

    public CSVData readCSV(String fullPath, String delimiter, boolean headerPresent) throws IOException {
        CSVData result = new CSVData();
        result.rows = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Paths.get(fullPath))) {
            String line;
			int lineCntr = 0;
            if (headerPresent) {
                line = br.readLine();
				//System.out.println(line);
                result.header = line.split(delimiter, -1);
            }
            while ((line = br.readLine()) != null) {
				if (!headerPresent && lineCntr==0){
					lineCntr++;
					String[] firstRow = line.split(delimiter, -1);
					//Generate numeric headers: "1", "2", "3", ...
					result.header = new String[firstRow.length];
					for (int i = 0; i < firstRow.length; i++) {
						result.header[i] = String.valueOf(i + 1);
					}
				}
				//System.out.println(line);
                result.rows.add(line.split(delimiter, -1));
            }
        }
        return result;
    }

    public int[][] extractIntCols(List<String[]> data, List<Integer> cols) {
        int[][] res = new int[data.size()][cols.size()];
        for (int r = 0; r < data.size(); r++) {
            for (int c = 0; c < cols.size(); c++) {
                res[r][c] = Integer.parseInt(data.get(r)[cols.get(c)]);
            }
        }
        return res;
    }

    public float[][] extractFloatCols(List<String[]> data, List<Integer> cols) {
        float[][] res = new float[data.size()][cols.size()];
        for (int r = 0; r < data.size(); r++) {
            for (int c = 0; c < cols.size(); c++) {
                res[r][c] = Float.parseFloat(data.get(r)[cols.get(c)]);
            }
        }
        return res;
    }

    public String[][] extractCatCols(List<String[]> data, List<Integer> cols) {
        String[][] res = new String[data.size()][cols.size()];
        for (int r = 0; r < data.size(); r++) {
            for (int c = 0; c < cols.size(); c++) {
                res[r][c] = data.get(r)[cols.get(c)];
            }
        }
        return res;
    }

    public int[][] extractTargetCol(List<String[]> data, int targetColIndex) {
		int[][] res = new int[data.size()][1];
		Set<Integer> uniqueNums = new HashSet<>();
		Map<String, Integer> labelMap = new LinkedHashMap<>();
		boolean isNumeric = true;

		//first pass: check if all values are numeric
		for (String[] row : data) {
			try {
				Integer.parseInt(row[targetColIndex]);
			} catch (NumberFormatException e) {
				isNumeric = false;
				break;
			}
		}
		if (isNumeric) {
			int label = 0;
			for (int r = 0; r < data.size(); r++) {
				int val = Integer.parseInt(data.get(r)[targetColIndex]);
				res[r][0] = val;
				uniqueNums.add(val);
			}
			numClasses = uniqueNums.size();  
		} else {//label encode target column e.g. yes/no to 0/1
			int label = 0;
			for (int r = 0; r < data.size(); r++) {
				String val = data.get(r)[targetColIndex];
				if (!labelMap.containsKey(val))
					labelMap.put(val, label++);
				res[r][0] = labelMap.get(val);
			}
			numClasses = labelMap.size();
		}
		return res;
	}

    public void writeIntMatrix(int[][] data, String filename) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            for (int c = 0; c < data[0].length; c++)
                for (int r = 0; r < data.length; r++) {
                    buffer.putInt(data[r][c]);
                    fos.write(buffer.array());
                    buffer.clear();
                }
        }
    }

    public void writeFloatMatrix(float[][] data, String filename) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);

            for (int c = 0; c < data[0].length; c++)
                for (int r = 0; r < data.length; r++) {
                    buffer.putFloat(data[r][c]);
                    fos.write(buffer.array());
                    buffer.clear();
                }
        }
    }

    public void writeStringMatrix(String[][] data, String filename) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(filename)) {
			ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
			lengthBuffer.order(ByteOrder.LITTLE_ENDIAN);

			int numRows = data.length;
			int numCols = data[0].length;

			for (int c = 0; c < numCols; c++) {
				//build a single comma-separated string for this column
				StringBuilder sb = new StringBuilder();
				for (int r = 0; r < numRows; r++) {
					if (r > 0) sb.append(",");
					sb.append(data[r][c]);
				}
				byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

				//write length prefix
				lengthBuffer.clear();
				lengthBuffer.putInt(bytes.length);
				fos.write(lengthBuffer.array());

				//write string bytes
				fos.write(bytes);
			}
		}
	}
	
	public void writeStringMatrixAscii(String[][] data, String filename) throws IOException {

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {

			int numRows = data.length;
			int numCols = data[0].length;

			for (int c = 0; c < numCols; c++) {

				StringBuilder sb = new StringBuilder();

				for (int r = 0; r < numRows; r++) {
					if (r > 0) sb.append(",");
					sb.append(data[r][c]);
				}

				writer.write(sb.toString());
				writer.newLine();  // one column per line
			}
		}
	}
    
    public void run(String[] args) throws Exception {

        String outputPath = "outputs/inpdata/";
        String configFullPath = args[0];
		String fileType = args[1];
		
        Config cfg = Config.fromFile(configFullPath);
		if (cfg.prefix!=null)
			dsName = cfg.prefix;

        Path configDir = Paths.get(configFullPath).getParent();
        
        Path csvPath;
		if ("train".equals(fileType)) csvPath = configDir.resolve(cfg.inputFile).toAbsolutePath();
		else{
			if (cfg.testFile==null){
				System.out.println("test file not specified in config file");
				return;
			}
			csvPath = configDir.resolve(cfg.testFile).toAbsolutePath();
		}
		
        CSVData csvData = readCSV(csvPath.toString(), cfg.delimiter, cfg.header);

        List<String[]> rows = csvData.rows;
        numRows = rows.size();

        numIntCols = cfg.numericIntColsIndices.size();
        numFloatCols = cfg.numericFloatColsIndices.size();
        numCatCols = cfg.catColsIndices.size();

        String baseFile = dsName;
        if (!cfg.numericIntColsIndices.isEmpty())
            writeIntMatrix(extractIntCols(rows, cfg.numericIntColsIndices),
                    outputPath + baseFile + "_x_"+fileType+"_int.bin");

        if (!cfg.numericFloatColsIndices.isEmpty())
            writeFloatMatrix(extractFloatCols(rows, cfg.numericFloatColsIndices),
                    outputPath + baseFile + "_x_"+fileType+"_float.bin");

        if (!cfg.catColsIndices.isEmpty())
            writeStringMatrix(extractCatCols(rows, cfg.catColsIndices),
                    outputPath + baseFile + "_x_"+fileType+"_cat.bin");

        writeIntMatrix(extractTargetCol(rows, cfg.targetColIndex),
                outputPath + baseFile + "_y_"+fileType+".bin");

		if ("train".equals(fileType)){//write _allColsIdxToName file during training
			List<String> orderedFeatureNames = new ArrayList<>();
			if (cfg.header && csvData.header != null) {
				for (Integer idx : cfg.numericIntColsIndices)
					if (!cfg.skipColsIndices.contains(idx) && idx != cfg.targetColIndex)
						orderedFeatureNames.add(csvData.header[idx]);

				for (Integer idx : cfg.numericFloatColsIndices)
					if (!cfg.skipColsIndices.contains(idx) && idx != cfg.targetColIndex)
						orderedFeatureNames.add(csvData.header[idx]);

				for (Integer idx : cfg.catColsIndices)
					if (!cfg.skipColsIndices.contains(idx) && idx != cfg.targetColIndex)
						orderedFeatureNames.add(csvData.header[idx]);
			}

			String[][] colNames = new String[1][orderedFeatureNames.size()];
			for (int i = 0; i < orderedFeatureNames.size(); i++)
				colNames[0][i] = "\"" + orderedFeatureNames.get(i) + "\"";

			writeStringMatrix(colNames, outputPath + baseFile + "_allColsIdxToName.bin");
			writeStringMatrixAscii(colNames, outputPath + baseFile + "_allColsIdxToName.txt");
		}
        //System.out.println("Binary files written successfully!");
    }
}