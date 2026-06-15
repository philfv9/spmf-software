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
 * This class provides an example of how to run the UBTGen algorithm.
 * 
 * It reads parameters either from default values or from a configuration file,
 * and then calls UtilityBasedTransactionGenerator to generate
 * a utility transaction database from a supervised learning dataset.
 *
 * The configuration file uses the format:
 * parameterName=value
 *
 * @author Srikumar Krishnamoorthy
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AlgoUBTGen {
	
	public AlgoUBTGen() {
	}
	
	public int[] getData(String[] data){
		int[] res = new int [data.length];
		for (int i=0;i<data.length;i++) res[i] = Integer.parseInt(data[i]);
		return res;
	}
	
	public void createOutputDirs() throws IOException {
		Path baseDir = Paths.get("outputs");
		Files.createDirectories(baseDir.resolve("transformParams"));
	}
	
	/**
     * Main method to execute the UBTGen algorithm.
     *
     * @param args optional argument: path to a configuration file
     */
	public void runAlgorithm(String path, String[] args) throws IOException{
		
		//System.out.println("path "+path);
		String inputFile ="contextUBTGenTitanicSample.csv";
		boolean header=true;
		String outputFile="contextUBTGenTitanicSample_utility.csv";
		String delimiter=",";
        
		int targetColIndex=1;
		int[] skipColsIndices=new int []{0,3,8,10};//for titanic dataset - skipping passengerId, passenger name, ticket and cabin columns
		int[] numericIntColsIndices=new int []{6,7};//for titanic dataset - Age, SibSp, Parch columns
		int[] numericFloatColsIndices=new int []{5,9};//for titanic dataset - Fare column
		int[] catColsIndices=new int []{2,4,11};//for titanic dataset - Pclass, Sex, Embarked columns
		int B = 3;//number of discretized bins for integer and float columns, if unspecified or left blank, it will be automatically computed
		
		boolean writeTransformParameters = false;
		boolean missingValueImputation = false;

		String configFileName = path + "\\" + "configTitanic.txt";
		if (args.length>0){
			configFileName = path + "\\" + args[0];
			BufferedReader myInput = null;
			String thisLine;
			try {
				myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(configFileName))));
				while ((thisLine = myInput.readLine()) != null) {
					String[] param = thisLine.split("=");
					
					if (param.length==2 && param[0].equals("inputFile")) inputFile = path + "/" + param[1];
					else if (param.length==2 && param[0].equals("outputFile")) outputFile = "outputs/" + param[1];
					else if (param.length==2 && param[0].equals("header")) header = Boolean.parseBoolean(param[1]);
					else if (param.length==2 && param[0].equals("delimiter")) delimiter = param[1];
					else if (param.length==2 && param[0].equals("targetColIndex")) targetColIndex = Integer.parseInt(param[1]);
					else if (param[0].equals("skipColsIndices")){
						if (param.length==1) skipColsIndices = new int [0];//empty or no columns to skip
						else skipColsIndices = getData(param[1].split(","));
					}
					else if (param[0].equals("numericIntColsIndices")){
						if (param.length==1) numericIntColsIndices = new int [0];//empty or no int columns
						else numericIntColsIndices = getData(param[1].split(","));
					}
					else if (param[0].equals("numericFloatColsIndices")){
						if (param.length==1) numericFloatColsIndices = new int [0];//empty or no float columns
						else numericFloatColsIndices = getData(param[1].split(","));
					}
					else if (param[0].equals("catColsIndices")){
						if (param.length==1) catColsIndices = new int [0];//empty or no cat columns
						else catColsIndices = getData(param[1].split(","));
					}
					else if (param[0].equals("B")){
						if (param.length==1) B = -1;//automatically computed if left blank
						else B = Integer.parseInt(param[1]);
					}else if (param.length==2 && param[0].equals("writeTransformParameters")) writeTransformParameters = Boolean.parseBoolean(param[1]);
					else if (param.length==2 && param[0].equals("missingValueImputation")) missingValueImputation = Boolean.parseBoolean(param[1]);
					else continue;//unknown parameter
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (myInput != null) myInput.close();
			}	
		}
		/*System.out.println("inputFile \t\t "+inputFile);
		System.out.println("outputFile \t\t "+outputFile);
		System.out.println("targetColumnIndex \t "+targetColIndex);
		System.out.println("skipColIndices \t\t "+Arrays.toString(skipColsIndices));
		System.out.println("numericIntColsIndices \t "+Arrays.toString(numericIntColsIndices));
		System.out.println("numericFloatColsIndices  "+Arrays.toString(numericFloatColsIndices));
		System.out.println("catColsIndices \t\t "+Arrays.toString(catColsIndices));
		System.out.println("numberOfBins \t\t "+B);
		*/
		
		UtilityBasedTransactionGenerator tg = new UtilityBasedTransactionGenerator();
		tg.runGenerator(inputFile, outputFile, header, delimiter, targetColIndex, 
						skipColsIndices, numericIntColsIndices, numericFloatColsIndices, catColsIndices, B, writeTransformParameters, missingValueImputation);
		System.out.println("output utility file "+outputFile+" generated");
	}
}
