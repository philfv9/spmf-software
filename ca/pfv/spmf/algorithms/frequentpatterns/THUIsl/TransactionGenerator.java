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
 * Generates and processes transactional data from binary input files.
 * Handles discretization of continuous features, label encoding for categorical features,
 * and calculates utility scores based on Information Gain and Correlation.
 */
 
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.lang.Math;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class TransactionGenerator{
	public TransactionGenerator(){}
	
	final boolean writeTransaction = true;
	private void readY(String fileName, int[] y_train){
		try (FileInputStream fis = new FileInputStream(fileName); FileChannel fileChannel = fis.getChannel()) {
			ByteBuffer byteBuffer = ByteBuffer.allocate(y_train.length * Integer.BYTES);  
			byteBuffer.order(ByteOrder.LITTLE_ENDIAN); 
			byteBuffer.clear();  
			fileChannel.read(byteBuffer);  
			byteBuffer.flip();
			IntBuffer intBuffer = byteBuffer.asIntBuffer();
			intBuffer.get(y_train);  
		}catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void writeColNameNewToFile(List<Integer> cols, String filename) throws IOException { 
		try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(filename))) {
			for (Integer d : cols) {
				dos.writeInt(d); 
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void writeColNameNewToFileAscii(List<Integer> cols, Map<Integer, Integer> binNameToItemCntr, Map<Integer, String> itemMap, String filename) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
			for (Integer d : cols) {
				writer.write(d.toString());
				writer.write(" ");
				writer.write(binNameToItemCntr.get(d).toString());
				writer.write(" ");
				writer.write(itemMap.get(binNameToItemCntr.get(d)));
				
				writer.newLine();  // one integer per line
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void readColNameNewFromFile(String filename, List<Integer> cols) throws IOException {
		try (DataInputStream dis = new DataInputStream(new FileInputStream(filename))) {
			while (dis.available() > 0) { 
				cols.add(dis.readInt()); //read integer from binary data
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void readFilesTest(String input, String dsName, int numIntCols, int numFloatCols, int numCatCols, int[][] dfFinal, List<Integer> colNamesNew) throws IOException {//stored column-wise
		String path = input+dsName;
		String Xfname_int_bin = path+"_x_test_int.bin", Xfname_float_bin = path+"_x_test_float.bin", Xfname_cat = path+"_x_test_cat.bin"; 
		if ((numIntCols + numFloatCols)>0){
			List<float[]> kbParamsBE = new ArrayList<float []>();
			int[] kbParamsNbins = new int [numIntCols + numFloatCols];
		
			String kbinsName = "outputs/feModels/"+dsName+"_kbins.bin";
			loadKbinsParams(kbinsName, kbParamsBE, kbParamsNbins, numIntCols + numFloatCols);
			if (numIntCols>0){
				try (FileInputStream fis = new FileInputStream(Xfname_int_bin); FileChannel fileChannel = fis.getChannel()) {
					ByteBuffer byteBuffer = ByteBuffer.allocate(dfFinal[0].length * Integer.BYTES);  // Buffer to hold one row at a time
					byteBuffer.order(ByteOrder.LITTLE_ENDIAN); 
					int[] colData = new int [dfFinal[0].length];
					
					for (int colIdx = 0; colIdx < numIntCols; colIdx++) {
						byteBuffer.clear();  // Reset buffer for the next row
						fileChannel.read(byteBuffer);  // Read numCols elements
						byteBuffer.flip();
						IntBuffer intBuffer = byteBuffer.asIntBuffer();
						intBuffer.get(colData);  // Convert ByteBuffer to int[]
				
						KBinsDiscretizer discretizer = new KBinsDiscretizer(kbParamsNbins[colIdx], kbParamsBE.get(colIdx));
						dfFinal[colIdx] = discretizer.transform(colData);

					}
				}catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (numFloatCols>0){
				float[][] msParams = new float [numFloatCols][2];
				String msFname = "outputs/feModels/"+dsName+"_ms.bin";
				loadMsParams(msFname, msParams);
				try (FileInputStream fis = new FileInputStream(Xfname_float_bin); FileChannel fileChannel = fis.getChannel()) {
					ByteBuffer byteBuffer = ByteBuffer.allocate(dfFinal[0].length * Float.BYTES);  // Buffer to hold one row at a time
					byteBuffer.order(ByteOrder.LITTLE_ENDIAN); 
					float[] colData = new float[dfFinal[0].length];
					
					for (int colIdx = 0; colIdx < numFloatCols; colIdx++) {
						byteBuffer.clear();  // Reset buffer for the next row
						fileChannel.read(byteBuffer);  // Read numCols elements
						byteBuffer.flip();
						FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
						floatBuffer.get(colData);  // Convert ByteBuffer to float[]
					
						int colNameIdx = numIntCols + colIdx;
						MinMaxScaler scaler = new MinMaxScaler(msParams[colIdx]);//while loading msParams, offset is ignored
						scaler.transform(colData);//in-place transform
						
						KBinsDiscretizer discretizer = new KBinsDiscretizer(kbParamsNbins[colNameIdx], kbParamsBE.get(colNameIdx));
						dfFinal[colNameIdx] = discretizer.transform(colData);
					}
				}catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		if (numCatCols>0){
			int offset = numIntCols + numFloatCols;
			List<Map<String, Integer>> lbParams = new ArrayList<Map<String, Integer>>();
			String lbFname = "outputs/feModels/"+dsName+"_lb.bin";
			loadLbParams(lbFname, lbParams);
			try (FileInputStream fis = new FileInputStream(Xfname_cat); FileChannel fileChannel = fis.getChannel()) {
				ByteBuffer lengthBuffer = ByteBuffer.allocate(4);  
				lengthBuffer.order(ByteOrder.LITTLE_ENDIAN); 
				LabelBinarizer lb = new LabelBinarizer();
				for (int colIdx = 0; colIdx < numCatCols; colIdx++) {
					lengthBuffer.clear();
					fileChannel.read(lengthBuffer);  
					lengthBuffer.flip();
					int rowLength = lengthBuffer.getInt(); 

					ByteBuffer rowBuffer = ByteBuffer.allocate(rowLength).order(ByteOrder.LITTLE_ENDIAN); 
					fileChannel.read(rowBuffer);  
					rowBuffer.flip();
					String row = StandardCharsets.UTF_8.decode(rowBuffer).toString(); 
					String[] columnValues = row.split(",", dfFinal[0].length); 
					for (int i = 0; i < columnValues.length; i++) columnValues[i] = columnValues[i].trim();
					
					int colNameIdx = offset + colIdx;
					lb.setLabelToIdx(lbParams.get(colIdx));
					dfFinal[colNameIdx] = lb.transformDense(columnValues);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		String fnameCols = "outputs/"+dsName+"_colNamesNew.bin";//read processed column names stored after training stage
		readColNameNewFromFile(fnameCols, colNamesNew);
	}
	
	private void readFilesTrain(String input, String dsName, int numIntCols, int numFloatCols, int numCatCols, int[][] dfFinal, 
									List<float [][]> binRangesOriginal, List<float []> binEdgesRight, int[] nbByCol, 
									int numBins, int[] y_train, boolean computeNumBinsFlag, int num_classes, String[] colIdxToName, List<String[]> catLabels) {
	
		String path = input+dsName;
		String Xfname_int_bin = path+"_x_train_int.bin", Xfname_float_bin = path+"_x_train_float.bin", Xfname_cat = path+"_x_train_cat.bin"; 
		
		List<float[]> kbParamsBE = new ArrayList<float []>();
		int[] kbParamsNbins = new int [numIntCols + numFloatCols];
		if (numIntCols>0){
			
			try (FileInputStream fis = new FileInputStream(Xfname_int_bin); FileChannel fileChannel = fis.getChannel()) {
				ByteBuffer byteBuffer = ByteBuffer.allocate(dfFinal[0].length * Integer.BYTES);  // Buffer to hold one row at a time
				byteBuffer.order(ByteOrder.LITTLE_ENDIAN); 
				int[] colData = new int [dfFinal[0].length];
				
				for (int colIdx = 0; colIdx < numIntCols; colIdx++) {
					byteBuffer.clear();  // Reset buffer for the next row
					fileChannel.read(byteBuffer);  // Read numCols elements
					byteBuffer.flip();
					IntBuffer intBuffer = byteBuffer.asIntBuffer();
					intBuffer.get(colData);  // Convert ByteBuffer to int[]
					
					int distinctValCnt = (int) Arrays.stream(colData).distinct().count();
					int nb = Math.max(Math.min(distinctValCnt - 1, (computeNumBinsFlag?compute_num_bins(colData, y_train, num_classes, 2, 20):numBins)), 2);

					KBinsDiscretizer discretizer = new KBinsDiscretizer(nb); 
					dfFinal[colIdx] = discretizer.fitTransform(colData);
					float[] binEdges = discretizer.getBinEdges();
					kbParamsBE.add(binEdges);
					kbParamsNbins[colIdx] = binEdges.length-1;//duplicate removal may reduce the number of bins
				
					float[][] origRange = new float [binEdges.length-1][2];
					for (int bi=1;bi<binEdges.length;bi++) origRange[bi-1] = new float []{binEdges[bi-1], binEdges[bi]};
					binRangesOriginal.add(origRange);//no scaling done for integer columns 
					nbByCol[colIdx] = binEdges.length-1;
					
					float maxEdge = binEdges[0];  
					for (float value : binEdges) {
						if (value > maxEdge) maxEdge = value;
					}
					float[] normalizedEdgesWithoutFirst = new float [binEdges.length-1];
					for (int ii=0;ii<binEdges.length-1;ii++) normalizedEdgesWithoutFirst[ii] = binEdges[ii+1]/maxEdge;//removing 0.0 entry
					binEdgesRight.add(normalizedEdgesWithoutFirst);
				}
			}catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (numFloatCols>0){
			float [][] msParams = new float [numFloatCols][2];
			float[][] origRange;
			Set<Float> distinctElements = new HashSet<Float>();
			try (FileInputStream fis = new FileInputStream(Xfname_float_bin); FileChannel fileChannel = fis.getChannel()) {
				ByteBuffer byteBuffer = ByteBuffer.allocate(dfFinal[0].length * Float.BYTES);  // Buffer to hold one row at a time
				byteBuffer.order(ByteOrder.LITTLE_ENDIAN); 
				float[] colData = new float[dfFinal[0].length];
				
				for (int colIdx = 0; colIdx < numFloatCols; colIdx++) {
					byteBuffer.clear();  // Reset buffer for the next row
					fileChannel.read(byteBuffer);  // Read numCols elements
					byteBuffer.flip();
					FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
					floatBuffer.get(colData);  // Convert ByteBuffer to float[]
					
					MinMaxScaler scaler = new MinMaxScaler();
					scaler.fitTransform(colData);//in-place updates
					msParams[colIdx] = new float []{scaler.dataMin, scaler.dataMax};

					Integer colNameIdx = numIntCols + colIdx;
					distinctElements.clear();
					for (float value : colData) distinctElements.add(value);
					int distinctValCnt = distinctElements.size();
					
					int nb = Math.max(Math.min(distinctValCnt - 1, (computeNumBinsFlag?compute_num_bins(colData, y_train, num_classes, 2, 20):numBins)), 2);
					KBinsDiscretizer discretizer = new KBinsDiscretizer(nb); 
					dfFinal[colNameIdx]  = discretizer.fitTransform(colData);
					float[] binEdges = discretizer.getBinEdges();
					kbParamsBE.add(binEdges);
					kbParamsNbins[colNameIdx] = binEdges.length-1; //duplicate removal may reduce the number of bins
					
					origRange = new float [binEdges.length-1][2];
					for (int bi=1;bi<binEdges.length;bi++) 
						origRange[bi-1] = new float []{scaler.inverseTransform(binEdges[bi-1]), scaler.inverseTransform(binEdges[bi])};
					binRangesOriginal.add(origRange);
					nbByCol[colNameIdx] = binEdges.length-1;
					
					float maxEdge = binEdges[0];  
					for (float value : binEdges) {
						if (value > maxEdge) maxEdge = value;
					}
					float[] normalizedEdgesWithoutFirst = new float [binEdges.length-1];
					for (int ii=0;ii<binEdges.length-1;ii++) normalizedEdgesWithoutFirst[ii] = binEdges[ii+1]/maxEdge;//removing 0.0 entry
					binEdgesRight.add(normalizedEdgesWithoutFirst);	
				}
			}catch (IOException e) {
				e.printStackTrace();
			}
			String msFname = "outputs/feModels/"+dsName+"_ms.bin";
			writeMsParams(msFname, msParams, numFloatCols);
			
			String msFnameAscii = "outputs/feModels/"+dsName+"_ms.txt";
			writeMsParamsAscii(msFnameAscii, msParams, numFloatCols, numIntCols, colIdxToName);
		}
		String kbinsName = "outputs/feModels/"+dsName+"_kbins.bin";
		writeKbinsParams(kbinsName, kbParamsBE, kbParamsNbins, numIntCols+numFloatCols);
		
		String kbinsNameAscii = "outputs/feModels/"+dsName+"_kbins.txt";
		writeKbinsParamsAscii(kbinsNameAscii, kbParamsBE, kbParamsNbins, numIntCols+numFloatCols, colIdxToName);
		
		if (numCatCols>0){
			int offset = numIntCols + numFloatCols;
			List<Map<String, Integer>> lbParams = new ArrayList<Map<String, Integer>>();
			try (FileInputStream fis = new FileInputStream(Xfname_cat); FileChannel fileChannel = fis.getChannel()) {
				ByteBuffer lengthBuffer = ByteBuffer.allocate(4);  
				lengthBuffer.order(ByteOrder.LITTLE_ENDIAN); 
				for (int colIdx = 0; colIdx < numCatCols; colIdx++) {
					lengthBuffer.clear();
					fileChannel.read(lengthBuffer);  
					lengthBuffer.flip();
					int rowLength = lengthBuffer.getInt(); 

					ByteBuffer rowBuffer = ByteBuffer.allocate(rowLength).order(ByteOrder.LITTLE_ENDIAN); 
					fileChannel.read(rowBuffer);  
					rowBuffer.flip();
					String row = StandardCharsets.UTF_8.decode(rowBuffer).toString();  
					String[] columnValues = row.split(",", dfFinal[0].length);
					for (int i = 0; i < columnValues.length; i++) columnValues[i] = columnValues[i].trim();
					
					LabelBinarizer lb = new LabelBinarizer();
					lb.fit(columnValues);
					lbParams.add(lb.labelToIdx);
					
					int colNameIdx = offset + colIdx;
					nbByCol[colNameIdx] = lb.labelToIdx.size();
					String[] lbls = lb.getLabels(colIdxToName[colNameIdx]);
					catLabels.add(lbls);
					dfFinal[colNameIdx] = lb.transformDense(columnValues);			
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			String lbFname = "outputs/feModels/"+dsName+"_lb.bin";
			writeLbParams(lbFname, lbParams, numCatCols);
			
			String lbFnameAscii = "outputs/feModels/"+dsName+"_lb.txt";
			writeLbParamsAscii(lbFnameAscii, lbParams, numCatCols, numIntCols+numFloatCols, colIdxToName);
		}
	}
	
	private String[] readColumnIdxToName(String fileName) throws IOException {
		List<String> colNames = new ArrayList<>();

		try (FileInputStream fis = new FileInputStream(fileName)) {
			byte[] lengthBytes = new byte[4];

			while (fis.read(lengthBytes) == 4) {
				// convert 4 bytes to int (little-endian)
				int strLength = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

				byte[] strBytes = new byte[strLength];
				int read = 0;
				while (read < strLength) {
					int r = fis.read(strBytes, read, strLength - read);
					if (r == -1) throw new IOException("Unexpected EOF while reading column string");
					read += r;
				}

				// decode and split by comma
				String[] columnEntries = new String(strBytes, StandardCharsets.UTF_8).split(",");
				// optional: remove quotes and trim
				for (int i = 0; i < columnEntries.length; i++) {
					columnEntries[i] = columnEntries[i].replace("\"", "").trim();
				}

				//since each column is stored as a single comma-separated string
				//we just take the first element as the column name
				colNames.add(columnEntries[0]);
			}
		}

		String[] result = colNames.toArray(new String[0]);
		return result;
	}
	
	private void writeTransactionsToFile(String filename, List<List<ItemUtility>> allTransactions, List<Float> allTutils) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (int i = 0; i < allTransactions.size(); i++) {
                List<ItemUtility> transaction = allTransactions.get(i);
                Float tutil = allTutils.get(i);
				sb.setLength(0);
                for (int pi=0;pi<transaction.size();pi++) {
                    //sb.append(transaction.get(pi).itemRef);//actual column details (binned or label encoded)
					sb.append(transaction.get(pi).item);//item id only
					if (pi<transaction.size()-1) sb.append(" ");
                }
				sb.append(":").append(tutil).append(":");
				for (ItemUtility pair : transaction) {
                    sb.append(pair.utility).append(" ");
                }
				writer.write(sb.toString().trim());
                writer.newLine(); 
            }
        }
    }
	
	private void writeLbParams(String filename, List<Map<String, Integer>> lbParams, int numCatCols) {
		try (FileOutputStream fos = new FileOutputStream(filename);
			FileChannel fileChannel = fos.getChannel()) {
			int sizePrefix = Integer.BYTES;
			for (int i = 0; i < numCatCols; i++) {
				Map<String, Integer> params = lbParams.get(i);
				int lineLength = 0;
				for (Map.Entry<String, Integer> entry : params.entrySet()) {
					byte[] keyBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
					int keyLength = keyBytes.length;
					lineLength += Integer.BYTES + keyLength + Integer.BYTES; //key length size, actual bytes of string, integer value
				}
				ByteBuffer buffer = ByteBuffer.allocate(lineLength + sizePrefix);//allocate buffer with space for line length prefix and line data
				buffer.putInt(lineLength);
				for (Map.Entry<String, Integer> entry : params.entrySet()) {
					byte[] keyBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
					int keyLength = keyBytes.length;
					int value = entry.getValue();

					buffer.putInt(keyLength);//number of bytes of string
					buffer.put(keyBytes);//actual string
					buffer.putInt(value);
				}
				buffer.flip();
				while (buffer.hasRemaining()) {
					fileChannel.write(buffer);
				}
			}
		} catch (IOException e) {
			System.err.println("Error writing to file: " + e.getMessage());
		}
	}
	
	private void writeLbParamsAscii(String filename,
                                List<Map<String, Integer>> lbParams,
                                int numCatCols, int numIntFloatCols, String[] colIdxToName) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
			for (int i = 0; i < numCatCols; i++) {
				Map<String, Integer> params = lbParams.get(i);
				StringBuilder line = new StringBuilder();
				//line.append("Column ").append(i).append(": ");
				line.append(colIdxToName[numIntFloatCols + i]).append(", ");
				boolean first = true;
				for (Map.Entry<String, Integer> entry : params.entrySet()) {
					if (!first) {
						line.append(", ");
					}
					line.append(entry.getKey())
						.append("=")
						.append(entry.getValue());
					first = false;
				}
				writer.write(line.toString());
				writer.newLine();
			}
		} catch (IOException e) {
			System.err.println("Error writing to file: " + e.getMessage());
		}
	}
	
	private void loadLbParams(String filename, List<Map<String, Integer>> resultMap) {
		try (FileInputStream fis = new FileInputStream(filename);
			FileChannel fileChannel = fis.getChannel()) {
			ByteBuffer lengthBuffer = ByteBuffer.allocate(Integer.BYTES);
			while (fileChannel.read(lengthBuffer) != -1) {
				lengthBuffer.flip();
				int lineLength = lengthBuffer.getInt();
				lengthBuffer.clear();
				ByteBuffer lineBuffer = ByteBuffer.allocate(lineLength);
				fileChannel.read(lineBuffer);
				lineBuffer.flip();
				Map<String, Integer> map = new HashMap<>();
				while (lineBuffer.remaining() > 0) {
					int keyLength = lineBuffer.getInt();
					byte[] keyBytes = new byte[keyLength];
					lineBuffer.get(keyBytes);
					String key = new String(keyBytes, StandardCharsets.UTF_8);
					int value = lineBuffer.getInt();
					map.put(key, value);
				}
				resultMap.add(map);
				lengthBuffer.clear();
			}
		} catch (IOException e) {
			System.err.println("Error reading from file: " + e.getMessage());
		}
	}
	
	private void writeKbinsParams(String fileName, List<float[]> binEdgesList, int[] numBinsList, int numIntFloatCols) {
		try (FileChannel fileChannel = new FileOutputStream(fileName).getChannel()) {
			int totalSize = Integer.BYTES * numIntFloatCols; 
			for (float[] binEdges : binEdgesList) totalSize += Float.BYTES * binEdges.length; 
			
			ByteBuffer buffer = ByteBuffer.allocate(totalSize);
			for (int i = 0; i < numIntFloatCols; i++) {
				buffer.putInt(numBinsList[i]);
				float[] binEdges = binEdgesList.get(i);
				for (float edge : binEdges) {
					buffer.putFloat(edge);
				}
			}
			buffer.flip(); 
			fileChannel.write(buffer); 
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void writeKbinsParamsAscii(String fileName, List<float[]> binEdgesList,
                                   int[] numBinsList, int numIntFloatCols, String[] colIdxToName) {

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
			for (int i = 0; i < numIntFloatCols; i++) {
				StringBuilder line = new StringBuilder();
				line.append(colIdxToName[i]).append(",");
				line.append(numBinsList[i]); // number of bins

				float[] binEdges = binEdgesList.get(i);
				for (float edge : binEdges) {
					line.append(",").append(edge); // comma-separated edges
				}
				writer.write(line.toString());
				writer.newLine();
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void loadKbinsParams(String fileName, List<float[]> binEdgesList, int[] numBinsList, int numIntFloatCols) {
		try (FileChannel fileChannel = new FileInputStream(fileName).getChannel()) {
			//read the entire file into a ByteBuffer
			long fileSize = fileChannel.size();
			ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
			fileChannel.read(buffer);
			buffer.flip(); 
			for (int i = 0; i < numIntFloatCols; i++) {
				int numBins = buffer.getInt();
				numBinsList[i] = numBins;

				float[] binEdges = new float[numBins + 1];
				binEdgesList.add(binEdges);

				for (int j = 0; j < numBins+1; j++) binEdges[j] = buffer.getFloat();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void writeMsParams(String fileName, float[][] msParams, int numFloatCols) {
		try (FileChannel fileChannel = new FileOutputStream(fileName).getChannel()) {
			ByteBuffer buffer = ByteBuffer.allocate(8 * numFloatCols); //Allocate buffer for 2 floats (4 bytes each) per column
			for (int i = 0; i < numFloatCols; i++) {
				buffer.putFloat(msParams[i][0]); 
				buffer.putFloat(msParams[i][1]); 
			}
			buffer.flip(); 
			fileChannel.write(buffer); 
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void writeMsParamsAscii(String fileName, float[][] msParams, int numFloatCols,
										int numIntCols, String[] colIdxToName) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
			for (int col = 0; col < numFloatCols; col++) {
				String line = String.format(Locale.US, "%s, %.4f, %.4f",
						colIdxToName[numIntCols + col], //colname
						msParams[col][0],  //min
						msParams[col][1]); //max

				writer.write(line);
				writer.newLine();
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void loadMsParams(String fileName, float[][] msParams) {
		try (FileChannel fileChannel = new FileInputStream(fileName).getChannel()) {
			ByteBuffer buffer = ByteBuffer.allocate(8 * msParams.length); //allocate buffer for 2 floats (4 bytes each) per column
			fileChannel.read(buffer); 
			buffer.flip(); 
			for (int i = 0; i < msParams.length; i++) {
				msParams[i][0] = buffer.getFloat(); 
				msParams[i][1] = buffer.getFloat(); 
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private int getBinName(int bi, int colIdx){
		return bi * 10000 + colIdx;
	}
	
	private float[] getProportion(int[] classCounts){
		float[] prop = new float [classCounts.length];//proportion of each class 
		int tot = 0;
		for (int i=0;i<classCounts.length;i++) tot += classCounts[i];
		for (int i=0;i<prop.length;i++) prop[i] = (float)classCounts[i]/tot;
		return prop;
	}
	
	private double getEntropy(int[] classCounts){
		float[] prop = getProportion(classCounts);//proportion of each class 
		double ent = 0;
		for (int i=0;i<prop.length;i++)
			if (prop[i]!=0)
				ent += (prop[i]*(Math.log(prop[i])/Math.log(classCounts.length)));
		if (ent==0) return ent;
		return -ent;
	}
	
	private int[] getOverallClassCounts(int[] ytrain, int num_classes){
		int[] tmpclasscounts = new int [num_classes];
		for (int ci=0;ci<num_classes; ci++) tmpclasscounts[ci] = 0;
		for (Integer yy : ytrain) tmpclasscounts[yy] += 1;
		return tmpclasscounts;
	}
	
	private int[] getRelevantYValues(int[] x, int[] y, int d) {
        List<Integer> relevantYValues = new ArrayList<>();
        for (int i = 0; i < x.length; i++) {
            if (x[i] == d) {
                relevantYValues.add(y[i]);
            }
        }
        return relevantYValues.stream().mapToInt(Integer::intValue).toArray();
    }
	
	private double entropyColLevel(int[] x, int[] y, int num_classes) {
        Set<Integer> distinctVals = new HashSet<>();
        for (int value : x) distinctVals.add(value);

        int totLen = y.length;
        double cumEnt = 0.0;
		for (int d : distinctVals) {
            int[] rely = getRelevantYValues(x, y, d);
            cumEnt += ((double) rely.length / totLen) * getEntropy(getOverallClassCounts(rely, num_classes));
        }
        return cumEnt;
    }
	
	private double getInitialEntropy(int[] y, int num_classes){
		int[] pClassCounts = new int [num_classes];
		pClassCounts = getOverallClassCounts(y, num_classes);
		return getEntropy(pClassCounts);
	}
	
	private double getIG(int[] x, int[] y, double initialEntropy, int num_classes){
		double colEntropy = entropyColLevel(x, y, num_classes);
		double gain = initialEntropy - colEntropy;
        return Math.round(gain*1e6)/1e6;
	}
	
	private int compute_num_bins(int[] col, int[] y, int num_classes, int nbStart, int nbEnd){
		double initialEntropy = getInitialEntropy(y, num_classes);
		double bestIgVal = 0;
		int bestNb = nbStart;
		int[] discretizedX;
		for (int nb=nbStart;nb<=nbEnd;nb++){
			KBinsDiscretizer discretizer = new KBinsDiscretizer(nb); 
			discretizedX  = discretizer.fitTransform(col);
			double ig = getIG(discretizedX, y, initialEntropy, num_classes);
			if (ig > bestIgVal){
				bestIgVal = ig;
				bestNb = nb;
			}	
		}
		return bestNb;
	}
	
	private int compute_num_bins(float[] col, int[] y, int num_classes, int nbStart, int nbEnd){
		double initialEntropy = getInitialEntropy(y, num_classes);
		double bestIgVal = 0;
		int bestNb = nbStart;
		int[] discretizedX;
		for (int nb=nbStart;nb<=nbEnd;nb++){
			KBinsDiscretizer discretizer = new KBinsDiscretizer(nb); 
			discretizedX  = discretizer.fitTransform(col);
			double ig = getIG(discretizedX, y, initialEntropy, num_classes);
			if (ig > bestIgVal){
				bestIgVal = ig;
				bestNb = nb;
			}	
		}
		return bestNb;
	}
	
	/**
     * Reads raw binary data, performs feature engineering (discretization/scaling),
     * calculates item utilities, and prepares the transaction list for the mining algorithm.
     *
     * @return The raising threshold (minUtility) based on the top-K items.
     * @throws IOException If file reading or parameter persistence fails.
     */
	public float prepare_data(String input, String dsName, int B, double imbWeights, int trainNumRows, List<List<ItemUtility>> transactions,
								List<Float> itemTWU, 
								Map<Integer, String> itemMap, List<Integer> ytrain, int topK,
								int numIntCols, int numFloatCols, int numCatCols, int numClasses) throws IOException{//training data
		
		String path = input+dsName;
		String yfname = path+"_y_train.bin", cfnameIdxToName=path+"_allColsIdxToName.bin";
		String[] colIdxToName = readColumnIdxToName(cfnameIdxToName);
		
		int[] y_train = new int [trainNumRows];
		readY(yfname, y_train);
		for (Integer yy : y_train) ytrain.add(yy);//used in AlgoTHUIsl
		
		//Map<Integer, Float> RIU = new HashMap<Integer, Float>();
		int ncols = numIntCols + numFloatCols + numCatCols;
		int[][] dfFinal = new int [ncols][trainNumRows];
		int[] nbByCol = new int [ncols];
		List<float []> binEdgesRight = new ArrayList<float []>();
		List<float [][]> binRangesOriginal = new ArrayList<float [][]>();
		
		List<String[]> catLabels = new ArrayList<String[]>();
		readFilesTrain(input, dsName, numIntCols, numFloatCols, numCatCols, dfFinal, binRangesOriginal, binEdgesRight, nbByCol, 
									B, y_train, (B == -1), numClasses, colIdxToName, catLabels);
		
		float[] corrVals=null, euVals=null, colData=null;
		if (numIntCols+numFloatCols>0){
			corrVals = new float [numIntCols + numFloatCols];
			euVals = new float [numIntCols + numFloatCols];
			colData = new float [y_train.length];
			for (int colIdx = 0; colIdx < numIntCols+numFloatCols; colIdx++) {
				for (int i=0;i<dfFinal[0].length;i++) colData[i] = (float)dfFinal[colIdx][i];
				float corr = (float)(Math.round(CorrelationCustom.getFastCorr(colData, y_train)*1e6))/1e6f;
				corrVals[colIdx] = corr;
				euVals[colIdx] = Math.abs(corr);
			}
		}
		List<Float> corrValsCat = null;
		List<Float> nmiScore = null;
		if (numCatCols>0){
			corrValsCat = new ArrayList<Float>();//added and retrieved in same sequence
			nmiScore = new ArrayList<Float>();
		
			int offset = numIntCols + numFloatCols;
			NMI nscore = new NMI();
			int[] colDataInt= new int [y_train.length];
			for (int colIdx = 0; colIdx < numCatCols; colIdx++) {
				int colNameIdx = offset + colIdx;
				int nbins = nbByCol[colNameIdx];
				for (int bi=1;bi<=nbins;bi++){
					for (int i=0;i<dfFinal[0].length;i++) colDataInt[i] = (dfFinal[colNameIdx][i]==bi?1:0);//binarize
					float corr = (float)(Math.round(CorrelationCustom.getFastCorr(colDataInt, y_train)*1e6))/1e6f;
					corrValsCat.add(corr);
					nmiScore.add((float)Math.round(nscore.getNMI(colDataInt, y_train)*1e6)/1e6f);
				}
			}
		}
		
		Map<Long, Float> tu = new HashMap<Long, Float>();
        Map<Integer, Float> tuByY = new HashMap<Integer, Float>();
		Map<Integer, Integer> binNameToItemCntr = new HashMap<Integer, Integer>();
		List<Integer> colNameNew = new ArrayList<Integer>();
		StringBuilder sb = new StringBuilder();
		String labelValue;
		float euT, iuT, euIuT;
		int currNumBins;
		int corrValsCntr = -1;
		String[] labelValues = null;
		int itemCntr = 0;
		for (int colIdx = 0; colIdx < ncols; colIdx++) {
			currNumBins = nbByCol[colIdx];
			if (colIdx>=numIntCols + numFloatCols){
				labelValues = catLabels.get(colIdx - numIntCols - numFloatCols);//index is offset by the initial set of int and float columns
			}
			
			for (int bi = 1; bi <= currNumBins; bi++) {
				Integer binName = getBinName(bi, colIdx);
				if (colIdx < numIntCols + numFloatCols){//non cat cols
					euT = euVals[colIdx];
					iuT = (corrVals[colIdx] > 0 ? binEdgesRight.get(colIdx)[bi - 1] : binEdgesRight.get(colIdx)[currNumBins - bi]);
					euIuT = euT*iuT;
					if (euIuT > 0){
						sb.setLength(0);
						sb.append(colIdxToName[colIdx]);sb.append("=[");sb.append(String.valueOf(binRangesOriginal.get(colIdx)[bi - 1][0]));
						sb.append("-");sb.append(String.valueOf(binRangesOriginal.get(colIdx)[bi - 1][1]));sb.append("]");
						//itemMap.put(binName, sb.toString());
						itemMap.put(itemCntr+1, sb.toString());
						colNameNew.add(binName);//use index+1 to map binName and itemCntr
						binNameToItemCntr.put(binName, ++itemCntr);
					}
				}else{//cat cols
					euT = nmiScore.get(++corrValsCntr);
					iuT = (corrValsCat.get(corrValsCntr) > 0 ? 1f : 0.05f);
					euIuT = euT*iuT;
					if (euIuT > 0) {
						labelValue = labelValues[bi-1];
						//itemMap.put(binName, labelValue);
						itemMap.put(itemCntr+1, labelValue);
						colNameNew.add(binName);
						binNameToItemCntr.put(binName, ++itemCntr);
					}
				}
				for (int yi=0;yi<numClasses;yi++){
					euIuT *= (yi>0?imbWeights:1f);//imbalanced weighting applied for the minority classes
					long tx = binName*1000+yi;
					tu.put(tx, euIuT);
					tuByY.put(yi, Math.max(tuByY.getOrDefault(yi, 0.0f), euIuT));
				}			
			}
		}
		writeColNameNewToFile(colNameNew, "outputs/"+dsName+"_colNamesNew.bin");
		writeColNameNewToFileAscii(colNameNew, binNameToItemCntr, itemMap, "outputs/"+dsName+"_colNamesNew.txt");
		tu.replaceAll((k, v) -> v / tuByY.get((int)(k % 1000)));//Normalize scores by y
        
		float[] RIU = new float [itemCntr];
		for (int i=0;i<itemCntr;i++){
			itemTWU.add(0f);
			RIU[i] = 0;
		}
		List<Float> allTutils = null;
		if (writeTransaction) allTutils = new ArrayList<Float>();
		for (int rowIdx = 0; rowIdx < dfFinal[0].length; rowIdx++) {
			int yi = y_train[rowIdx];
			float tutils = 0;
			List<ItemUtility> originalTransaction = new ArrayList<ItemUtility>();
			for (int colIdx=0;colIdx<dfFinal.length;colIdx++){
				int bi = (int)dfFinal[colIdx][rowIdx];
				Integer binName = getBinName(bi, colIdx);
				long tx = binName*1000+yi;
				if (tu.get(tx)==null || !colNameNew.contains(binName)) continue;
				
				float iutils = (float)(Math.round(tu.get(tx) * 1e6) / 1e6f);
				ItemUtility currt = new ItemUtility(binNameToItemCntr.get(binName), iutils);
				currt.itemRef = itemMap.get(binNameToItemCntr.get(binName));
				
				originalTransaction.add(currt);
				tutils += iutils;
			}
			if (writeTransaction) allTutils.add(tutils);
			if (tutils>0){
				transactions.add(originalTransaction);
				for (ItemUtility p :  originalTransaction){
					itemTWU.set(p.item-1, itemTWU.get(p.item-1) + tutils);
					RIU[p.item-1] += p.utility;
				}
			}else{
				ItemUtility u = new ItemUtility(-1, 0f);
				List<ItemUtility> ulist = new ArrayList<ItemUtility>();
				ulist.add(u);
				transactions.add(ulist);
			}
		}
		if (writeTransaction) writeTransactionsToFile("outputs/"+dsName+"_x_train_processed.txt", transactions, allTutils);
		float minUtility = raisingThresholdRIU(RIU, topK);
		return minUtility;
	}
	
	/**
     * Applies learned transformations (bins, scaling, labels) to test data.
     * Maps test observations to the item IDs generated during the training phase.
     *
     * @throws IOException If test files or saved model parameters cannot be accessed.
     */
	public void prepare_data_apply(String input, String dsName, int foldNo, int testNumRows, List<List<Integer>> testTransactions,
									int numIntCols, int numFloatCols, int numCatCols) throws IOException{//apply on test data
		
		int[][] dfFinal = new int [numIntCols + numFloatCols + numCatCols][testNumRows];
		List<Integer> colNameNew = new ArrayList<Integer>();
		readFilesTest(input, dsName, numIntCols, numFloatCols, numCatCols, dfFinal, colNameNew); 
		int index=-1;
		for (int rowIdx = 0; rowIdx < dfFinal[0].length; rowIdx++) {//prepare transactions
			List<Integer> originalTransaction = new ArrayList<Integer>();
			for (int colIdx=0;colIdx<dfFinal.length;colIdx++){
				Integer binName = getBinName(dfFinal[colIdx][rowIdx], colIdx);
				index = colNameNew.indexOf(binName);//index contains the mapped entry
				if (index!=-1) originalTransaction.add(index+1);//itemCntr starts from 1
			}
			if (originalTransaction.size()>0) testTransactions.add(originalTransaction);
			else testTransactions.add(new ArrayList<Integer>(Collections.singletonList(-1)));
		}		
	}
	
	private float raisingThresholdRIU(float[] values, int k) {
		float minUtility = 0;
		List<Float> list = new ArrayList<Float>();
		for (float value : values) list.add(value);
		Collections.sort(list, Collections.reverseOrder());
		if (list.size() >= k && k > 0) {
			minUtility = list.get(k - 1);
		}

		return minUtility;
	}
}
