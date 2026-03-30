package ca.pfv.spmf.experiments.oneparametervaried;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.ProcessBuilder.Redirect;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ca.pfv.spmf.gui.preferences.PreferencesManager;

/* This file is copyright (c) 2021 Philippe Fournier-Viger
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
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
/**
 * Class that allows to run experiments by calling the SPMF jar file to run
 * algorithms.
 * 
 * @author Philippe Fournier-Viger
 *
 */
public class ExperimenterForParameterChange {

	/** The path to the JAR file of SPMF */
	private String spmfJarPath = "spmf.jar";

	/** The path for outputing the console output of algorithms */
	private String logFilePath = null;

	/** The string representing a timeout */
	private String timeoutCodeString = "TIMEOUT";

	/** The integer representing a timeout */
	private final int timeoutCode = -999;

	/**
	 * Object to properly display the double numbers with two decimals using the
	 * locale settings
	 */
	DecimalFormat formatTwoDecimals;

	/**
	 * Object to properly display the double numbers with all decimals using the
	 * locale settings
	 */
	DecimalFormat formatAllDecimals;

	/**
	 * Constructor;
	 */
	public ExperimenterForParameterChange() {
		// Setup the object to format double numbers with two decimals
		formatTwoDecimals = (DecimalFormat) NumberFormat.getNumberInstance();
		formatTwoDecimals.applyPattern("#.##");
		// Setupt the object to format double numbers with all decimals
		formatAllDecimals = (DecimalFormat) NumberFormat.getNumberInstance();
	}

	/**
	 * Run an algorithm a single time
	 * 
	 * @param algorithmName         the algorithm name
	 * @param args                  the arguments of the algorithm
	 * @param inputFile             the input file
	 * @param outputDirectory       the output directory
	 * @param timeoutInMilliseconds a time out (maximum time in milliseconds)
	 * @param compareOutputSize     a boolean indicating if the output size of
	 *                              algorithms should be compared as well
	 * @throws Exception if some error occurs
	 * @param showCommand show the command for each execution
	 * @param generatePGFPLOTFigures flag indicating if PGFPlot figures should be generated from the results
	 * @param variedParameterName  the name of the varied parameter (for displaying)
	 */
	public void runAnAlgorithmAndVaryParameter(String[] algorithmNames, String[] args, String varyingParameterValues[],
			String inputFile, String outputDirectory, int timeoutInMilliseconds, boolean compareOutputSize,
			boolean showCommand, boolean generatePGFPLOTFigures, String variedParameterName) throws Exception {

		// If the output directory does not exist, we must create it
		File directory = new File(outputDirectory);
		directory.mkdir();

		// Create the name of a log file, where we will store information about each run
		// of an algorithm
		String logFile = outputDirectory + File.separatorChar + "EXPERIMENT_LOG.txt";
		// If the log file exists, delete it
		File file = new File(logFile);
		if (file.exists()) {
			file.delete();
		}
		// Redirect the console output to that log file
		setRedirectOutputPath(logFile);

		// Find the position of the parameter to be varied, which should be indicated by
		// "##"
		int positionOfVariedParameter = -1;
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("##")) {
				positionOfVariedParameter = i;
				break;
			}
		}

		// If no parameter was found with ##, we tell the user that he should choose one
		if (positionOfVariedParameter == -1) {
			throw new Exception("ERROR: The parameter to be varied should be indicated with the value: ##");
		}

		// ========== PREPARE THE INITIAL COMMAND FOR THE EXPERIMENTS =============
		// The command will have a format like this:
		// java -jar spmf.jar run Apriori contextPasquier99.txt output.txt 40% 2

		List<String> commandWithParameters = new ArrayList<String>();
		commandWithParameters.add("java");
		commandWithParameters.add("-jar");
		commandWithParameters.add(spmfJarPath);
		commandWithParameters.add("run");
		commandWithParameters.add("PLACEHOLDER_ALGORITHM_NAME");
		if (inputFile != null) {
			commandWithParameters.add(inputFile);
		}
		commandWithParameters.add("PLACEHOLDER_OUTPUT_FILE");

		// Calculate the position of the varied parameter in the command
		int positionVariedParameterInCommand = commandWithParameters.size() + positionOfVariedParameter;
		for (String arg : args) {
			commandWithParameters.add(arg);
		}

		// Create array to store runtimes, memory usage, and the sizes of output files
		// First dimension: Algorithm Second dimension: parameter value
		double[][] runtimes = new double[algorithmNames.length][varyingParameterValues.length];
		double[][] memoryResults = new double[algorithmNames.length][varyingParameterValues.length];
		int[][] outputSizes = new int[algorithmNames.length][varyingParameterValues.length];

		System.out.println("********************************************");
		System.out.println("*****       RUNNING EXPERIMENTS        *****");
		System.out.println("********************************************");
		System.out.println(" INPUT: " + inputFile);
		System.out.println(" OUTPUT DIRECTORY: " + outputDirectory);
		System.out.println();
		int experimentCount = 1;

		// ==== FOR EACH ALGORITHM
		for (int m = 0; m < algorithmNames.length; m++) {

			// Change the algorithm name in the command
			String algorithmName = algorithmNames[m];
			commandWithParameters.set(4, algorithmName);

			// ========= FOR EACH PARAMETER VALUE OF THE VARIED PARAMETER ==================
			for (int j = 0; j < varyingParameterValues.length; j++) {

				// == Change the parameter value in the command
				String value = varyingParameterValues[j];
				commandWithParameters.set(positionVariedParameterInCommand, value);

				// Change the output file name in the command
				String outputFile = outputDirectory + File.separatorChar + algorithmName + "_" + value + ".txt";
				commandWithParameters.set(6, outputFile);

				// Print information about this experiment
				System.out.println(" *****  EXPERIMENT " + experimentCount++);
				System.out.println("   ALGORITHM: " + algorithmName + " \t" + variedParameterName + "= "
						+ convertDoubleStringToLocalizeString(value) + "  *****");

				// If the user wants to see the command (e.g. for debugging), we will show it
				if (showCommand) {
					// ===== Print the command for this experiment
					StringBuilder theCommandAsString = new StringBuilder("   COMMAND: ");
					for (String str : commandWithParameters) {
						theCommandAsString.append(str);
						theCommandAsString.append(' ');
					}
					System.out.println(theCommandAsString);
				}

				// ===== Create an object to run the command in a separated process
				ProcessBuilder pb = new ProcessBuilder(commandWithParameters);
				// Indicate that the output must be redirected to the log file
				pb.redirectOutput(Redirect.appendTo(new File(logFilePath)));
				pb.redirectError(Redirect.appendTo(new File(logFilePath)));

				// Record the start time
				long startTime = System.currentTimeMillis();

				// Launch the algorithm
				Process process = pb.start();

				// Wait until the timeout for the algorithm to terminate
				boolean exitValue = process.waitFor(timeoutInMilliseconds, TimeUnit.MILLISECONDS);

				// Record the end time
				long totalTime = System.currentTimeMillis() - startTime;

				// If the algorithm has timed-out
				if (exitValue == false) {
					System.out.println("   TIME: TIME-OUT");
					process.destroyForcibly();

					// Indicate in the results that the algorithm has timed-out
					runtimes[m][j] = timeoutCode;
					memoryResults[m][j] = timeoutCode;
					if (compareOutputSize) {
						outputSizes[m][j] = timeoutCode;
					}

				} else {
					// If the algorithm has not timed-out

					// Record the execution time  (in seconds)
					runtimes[m][j] = totalTime / 1000d;

					// Record the memory usage
					memoryResults[m][j] = PreferencesManager.getInstance().getLastMemoryUsage();

					// Print information
					System.out.println("   TIME: " + formatTwoDecimals.format(runtimes[m][j]) + " s \t MEMORY: "
							+ formatTwoDecimals.format(memoryResults[m][j]) + " MB");
					System.out.print("   OUTPUT: " + new File(outputFile).getName());

					// Record the size of the output file if the user wants it
					if (compareOutputSize) {
						outputSizes[m][j] = calculateSizeOfFile(outputFile);
						System.out.print(" \t OUTPUT_SIZE: " + outputSizes[m][j] + " lines");
					}
					System.out.println();
					System.out.println();

				}
			}
		}

		// ============ PRINT RESULTS
		System.out.println();
		System.out.println("********************************************");
		System.out.println("*****             RESULTS              *****");
		System.out.println("********************************************");

		// We will use a StringBuffer to temporarily store the results
		StringBuffer buffer = new StringBuffer();

		// Write general information (input file and parameters)
		buffer.append("INPUT FILE: ");
		buffer.append(inputFile);
		buffer.append(System.lineSeparator());
		buffer.append("PARAMETERS: ");
		buffer.append(Arrays.toString(args));
		buffer.append(System.lineSeparator());
		buffer.append(System.lineSeparator());

		/// ============================ TIME =======================
		// Write the time results
		buffer.append("TIME (S)");
		buffer.append(System.lineSeparator());
		// First we write all the values of the varied parameter
		buffer.append(variedParameterName + "\t ");
		for (int k = 0; k < varyingParameterValues.length; k++) {
			String parameterValue = varyingParameterValues[k];
			parameterValue = convertDoubleStringToLocalizeString(parameterValue);

			buffer.append(parameterValue + "\t");
		}
		buffer.append(System.lineSeparator());
		// Then, for each algorithm
		for (int m = 0; m < algorithmNames.length; m++) {
			buffer.append(algorithmNames[m] + "\t");
			// For each varied parameter value
			for (int k = 0; k < varyingParameterValues.length; k++) {
				// We write the runtime or time-out
				double value = runtimes[m][k];
				if (value == timeoutCode) {
					buffer.append(timeoutCodeString + "\t");
				} else {
					buffer.append(formatTwoDecimals.format(value) + "\t");
				}
			}
			buffer.append(System.lineSeparator());
		}

		buffer.append(System.lineSeparator());

		/// ============================ MEMORY =======================
		// Write the memory results
		buffer.append("MEMORY (MB)");
		buffer.append(System.lineSeparator());
		// First we write all the values of the varied parameter
		buffer.append(variedParameterName + "\t ");
		for (int k = 0; k < varyingParameterValues.length; k++) {
			String parameterValue = varyingParameterValues[k];
			parameterValue = convertDoubleStringToLocalizeString(parameterValue);

			buffer.append(parameterValue + "\t");
		}
		buffer.append(System.lineSeparator());
		// Then, for each algorithm
		for (int m = 0; m < algorithmNames.length; m++) {
			buffer.append(algorithmNames[m] + "\t");
			// For each varied parameter value
			for (int k = 0; k < varyingParameterValues.length; k++) {
				// We write the memory or time-out
				double value = memoryResults[m][k];
				if (value == timeoutCode) {
					buffer.append(timeoutCodeString + "\t");
				} else {
					buffer.append(formatTwoDecimals.format(value) + "\t");
				}
			}
			buffer.append(System.lineSeparator());
		}

		/// ========================== OUTPUT SIZES ==================
		// Write the output sizes results
		buffer.append(System.lineSeparator());
		buffer.append("OUTPUT_SIZE (LINES)");
		buffer.append(System.lineSeparator());
		// First we write all the values of the varied parameter
		buffer.append(variedParameterName + "\t ");
		for (int k = 0; k < varyingParameterValues.length; k++) {
			String parameterValue = varyingParameterValues[k];
			parameterValue = convertDoubleStringToLocalizeString(parameterValue);

			buffer.append(parameterValue + "\t");
		}
		buffer.append(System.lineSeparator());
		// Then, for each algorithm
		for (int m = 0; m < algorithmNames.length; m++) {
			buffer.append(algorithmNames[m] + "\t");
			// For each varied parameter value
			for (int k = 0; k < varyingParameterValues.length; k++) {
				// We write the output size or time-out
				int value = outputSizes[m][k];
				if (value == timeoutCode) {
					buffer.append(timeoutCodeString + "\t");
				} else {
					buffer.append(value + "\t");
				}
			}
			buffer.append(System.lineSeparator());
		}

		// =========== Print results to the console ===========
		String bufferAsString = buffer.toString();
		System.out.println(bufferAsString);

		// =========== Also, write result to the result file ===========
		String summaryFilePath = outputDirectory + File.separatorChar + "EXPERIMENT_RESULT.txt";
		BufferedWriter writer = new BufferedWriter(new FileWriter(summaryFilePath));
		writer.write(bufferAsString);
		writer.close();
		
		
		//===========================================================
		// if PGFPlot figures should be generated for this experiment
		if(generatePGFPLOTFigures) {
			buffer = new StringBuffer();
			buffer.append("\\documentclass{article}");
			buffer.append(System.lineSeparator());
			buffer.append("\\usepackage{tikz}");
			buffer.append(System.lineSeparator());
			buffer.append("\\usepackage{pgfplots}");
			buffer.append(System.lineSeparator());
			buffer.append("\\begin{document}");
			buffer.append(System.lineSeparator());
			buffer.append(System.lineSeparator());
			
			// ======= FIGURE FOR TIME
			buffer.append("\\begin{tikzpicture}");
			buffer.append(System.lineSeparator());
			buffer.append("\\begin{axis}[");  // xmin=0,ymin=0,
			buffer.append(System.lineSeparator());
			//  ====== TIME =========
			buffer.append("xlabel=$" + variedParameterName + "$,");
			buffer.append("ylabel=Time (s),cycle list name=color]");
			buffer.append(System.lineSeparator());
			// ==== FOR EACH ALGORITHM
			for (int m = 0; m < algorithmNames.length; m++) {
				String algorithmName = algorithmNames[m].replace('_', '-');

				buffer.append("\\addplot plot coordinates {");

				// ========= FOR EACH PARAMETER VALUE OF THE VARIED PARAMETER ==================
				for (int j = 0; j < varyingParameterValues.length; j++) {
					if(runtimes[m][j]>=0) {
						buffer.append("(");
						buffer.append(""+Double.parseDouble(varyingParameterValues[j]));
						buffer.append(",");
						buffer.append(""+runtimes[m][j]);
						buffer.append(")");
					}
				}
				buffer.append("};\\addlegendentry{" + algorithmName+"}");
			}
			buffer.append("\\end{axis}");
			buffer.append(System.lineSeparator());
			buffer.append("\\end{tikzpicture}");
			buffer.append(System.lineSeparator());
			buffer.append(System.lineSeparator());
			// =======  END OF FIGURE FOR TIME
			
			// ======= FIGURE FOR MEMORY
			buffer.append("\\begin{tikzpicture}");
			buffer.append(System.lineSeparator());
			buffer.append("\\begin{axis}[");  // xmin=0,ymin=0,
			buffer.append(System.lineSeparator());
			//  ====== TIME =========
			buffer.append("xlabel=$" + variedParameterName + "$,");
			buffer.append("ylabel=Memory (MB),cycle list name=color]");
			buffer.append(System.lineSeparator());
			// ==== FOR EACH ALGORITHM
			for (int m = 0; m < algorithmNames.length; m++) {
				String algorithmName = algorithmNames[m].replace('_', '-');

				buffer.append("\\addplot plot coordinates {");

				// ========= FOR EACH PARAMETER VALUE OF THE VARIED PARAMETER ==================
				for (int j = 0; j < varyingParameterValues.length; j++) {
					if(runtimes[m][j]>=0) {
						buffer.append("(");
						buffer.append(""+Double.parseDouble(varyingParameterValues[j]));
						buffer.append(",");
						buffer.append(""+memoryResults[m][j]);
						buffer.append(")");
					}
				}
				buffer.append("};\\addlegendentry{" + algorithmName+"}");
			}
			buffer.append("\\end{axis}");
			buffer.append(System.lineSeparator());
			buffer.append("\\end{tikzpicture}");
			buffer.append(System.lineSeparator());
			buffer.append(System.lineSeparator());
			// =======  END OF FIGURE FOR MEMORY
			
			if(compareOutputSize) {
				// ======= FIGURE FOR OUTPUT SIZES
				buffer.append("\\begin{tikzpicture}");
				buffer.append(System.lineSeparator());
				buffer.append("\\begin{axis}[");  // xmin=0,ymin=0,
				buffer.append(System.lineSeparator());
				//  ====== TIME =========
				buffer.append("xlabel=$" + variedParameterName + "$,");
				buffer.append("ylabel=Output size,cycle list name=color]");
				buffer.append(System.lineSeparator());
				// ==== FOR EACH ALGORITHM
				for (int m = 0; m < algorithmNames.length; m++) {
					String algorithmName = algorithmNames[m].replace('_', '-');

					buffer.append("\\addplot plot coordinates {");

					// ========= FOR EACH PARAMETER VALUE OF THE VARIED PARAMETER ==================
					for (int j = 0; j < varyingParameterValues.length; j++) {
						if(runtimes[m][j]>=0) {
							buffer.append("(");
							buffer.append(""+Double.parseDouble(varyingParameterValues[j]));
							buffer.append(",");
							buffer.append(""+outputSizes[m][j]);
							buffer.append(")");
						}
					}
					buffer.append("};\\addlegendentry{" + algorithmName+"}");
				}
				buffer.append("\\end{axis}");
				buffer.append(System.lineSeparator());
				buffer.append("\\end{tikzpicture}");
				buffer.append(System.lineSeparator());
				buffer.append(System.lineSeparator());
				// =======  END OF FIGURE FOR MEMORY
			}
			
			
			buffer.append("\\end{document}");
			buffer.append(System.lineSeparator());
			
			String latexpath = outputDirectory + File.separatorChar + "PGPLOT_FIGURES.tex";
			writer = new BufferedWriter(new FileWriter(latexpath));
			writer.write(buffer.toString());
			writer.close();
		}

		
	}

	/**
	 * This method takes as input a number that could be a double value containing
	 * the "." as separator for the decimal. If it is the case, we will convert the
	 * double value to a localized string where "." is replaced by the proper
	 * separator (e.g. ",")
	 * 
	 * @param parameterValue the number as a string
	 * @return the new string
	 */
	private String convertDoubleStringToLocalizeString(String parameterValue) {
		// If it is a decimal value, we try to change it to the correct locale format
		if (parameterValue.contains(".")) {
			try {
				double doubleValue = Double.parseDouble(parameterValue);
				parameterValue = formatAllDecimals.format(doubleValue);
			} catch (Exception e) {
				// It is not a double value so we do nothing
			}
		}
		return parameterValue;
	}

	/**
	 * Calculate the size of an output file (the number of non empty lines that are
	 * not comments or metadata)
	 * 
	 * @param file and output file path
	 * @return the number of lines or -1 if the file cannot be read (does not exist
	 *         or other reasons)
	 */
	private int calculateSizeOfFile(String file) {
		int size = 0;
		// scan the database to load it into memory and count the support of each single
		// item at the same time
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(file));

			String line;
			// for each line (transactions) until the end of the file
			while (((line = reader.readLine()) != null)) {
				// if the line is a comment, is empty or is metadata
				if (line.isEmpty() == true || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}
				if (line.length() > 0) {
					size++;
				}
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
		return size;
	}

	/**
	 * Set the path to the jar file of SPMF (SPMF.jar) for running the experiments
	 * 
	 * @param path the path
	 */
	public void setSPMFJarFilePath(String path) {
		this.spmfJarPath = path;
	}

	/**
	 * Get the path of a file for redirecting the console output of algorithms
	 * 
	 * @return the path
	 */
	public String getRedirectOutputPath() {
		return logFilePath;
	}

	/**
	 * Set a path of a file for redirecting the console output of algorithms
	 * 
	 * @param redirectOutputPath the path
	 */
	public void setRedirectOutputPath(String redirectOutputPath) {
		this.logFilePath = redirectOutputPath;
	}

	/**
	 * Get the string that represents a timeout
	 * 
	 * @return the string
	 */
	public String getTimeoutCode() {
		return timeoutCodeString;
	}

	/**
	 * Set a string that will indicate that a timeout has occurred
	 * 
	 * @param timeoutCodeString the result
	 */
	public void setTimeoutCodeS(String timeoutCodeString) {
		this.timeoutCodeString = timeoutCodeString;
	}

}
