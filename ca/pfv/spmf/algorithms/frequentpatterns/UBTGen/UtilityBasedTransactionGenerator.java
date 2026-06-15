package ca.pfv.spmf.algorithms.frequentpatterns.UBTGen;

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
 * This class implements the Utility-Based Transaction Generator (UBTGen).
 * 
 * It transforms a supervised learning dataset into a utility transaction
 * database suitable for High-Utility Itemset Mining (HUIM).
 *
 * The output follows the standard SPMF utility transaction format:
 * items:transactionUtility:itemUtilities
 *
 * @author Srikumar Krishnamoorthy
 */
 
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.lang.Math;
import java.util.Set;
import java.util.HashSet;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

public class UtilityBasedTransactionGenerator{
	public UtilityBasedTransactionGenerator(){}
	
	private void writeLbParams(String filename, List<Map<String, Integer>> lbParams, int offset, int numCatCols, Map<Integer, String> colIdxToName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write("column, label:index\n");
			StringBuilder sb = new StringBuilder();
			String keyValuePairs;
            for (int i = 0; i < numCatCols; i++) {
                keyValuePairs = lbParams.get(i).entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue()).collect(Collectors.joining(";"));
                sb.append(colIdxToName.get(offset+i));sb.append(",");sb.append(keyValuePairs);sb.append("\n");
            }
			writer.write(sb.toString());
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }
	
	private void writeKbinsParams(String fileName, List<float[]> binEdgesList, List<Integer> numBinsList, int numIntFloatCols, Map<Integer, String> colIdxToName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
			StringBuilder sb = new StringBuilder();
			String edgesStr;
			for (int i = 0; i < numIntFloatCols; i++) {
                edgesStr = Arrays.toString(binEdgesList.get(i)).replace("[", "").replace("]", "").replace(" ", "").replace(","," ");
				sb.append(colIdxToName.get(i));sb.append(",");sb.append(numBinsList.get(i));sb.append(",");
				sb.append("[");sb.append(edgesStr);sb.append("]");
				sb.append("\n");
            }
			writer.write(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	
	private void writeMsParams(String fileName, List<Float[]> msParams, int offset, int numFloatCols, Map<Integer, String> colIdxToName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            StringBuilder sb = new StringBuilder();
			for (int i = 0; i < numFloatCols; i++) {//write columnName, dataMin, dataMax 
                sb.append(colIdxToName.get(offset + i));sb.append(",");sb.append(msParams.get(i)[0]);sb.append(",");sb.append(msParams.get(i)[1]);sb.append("\n");
            }
			writer.write(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	
	public void writeColNameNewToFile(List<Integer> cols, Map<Integer, Integer> binNameToItemCntr, Map<Integer, String> itemMap, 
										String filename) throws IOException { 
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (int i=0;i<cols.size();i++){
				writer.write(cols.get(i).toString());//itemCntr, binName
				writer.write(" ");
				writer.write(binNameToItemCntr.get(cols.get(i)).toString());
				writer.write(" ");
				writer.write(itemMap.get(binNameToItemCntr.get(cols.get(i))));
				
				writer.newLine();
			}
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	
	/**
	 * Transforms input dataset columns (integer, float, and categorical) into a processed format suitable
	 * for machine learning models. This function discretizes numeric columns using KBinsDiscretizer,
	 * normalizes float columns with MinMaxScaler, and binarizes categorical columns using LabelBinarizer.
	 * It also optionally writes transformation parameters to disk.
	 *
	 * The function updates the provided dfFinal array in-place, storing the transformed values
	 * for each column. It computes the number of bins per column based on the distinct values or a
	 * specified number of bins, and maintains metadata for later use (e.g., normalized bin edges, min/max
	 * values, and label mappings).
	 *
	 * @param numIntCols          Number of integer columns in the dataset.
	 * @param numFloatCols        Number of float columns in the dataset.
	 * @param numCatCols          Number of categorical columns in the dataset.
	 * @param data_int            2D array containing original integer column values.
	 * @param data_float          2D array containing original float column values.
	 * @param data_cat            2D array containing original categorical column values.
	 * @param dfFinal             2D array to store transformed column values (updated in-place).
	 * @param binEdgesRight       List to store normalized right edges of bins for numeric columns.
	 * @param nbByCol             Array to store the number of bins or classes per column.
	 * @param numBins             Default number of bins to use if not automatically computed.
	 * @param y_train             Target values array used for computing optimal number of bins.
	 * @param computeNumBinsFlag  Flag indicating whether to compute number of bins automatically per column.
	 * @param num_classes         Number of target classes (used for bin computation).
	 * @param colIdxToName        Mapping from column index to column name (used for writing parameters).
	 * @param catLabels           List to store label strings for categorical columns after binarization.
	 * @param outFilePrefix       Prefix for output files where transformation parameters will be saved.
	 * @param writeTransformParameters Flag indicating whether to write transformation parameters to disk.
	 *
	 * Numeric columns (integer and float) are discretized using KBinsDiscretizer, with float columns
	 * optionally scaled with MinMaxScaler. Categorical columns are encoded using LabelBinarizer. The
	 * function keeps track of missing values (-999) and handles them appropriately during discretization
	 * and scaling.
	 *
	 * After execution, dfFinal contains all transformed columns in the order: integer columns,
	 * float columns, categorical columns. Normalized bin edges for numeric columns and label mappings
	 * for categorical columns are optionally written to output files if writeTransformParameters is true.
	 */
	private void transformData(int numIntCols, int numFloatCols, int numCatCols, int[][] data_int, float[][] data_float, String[][] data_cat, int[][] dfFinal, 
						List<float [][]> binRangesOriginal, List<float []> binEdgesRight, int[] nbByCol, 
						int numBins, int[] y_train, boolean computeNumBinsFlag, int num_classes, 
						Map<Integer, String> colIdxToName, List<String[]> catLabels, String outFilePrefix, boolean writeTransformParameters) {
	
		List<float[]> kbParamsBE = new ArrayList<float []>();
		List<Integer> kbParamsNbins = new ArrayList<Integer>();
		if (numIntCols>0){
			List<Integer> colDataTmp = null;
			List<Integer> tmpY = null;
			int[] colData = new int [dfFinal[0].length];
			for (int colIdx = 0; colIdx < numIntCols; colIdx++) {
				boolean missingValueFound = false;
				for (int ii=0;ii<colData.length;ii++){
					if (data_int[colIdx][ii]==-999) missingValueFound=true;
					colData[ii] = data_int[colIdx][ii];
				}
				
				int distinctValCnt = (int) Arrays.stream(colData).distinct().count();
				if (missingValueFound) --distinctValCnt;//reduce count for -999
				int nb = Math.max(Math.min(distinctValCnt - 1, (computeNumBinsFlag?compute_num_bins(colData, y_train, num_classes, 2, 20):numBins)), 2);

				KBinsDiscretizer discretizer = new KBinsDiscretizer(nb); 
				dfFinal[colIdx] = discretizer.fitTransform(colData);
				float[] binEdges = discretizer.getBinEdges();
				kbParamsBE.add(binEdges);
				kbParamsNbins.add(binEdges.length-1);//duplicate removal may reduce the number of bins
				
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
		}
		if (numFloatCols>0){
			float[][] origRange;
			List<Float []> msParams = new ArrayList<Float []>();
			Set<Float> distinctElements = new HashSet<Float>();
					
			for (int colIdx = 0; colIdx < numFloatCols; colIdx++) {
				boolean missingValueFound = false;
				float[] colData = new float[dfFinal[0].length];	
				for (int ii=0;ii<colData.length;ii++){
					if (data_float[colIdx][ii]==-999) missingValueFound=true;
					colData[ii] = data_float[colIdx][ii];
				}
				
				MinMaxScaler scaler = new MinMaxScaler();
				scaler.fitTransform(colData);//in-place updates
				msParams.add(new Float []{scaler.dataMin, scaler.dataMax});
				
				Integer colNameIdx = numIntCols + colIdx;
				distinctElements.clear();
				for (float value : colData) distinctElements.add(value);
				int distinctValCnt = distinctElements.size();
				if (missingValueFound) --distinctValCnt;//reduce count for -999
				
				int nb = Math.max(Math.min(distinctValCnt - 1, (computeNumBinsFlag?compute_num_bins(colData, y_train, num_classes, 2, 20):numBins)), 2);
				KBinsDiscretizer discretizer = new KBinsDiscretizer(nb); 
				dfFinal[colNameIdx]  = discretizer.fitTransform(colData);
				float[] binEdges = discretizer.getBinEdges();
				kbParamsBE.add(binEdges);
				kbParamsNbins.add(binEdges.length-1);//duplicate removal may reduce the number of bins
				
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
			if (writeTransformParameters){
				String msFname = "outputs/transformParams/"+outFilePrefix+"_ms.txt";
				writeMsParams(msFname, msParams, numIntCols, numFloatCols, colIdxToName);
			}
		}
		if (writeTransformParameters && (numIntCols+numFloatCols)>0){
			String kbinsName = "outputs/transformParams/"+outFilePrefix+"_kbins.txt";
			writeKbinsParams(kbinsName, kbParamsBE, kbParamsNbins, numIntCols+numFloatCols, colIdxToName);
		}
		
		if (numCatCols>0){
			int offset = numIntCols + numFloatCols;
			List<Map<String, Integer>> lbParams = new ArrayList<Map<String, Integer>>();
			
			String[] colData = new String[dfFinal[0].length];		
			for (int colIdx = 0; colIdx < numCatCols; colIdx++) {
				for (int ii=0;ii<colData.length;ii++) colData[ii] = data_cat[colIdx][ii].trim();
				
				LabelBinarizer lb = new LabelBinarizer();
				lb.fit(colData);
				lbParams.add(lb.labelToIdx);

				int colNameIdx = offset + colIdx;
				nbByCol[colNameIdx] = lb.labelToIdx.size();
				dfFinal[colNameIdx] = lb.transformDense(colData);	
				
				String[] lbls = lb.getLabels(colIdxToName.get(colNameIdx));
				catLabels.add(lbls);				
			}
			if (writeTransformParameters){
				String lbFname = "outputs/transformParams/"+outFilePrefix+"_lb.txt";
				writeLbParams(lbFname, lbParams, offset, numCatCols, colIdxToName);
			}
		}
	}
	
	private void writeTransactionsToFile(List<String> allTransactions, String fileName) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            for (int i = 0; i < allTransactions.size(); i++) {
				writer.write(allTransactions.get(i).trim());
                writer.newLine(); 
            }
        }
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
	
	private int getBinName(int bi, int colIdx){
		return bi * 10000 + colIdx;
	}
	
	public void prepare_data(int[][] data_int, float[][] data_float, String[][] data_cat, float[] y_train, int[] y_train_int, int[][] dfFinal,
								int B, int numRows, int ncols, int numClasses, Map<Integer, String> colIdxToName, double imbWeights,
								List<String> transactions, String outFilePrefix, boolean missingValueImputation, boolean writeTransformParameters) throws IOException{
		
		int[] nbByCol = new int [ncols];
		List<float []> binEdgesRight = new ArrayList<float []>();
		List<float [][]> binRangesOriginal = new ArrayList<float [][]>();
		
		int numIntCols = data_int.length, numFloatCols = data_float.length, numCatCols = data_cat.length;
		List<String[]> catLabels = new ArrayList<String[]>();
		transformData(numIntCols, numFloatCols, numCatCols, data_int, data_float, data_cat, dfFinal, binRangesOriginal, binEdgesRight, nbByCol, 
									B, y_train_int, (B == -1), numClasses, colIdxToName, catLabels, outFilePrefix, writeTransformParameters);
									
		
		String[] colIdxToNameString = new String [numIntCols+numFloatCols+numCatCols];
		for (int i=0;i<colIdxToNameString.length;i++)
			colIdxToNameString[i] = colIdxToName.get(i);
		
		float[] corrVals=null, euVals=null, colData=null;
		if (numIntCols+numFloatCols>0){
			List<Float> colDataTmp=null;
			List<Integer> tmpY=null;
			
			corrVals = new float [numIntCols + numFloatCols];
			euVals = new float [numIntCols + numFloatCols];
			if (missingValueImputation)//no missing value after imputation
				colData = new float [y_train.length];
			for (int colIdx = 0; colIdx < numIntCols+numFloatCols; colIdx++) {
				float corr = 0.0f;
				if (missingValueImputation){
					for (int i=0;i<dfFinal[0].length;i++) colData[i] = (float)dfFinal[colIdx][i];
					corr = (float)(Math.round(CorrelationCustom.getFastCorr(colData, y_train_int)*1e6))/1e6f;
				}else{
					colDataTmp = new ArrayList<Float>();
					tmpY = new ArrayList<Integer>();
					for (int i=0;i<dfFinal[0].length;i++){
						if (dfFinal[colIdx][i]!=-999){
							colDataTmp.add((float)dfFinal[colIdx][i]);
							tmpY.add(y_train_int[i]);
						}
					}
					colData = new float [colDataTmp.size()];
					int[] y_train_tmp = new int [colDataTmp.size()];
					for (int i=0;i<colDataTmp.size();i++){
						colData[i] = colDataTmp.get(i);
						y_train_tmp[i] = tmpY.get(i);
					}
					corr = (float)(Math.round(CorrelationCustom.getFastCorr(colData, y_train_tmp)*1e6))/1e6f;
				}
				corrVals[colIdx] = corr;
				euVals[colIdx] = Math.abs(corr);
			}
		}
		List<Float> corrValsCat = null;
		List<Float> nmiScore = null;
		if (numCatCols>0){
			List<Integer> colDataTmp = null;
			List<Integer> tmpY = null;
			
			corrValsCat = new ArrayList<Float>();//added and retrieved in same sequence
			nmiScore = new ArrayList<Float>();
		
			int offset = numIntCols + numFloatCols;
			NMI nscore = new NMI();
			
			int[] colDataInt= null;
			
			if (missingValueImputation)//no missing value after imputation
				colDataInt = new int [y_train.length];
			
			for (int colIdx = 0; colIdx < numCatCols; colIdx++) {
				int colNameIdx = offset + colIdx;
				int nbins = nbByCol[colNameIdx];
				
				for (int bi=1;bi<=nbins;bi++){
					float corr = 0.0f;
					if (missingValueImputation){
						for (int i=0;i<dfFinal[0].length;i++) colDataInt[i] = (dfFinal[colNameIdx][i]==bi?1:0);//binarize
						corr = (float)(Math.round(CorrelationCustom.getFastCorr(colDataInt, y_train_int)*1e6))/1e6f;
						corrValsCat.add(corr);
						nmiScore.add((float)Math.round(nscore.getNMI(colDataInt, y_train_int)*1e6)/1e6f);
					}else{
						colDataTmp = new ArrayList<Integer>();
						tmpY = new ArrayList<Integer>();
						for (int i=0;i<dfFinal[0].length;i++){
							if (dfFinal[colNameIdx][i]!=-999){
								colDataTmp.add(dfFinal[colNameIdx][i]);
								tmpY.add(y_train_int[i]);
							}
						}
						colDataInt = new int [colDataTmp.size()];
						int[] y_train_tmp = new int [colDataTmp.size()];
						for (int i=0;i<colDataTmp.size();i++){
							colDataInt[i] = colDataTmp.get(i);
							y_train_tmp[i] = tmpY.get(i);
						}
						corr = (float)(Math.round(CorrelationCustom.getFastCorr(colDataInt, y_train_tmp)*1e6))/1e6f;
						corrValsCat.add(corr);
						nmiScore.add((float)Math.round(nscore.getNMI(colDataInt, y_train_tmp)*1e6)/1e6f);					
					}
				}
			}
		}
		
		Map<Long, Float> tu = new HashMap<Long, Float>();
        Map<Integer, Float> tuByY = new HashMap<Integer, Float>();
		Map<Integer, Integer> binNameToItemCntr = new HashMap<Integer, Integer>();
		List<Integer> colNameNew = new ArrayList<Integer>();
		StringBuilder sb = new StringBuilder();
		String labelValue;
		Map<Integer, String> itemMap = new HashMap<Integer, String>();	
		
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
						sb.append(colIdxToNameString[colIdx]);sb.append("=[");sb.append(String.valueOf(binRangesOriginal.get(colIdx)[bi - 1][0]));
						sb.append("-");sb.append(String.valueOf(binRangesOriginal.get(colIdx)[bi - 1][1]));sb.append("]");
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
		String colNameNewFile = "outputs/"+outFilePrefix+"_colNameNew.txt";
		writeColNameNewToFile(colNameNew, binNameToItemCntr, itemMap, colNameNewFile);
		
		tu.replaceAll((k, v) -> v / tuByY.get((int)(k % 1000)));//Normalize scores by y
        
		StringBuilder sbItems = new StringBuilder();
		StringBuilder sbUtils = new StringBuilder();
		for (int rowIdx = 0; rowIdx < dfFinal[0].length; rowIdx++) {
			int yi = y_train_int[rowIdx];
			float tutils = 0;
			sbItems.setLength(0);
			sbUtils.setLength(0);
			for (int colIdx=0;colIdx<dfFinal.length;colIdx++){
				if (dfFinal[colIdx][rowIdx]==-999) continue;//missing value 
				
				int bi = dfFinal[colIdx][rowIdx];
				Integer binName = getBinName(bi, colIdx);
				long tx = binName*1000+yi;
				if (tu.get(tx)==null || !colNameNew.contains(binName)) continue;
				
				float iutils = (float)(Math.round(tu.get(tx) * 1e6) / 1e6f);
				sbItems.append(binNameToItemCntr.get(binName));
				if (colIdx<dfFinal.length-1) sbItems.append(" ");
				sbUtils.append(iutils);
				if (colIdx<dfFinal.length-1) sbUtils.append(" ");
				
				tutils += iutils;
			}
			tutils = (float)(Math.round(tutils * 1e6) / 1e6f);
			if (tutils>0){
				StringBuilder trans = new StringBuilder();
				trans.append(sbItems.toString().trim());
				trans.append(":");
				trans.append(tutils);
				trans.append(":");
				trans.append(sbUtils.toString());
				transactions.add(trans.toString());
			}else{
				transactions.add("-1:0:0");//dummy transaction - may be skipped if alignment with original dataset is not required
			}
			
		}
	}
	
	private boolean containsIdx(int searchIdx, int[] data){
		for (int i=0;i<data.length;i++)
			if (data[i]==searchIdx) return true;
		return false;
	}
	
	private String[] parseLine(String line, String delimiter){
		//function that handles cases where delimiter is present inside quoted text e.g. PassengerName field in titanic dataset
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        java.util.List<String> tokens = new java.util.ArrayList<>();
        for (char c : line.toCharArray()) {
            if (c == '\"') { // Handle quotes
                inQuotes = !inQuotes;
            } else if (c == delimiter.charAt(0) && !inQuotes) { //Handle delimiter if not inside quotes
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());//Add the last token
        return tokens.toArray(new String[0]);
    }
	
	private boolean isMissingValue(String data){
		String[] missingValues = new String []{"","?","-7","-8"};//-7, -8 missing values in heloc dataset
		for (String s : missingValues) if (data.equals(s)) return true;
		return false;
	}
	
	private void imputeMissingIntFloat(int[][] data_int, float[][] data_float, List<Integer> missing_int, List<Integer> missing_float, 
											Map<Integer, String> colIdxToName){
		for (Integer missingCol : missing_int){
			int total = 0;
			int cntr = 0;
			for (int i=0;i<data_int[missingCol].length;i++){
				if (data_int[missingCol][i]!=-999){//not a missing value
					total += data_int[missingCol][i];
					cntr += 1;
				}
			}
			float meanVal = (float)total/cntr;
			int imputeVal = (int)Math.round(meanVal);
			for (int i=0;i<data_int[missingCol].length;i++){
				if (data_int[missingCol][i]==-999)//replace missing value with mean 
					data_int[missingCol][i] = imputeVal;
			}
		}
		int offset = data_int.length;
		for (Integer missingCol : missing_float){
			float total = 0f;
			int cntr = 0;
			for (int i=0;i<data_float[missingCol].length;i++){
				if (data_float[missingCol][i]!=-999){//not a missing value
					total += data_float[missingCol][i];
					cntr += 1;
				}
			}
			float imputeVal = total/cntr;
			for (int i=0;i<data_float[missingCol].length;i++){
				if (data_float[missingCol][i]==-999)//replace missing value with mean 
					data_float[missingCol][i] = imputeVal;
			}
		}
	}
	
	private void imputeMissingCat(String[][] data_cat, List<Integer> missing_cat, int offset, Map<Integer, String> colIdxToName){
		for (Integer missingCol : missing_cat){
			Map<String, Integer> vals = new HashMap<String, Integer>();
			for (String s : data_cat[missingCol])
				if (!s.equals("-999"))
					vals.put(s, vals.getOrDefault(s, 0) + 1);
			
			int maxCatCnt=0;
			String maxCat="";
			for(Map.Entry<String, Integer> entry : vals.entrySet()){
				if (entry.getValue()>maxCatCnt){
					maxCatCnt = entry.getValue();
					maxCat = entry.getKey();
				}
			}
			for (int i=0;i<data_cat[missingCol].length;i++)
				if (data_cat[missingCol][i].equals("-999"))//replace missing value with mode 
					data_cat[missingCol][i] = maxCat;	
		}
	}
	
	/**
     * Runs the utility-based transaction generator.
     *
     * @param inputFile input dataset path
     * @param outputFile output transaction file path
     * @param header whether input contains header
     * @param delimiter column delimiter
     * @param targetColIndex index of target column
     * @param skipColsIndices indices of columns to skip
     * @param numericIntColsIndices integer column indices
     * @param numericFloatColsIndices float column indices
     * @param catColsIndices categorical column indices
     * @param B number of discretization bins (-1 for automatic selection)
     * @param writeTransformParameters whether to export transformation parameters
     * @param missingValueImputation whether to apply missing value imputation
     */
	public void runGenerator(String inputFile, String outputFile, boolean header, String delimiter, int targetColIndex, 
						int[] skipColsIndices, int[] numericIntColsIndices, int[] numericFloatColsIndices, int[] catColsIndices, 
						int B, boolean writeTransformParameters, boolean missingValueImputation) throws IOException{
							
		BufferedReader myInput = null;
		String thisLine;
		
		//first pass to determine number of rows 
		int numRows = 0;
		int ncols = numericIntColsIndices.length + numericFloatColsIndices.length + catColsIndices.length;
		Map<Integer, String> colIdxToName = new HashMap<Integer, String>();
		String[] tmp = outputFile.split("/");
		String outFilePrefix = tmp[tmp.length-1].split("\\.")[0].replace(" ", "");//extract filename without extension and remove extra spaces
		try{
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(inputFile))));
			if (header){
				numRows = -1;
			}else{
				int intCntr=-1, floatCntr=-1, catCntr=-1;
				for (int i=0;i<ncols;i++){
					if (containsIdx(i, skipColsIndices)) continue;
					else if (containsIdx(i, numericIntColsIndices)) colIdxToName.put(++intCntr, String.valueOf(i+1));
					else if (containsIdx(i, numericFloatColsIndices)) colIdxToName.put(numericIntColsIndices.length+(++floatCntr), String.valueOf(i+1));
					else if (containsIdx(i, catColsIndices)) colIdxToName.put(numericIntColsIndices.length+ numericFloatColsIndices.length + (++catCntr), String.valueOf(i+1));
					else{}//target column
					//colIdxToName.put(i, String.valueOf(i+1));//no column name specified, using consecutive integer values
				}
			}
			while ((thisLine = myInput.readLine()) != null) {
				if (++numRows==0){//skip first row, when header is set to true
					String[] headerRow = thisLine.split(delimiter, -1);
					int intCntr=-1, floatCntr=-1, catCntr=-1;
					for (int i=0;i<headerRow.length;i++){
						if (containsIdx(i, skipColsIndices)) continue;
						else if (containsIdx(i, numericIntColsIndices)) colIdxToName.put(++intCntr, headerRow[i]);
						else if (containsIdx(i, numericFloatColsIndices)) colIdxToName.put(numericIntColsIndices.length+(++floatCntr), headerRow[i]);
						else if (containsIdx(i, catColsIndices)) colIdxToName.put(numericIntColsIndices.length+ numericFloatColsIndices.length + (++catCntr), headerRow[i]);
						else{}//target column
						//colIdxToName.put(i, String.valueOf(i+1));//no column name specified, using consecutive integer values
					}
					continue;
				}
				String[] data = parseLine(thisLine, delimiter);
				if (outFilePrefix.equals("heloc_utility") && data[1].equals("-9")) {numRows--;continue;}//skip missing rows in heloc dataset 
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (myInput != null) myInput.close();
		}
		
		int[][] data_int = new int [numericIntColsIndices.length][numRows];//stored coulumn-wise
		float[][] data_float = new float [numericFloatColsIndices.length][numRows];
		String[][] data_cat = new String [catColsIndices.length][numRows];
		float[] y_train = new float [numRows];
		int[] y_train_int = new int [numRows];
		String[] ytrainStr = new String [numRows];
		
		List<Integer> missing_int=null, missing_float=null, missing_cat=null;
		if (missingValueImputation){
			missing_int = new ArrayList<Integer>();
			missing_float = new ArrayList<Integer>();
			missing_cat = new ArrayList<Integer>();
		}
        
		//second pass, read and store the data for preparing transaction level data
		try{
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(inputFile))));
			int lineCntr = 0;
			if (header) lineCntr = -1;
			while ((thisLine = myInput.readLine()) != null) {
				if (++lineCntr==0){
					String[] data = parseLine(thisLine, delimiter);
					int intCntr=-1, floatCntr=-1, catCntr=-1;
					for (int i=0;i<data.length;i++){
						if (containsIdx(i, skipColsIndices)) continue;
						else if (containsIdx(i, numericIntColsIndices)) colIdxToName.put(++intCntr, data[i]);
						else if (containsIdx(i, numericFloatColsIndices)) colIdxToName.put(numericIntColsIndices.length+(++floatCntr), data[i]);
						else if (containsIdx(i, catColsIndices)) colIdxToName.put(numericIntColsIndices.length+ numericFloatColsIndices.length + (++catCntr), data[i]);
						else{}//target column
					}
					continue;//skip first row, when header is set to true
				}
				String[] data = parseLine(thisLine, delimiter);
				if (outFilePrefix.equals("heloc_utility") && data[1].equals("-9")) {lineCntr--;continue;}//skip missing rows in heloc dataset 
			
				int intCntr=-1, floatCntr=-1, catCntr=-1;
				//System.out.println("data "+Arrays.toString(data));
				for (int i=0;i<data.length;i++){
					if (containsIdx(i, skipColsIndices)) continue;
					if (i==targetColIndex){//target or label column
						//y_train[lineCntr-1] = Float.parseFloat(data[i]);	
						ytrainStr[lineCntr-1] = data[i];//y_train may be a categorical variable, label encode to integer values 
					}else if (containsIdx(i, numericIntColsIndices)){
						data_int[++intCntr][lineCntr-1] = (isMissingValue(data[i])?-999:Integer.parseInt(data[i])); //-999 used as a missing value code, and referred in multiple files
						if (missingValueImputation && isMissingValue(data[i]) && !missing_int.contains(intCntr)) missing_int.add(intCntr);
					}else if (containsIdx(i, numericFloatColsIndices)){
						data_float[++floatCntr][lineCntr-1] = (isMissingValue(data[i])?-999:Float.parseFloat(data[i])); //-999 used as a missing value code 
						if (missingValueImputation && isMissingValue(data[i]) && !missing_float.contains(floatCntr)) missing_float.add(floatCntr);
					}else{//cat columns
						data_cat[++catCntr][lineCntr-1] = (isMissingValue(data[i].trim())?"-999":data[i].trim()); //"-999" used as a missing value code
						if (missingValueImputation && isMissingValue(data[i].trim()) && !missing_cat.contains(catCntr)) missing_cat.add(catCntr);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (myInput != null) myInput.close();
		}

		if (missingValueImputation && (missing_int.size()+missing_float.size())>0)
			imputeMissingIntFloat(data_int, data_float, missing_int, missing_float, colIdxToName);//replace -999 with mean (round to nearest integer for integer columns)
		
		if (missingValueImputation && missing_cat.size()>0)
			imputeMissingCat(data_cat, missing_cat, data_int.length+data_float.length, colIdxToName);//replace -999 with mode
				
		
		LabelBinarizer lb = new LabelBinarizer();
		lb.fit(ytrainStr);//index starts at 1
		int[] ytmp = lb.transformDense(ytrainStr);
		for (int i=0;i<y_train.length;i++)
			y_train[i] = (float)(ytmp[i]-1);//-1 for starting with label value of 0
		
		for (int i = 0; i < y_train.length; i++) y_train_int[i] = (int) y_train[i];  
		List<Integer> uniqueY = Arrays.stream(y_train_int).distinct().boxed().collect(Collectors.toList());
		
		int[][] dfFinal = new int [ncols][numRows];
		
		double imbWeights = 1.0;//additional weights to be assigned to minority class
		
		List<String> transactions = new ArrayList<String>();//format: standard HUI dataset format 
															//list of items separated by space:total transaction utility:list of item utilities separated by space
		//prepare transaction level data
		prepare_data(data_int, data_float, data_cat, y_train, y_train_int, dfFinal, B, numRows, ncols, uniqueY.size(), 
						colIdxToName, imbWeights, transactions, outFilePrefix, missingValueImputation, writeTransformParameters);
		
		//write the generated transactions to a file (written to outputs folder)
		writeTransactionsToFile(transactions, outputFile);
	}
}
