package ca.pfv.spmf.algorithmmanager;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/* This file is copyright (c) 2008-2013 Philippe Fournier-Viger
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
 * This class is used to load the list of all available algorithms available in
 * SPMF.
 * 
 * @see DescriptionOfAlgorithm
 * @author Philippe Fournier-Viger 2016
 */

public class AlgorithmManager {

	/** List of algorithms available in SPMF */
	List<DescriptionOfAlgorithm> algorithms;

	/** the only instance of this class (singleton) **/
	static AlgorithmManager instance = null;

	/**
	 * Default Constructor
	 * 
	 * @throws Exception
	 */
	private AlgorithmManager() throws Exception {
		// Load all algorithms in the package "ca.pfv.spmf.algorithms.description."
		algorithms = getDescriptionOfAlgorithmsInPackage("ca.pfv.spmf.algorithmmanager.descriptions.");

		// Then we should load algorithms from Jar files in the same folder if required
		// ....

		// Then, we will sort the list of algorithms by categories.
		Collections.sort(algorithms, new Comparator<DescriptionOfAlgorithm>() {
			@Override
			public int compare(DescriptionOfAlgorithm description1, DescriptionOfAlgorithm description2) {
				// if different category, we sort by categories,
				if (description1.getAlgorithmCategory().equals(description2.getAlgorithmCategory()) == false) {
					// -----------------------------------------------------
					// The following lines make sure that the category of algorithm "DATASET TOOLS"
					// is always the last one in the list.

					// -----------------------------------------------------
					return description1.getAlgorithmCategory().compareTo(description2.getAlgorithmCategory());
				}
				// otherwise we sort by name
				return description1.getName().compareTo(description2.getName());
			}
		});
	}

	//

	/**
	 * Obtain the only instance of this class (singleton design pattern)
	 * 
	 * @return An instance of AlgorithManager
	 * @throws Exception if error occurs while initializing the instance
	 */
	public static AlgorithmManager getInstance() throws Exception {
		// if the instance is not created yet
		if (instance == null) {
			// we create it
			instance = new AlgorithmManager();
		}
		return instance;
	}

	/**
	 * Get the list of algorithms as String as displayed by the user interface of
	 * SPMF. The name of the first category appears, followed by the list of
	 * algorithms in the first category. Then, it is followed by the second category
	 * and so on...
	 * 
	 * @param includeTools       include tools in the list
	 * @param includeAlgorithms  include algorithms in the list
	 * @param includeExperiments include algorithms for running experiments
	 * @return the list of algorithms as String
	 */
	public List<String> getListOfAlgorithmsAsString(boolean showViewAndTransform, boolean showGenerateData, boolean includeTools, boolean includeAlgorithms,
			boolean includeExperiments) {
		// Create the list of String objects
		List<String> listOfNames = new ArrayList<String>();

		// Variable to remember the last category that was seen
		String previousCategory = null;

		// for each algorithm
		for (DescriptionOfAlgorithm algorithm : algorithms) {
			// if this algorithm is of type TOOLS
			if (AlgorithmType.DATA_PROCESSOR.equals(algorithm.getAlgorithmType())
					|| AlgorithmType.DATA_STATS_CALCULATOR.equals(algorithm.getAlgorithmType())
					|| AlgorithmType.DATA_VIEWER.equals(algorithm.getAlgorithmType())) {
				// if we don't want to include the tools, we skip it
				if (showViewAndTransform == false) {
					continue;
				}
			}if (AlgorithmType.DATA_GENERATOR.equals(algorithm.getAlgorithmType())) {
				// if we don't want to include the tools, we skip it
				if (showGenerateData == false) {
					continue;
				}
			} if (AlgorithmType.OTHER_TOOL.equals(algorithm.getAlgorithmType())
					|| AlgorithmType.OTHER_GUI_TOOL.equals(algorithm.getAlgorithmType())) {
				// if we don't want to include the tools, we skip it
				if (includeTools == false) {
					continue;
				}
			} else if (AlgorithmType.EXPERIMENT_TOOL.equals(algorithm.getAlgorithmType())) {
				// if we don't want to include the tools to run experiments, we skip it
				if (includeExperiments == false) {
					continue;
				}
			} else if (AlgorithmType.DATA_MINING.equals(algorithm.getAlgorithmType())) {
				// if we don't want to include this type of algorithm, we skip it
				if (includeAlgorithms == false) {
					continue;
				}
			}

			// if this algorithm belong to a new category, we will add the category name to
			// the list of algorithms
			if (algorithm.getAlgorithmCategory().equals(previousCategory) == false) {
				listOfNames.add(" --- " + algorithm.getAlgorithmCategory() + " --- ");
				// remember the category
				previousCategory = algorithm.getAlgorithmCategory();
			}
			// Then add the algorithm name
			listOfNames.add(algorithm.getName());

		}
		// Return the list
		return listOfNames;
	}

	/**
	 * Get the description of all algorithms in a given package name, from a jar or
	 * not Code was inspired from Stack Overflow: http://stackoverflow
	 * .com/questions/1456930/how-do-i-read-all-classes-from-
	 * a-java-package-in-the-classpath
	 * 
	 * @param packageName the package name
	 * @return A list of DescriptionOfAlgorithm objects, each describing an
	 *         algorithm
	 * @throws Exception if an error occurs while looking for the descriptions of
	 *                   algorithms
	 */
	private static List<DescriptionOfAlgorithm> getDescriptionOfAlgorithmsInPackage(String packageName)
			throws Exception {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

		ArrayList<DescriptionOfAlgorithm> classes = new ArrayList<DescriptionOfAlgorithm>();

		String originalPackageName = packageName;
		packageName = packageName.replace(".", "/");
		URL packageURL = classLoader.getResource(packageName);

		if (packageURL.getProtocol().equals("jar")) {
			String jarFileName;
			JarFile jf;
			Enumeration<JarEntry> jarEntries;
			String entryName;

			// build jar file name, then loop through zipped entries
			jarFileName = URLDecoder.decode(packageURL.getFile(), "UTF-8");
			jarFileName = jarFileName.substring(5, jarFileName.indexOf('!'));
			System.out.println(">" + jarFileName);
			jf = new JarFile(jarFileName);
			jarEntries = jf.entries();
			while (jarEntries.hasMoreElements()) {
				entryName = jarEntries.nextElement().getName();
				if (entryName.startsWith(packageName) && entryName.endsWith(".class")) {
					entryName = entryName.substring(packageName.length(), entryName.lastIndexOf('.'));
					// Get the class
//					System.out.println("ENTRY NAME : " + entryName);
//					System.out.println("PACKAGE NAME : " + packageName);
//					System.out.println("PACKAGE URL : " + packageURL);
//					System.out.println("ORIGINAL PACKAGE NAME : " + originalPackageName);
					Class<?> theClass = Class.forName(originalPackageName + entryName);
					if (theClass.getSuperclass() == DescriptionOfAlgorithm.class) {
						DescriptionOfAlgorithm instance = (DescriptionOfAlgorithm) theClass.getDeclaredConstructor()
								.newInstance();

						classes.add(instance);
					}
				}
			}
			jf.close();

			// loop through files in classpath
		} else {
			URI uri = new URI(packageURL.toString());
			File folder = new File(uri.getPath());
			// won't work with path which contains blank (%20)
			// File folder = new File(packageURL.getFile());
			File[] contenuti = folder.listFiles();
			String entryName;
			for (File actual : contenuti) {
				entryName = actual.getName();
				if (entryName.endsWith(".class")) {
					entryName = entryName.substring(0, entryName.lastIndexOf('.'));
					// Get the class
					@SuppressWarnings("rawtypes")
					Class theClass = Class.forName(originalPackageName + entryName);
					if (theClass.getSuperclass().equals(DescriptionOfAlgorithm.class)) {
						@SuppressWarnings("unchecked")
						DescriptionOfAlgorithm instance = (DescriptionOfAlgorithm) theClass.getDeclaredConstructor()
								.newInstance();

						classes.add(instance);
					}
				}
			}
		}
		return classes;
	}

	/**
	 * Get the description of a specific algorithm
	 * 
	 * @param algorithm the name of the algorithm
	 * @return the description of the algorithm (a DescriptionOfAlgorithm object),
	 *         or null if not found
	 */
	public DescriptionOfAlgorithm getDescriptionOfAlgorithm(String nameOfAlgorithm) {
		for (DescriptionOfAlgorithm algorithm : algorithms) {
			// if this algorithm belong to a new category, we will add the category name to
			// the list of algorithms
			if (algorithm.getName().equals(nameOfAlgorithm)) {
				return algorithm;
			}
		}
		return null;
	}

	/**
	 * Get the data viewer algorithm that is suitable for some input file types.
	 * 
	 * @param inputtypes the input types
	 * @return the description of the data viewer algorithm or null if none is found
	 */
	public DescriptionOfAlgorithm getViewerFor(String[] inputtypes) {
		if (inputtypes == null || inputtypes.length == 0) {
			return null;
		}

		String typeToSearchFor = inputtypes[inputtypes.length - 1];
		for (DescriptionOfAlgorithm algorithm : algorithms) {
			// if this algorithm belong to a new category, we will add the category name to
			// the list of algorithms
			if (AlgorithmType.DATA_VIEWER.equals(algorithm.getAlgorithmType())) {
				String[] algoTypes = algorithm.getInputFileTypes();
				String typeOfAlgorithm = algoTypes[algoTypes.length - 1];
				if (typeOfAlgorithm.equals(typeToSearchFor)) {
					return algorithm;
				}
			}
		}
		return null;
	}

}
