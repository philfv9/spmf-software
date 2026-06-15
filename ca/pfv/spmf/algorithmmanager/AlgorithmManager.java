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
 * 
 * Do not remove copyright and license information from this file.
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
        algorithms = getDescriptionOfAlgorithmsInPackage(
                "ca.pfv.spmf.algorithmmanager.descriptions.");

        // Sort the list of algorithms by categories.
        Collections.sort(algorithms, new Comparator<DescriptionOfAlgorithm>() {
            @Override
            public int compare(DescriptionOfAlgorithm description1,
                    DescriptionOfAlgorithm description2) {
                if (!description1.getAlgorithmCategory()
                        .equals(description2.getAlgorithmCategory())) {
                    return description1.getAlgorithmCategory()
                            .compareTo(description2.getAlgorithmCategory());
                }
                return description1.getName().compareTo(description2.getName());
            }
        });
    }

    /**
     * Obtain the only instance of this class (singleton design pattern).
     *
     * @return an instance of AlgorithmManager
     * @throws Exception if an error occurs while initializing the instance
     */
    public static AlgorithmManager getInstance() throws Exception {
        if (instance == null) {
            instance = new AlgorithmManager();
        }
        return instance;
    }

    // =========================================================================
    // Algorithm-type predicates
    // =========================================================================

    /**
     * Returns {@code true} if {@code algorithm} is a view-or-transform algorithm
     * (i.e. a data processor, stats calculator, or data viewer).
     *
     * @param algorithm the algorithm to test
     * @return {@code true} if it belongs to the view-and-transform category
     */
    public boolean isViewOrTransformAlgorithm(DescriptionOfAlgorithm algorithm) {
        AlgorithmType type = algorithm.getAlgorithmType();
        return AlgorithmType.DATA_PROCESSOR.equals(type)
                || AlgorithmType.DATA_STATS_CALCULATOR.equals(type)
                || AlgorithmType.DATA_VIEWER.equals(type);
    }

    /**
     * Returns {@code true} if {@code algorithm} is a data-generator algorithm.
     *
     * @param algorithm the algorithm to test
     * @return {@code true} if it belongs to the data-generation category
     */
    public boolean isDataGeneratorAlgorithm(DescriptionOfAlgorithm algorithm) {
        return AlgorithmType.DATA_GENERATOR.equals(algorithm.getAlgorithmType());
    }

    /**
     * Returns {@code true} if {@code algorithm} is a tool algorithm
     * (i.e. a general tool or a GUI tool, but not an experiment tool).
     *
     * @param algorithm the algorithm to test
     * @return {@code true} if it belongs to the tools category
     */
    public boolean isToolAlgorithm(DescriptionOfAlgorithm algorithm) {
        AlgorithmType type = algorithm.getAlgorithmType();
        return AlgorithmType.OTHER_TOOL.equals(type)
                || AlgorithmType.OTHER_GUI_TOOL.equals(type);
    }

    /**
     * Returns {@code true} if {@code algorithm} is an experiment-tool algorithm.
     *
     * @param algorithm the algorithm to test
     * @return {@code true} if it belongs to the experiment-tools category
     */
    public boolean isExperimentAlgorithm(DescriptionOfAlgorithm algorithm) {
        return AlgorithmType.EXPERIMENT_TOOL.equals(algorithm.getAlgorithmType());
    }

    // =========================================================================
    // Algorithm listing
    // =========================================================================

    /**
     * Get the list of algorithms as Strings as displayed by the SPMF UI.
     * The name of the first category appears, followed by the algorithms in that
     * category, then the second category, and so on.
     *
     * @param showViewAndTransform include view-and-transform algorithms
     * @param showGenerateData     include data-generation algorithms
     * @param includeTools         include tool algorithms
     * @param includeAlgorithms    include data-mining algorithms
     * @param includeExperiments   include experiment-tool algorithms
     * @return the list of algorithm names (and category headers) as Strings
     */
    public List<String> getListOfAlgorithmsAsString(boolean showViewAndTransform,
            boolean showGenerateData, boolean includeTools,
            boolean includeAlgorithms, boolean includeExperiments) {

        List<String> listOfNames = new ArrayList<String>();
        String previousCategory = null;

        for (DescriptionOfAlgorithm algorithm : algorithms) {

            if (isViewOrTransformAlgorithm(algorithm) && !showViewAndTransform) {
                continue;
            }
            if (isDataGeneratorAlgorithm(algorithm) && !showGenerateData) {
                continue;
            }
            if (isToolAlgorithm(algorithm) && !includeTools) {
                continue;
            }
            if (isExperimentAlgorithm(algorithm) && !includeExperiments) {
                continue;
            }
            if (AlgorithmType.DATA_MINING.equals(algorithm.getAlgorithmType())
                    && !includeAlgorithms) {
                continue;
            }

            // Add the category header if we have entered a new category
            if (!algorithm.getAlgorithmCategory().equals(previousCategory)) {
                listOfNames.add(" --- " + algorithm.getAlgorithmCategory() + " --- ");
                previousCategory = algorithm.getAlgorithmCategory();
            }

            listOfNames.add(algorithm.getName());
        }

        return listOfNames;
    }

    // =========================================================================
    // Internal loading
    // =========================================================================

    /**
     * Get the description of all algorithms in a given package, from a jar or from
     * the file system. Code inspired from Stack Overflow:
     * http://stackoverflow.com/questions/1456930/
     *
     * @param packageName the package name
     * @return a list of {@link DescriptionOfAlgorithm} objects
     * @throws Exception if an error occurs while scanning for descriptions
     */
    private static List<DescriptionOfAlgorithm> getDescriptionOfAlgorithmsInPackage(
            String packageName) throws Exception {

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

            jarFileName = URLDecoder.decode(packageURL.getFile(), "UTF-8");
            jarFileName = jarFileName.substring(5, jarFileName.indexOf('!'));
            System.out.println(">" + jarFileName);
            jf = new JarFile(jarFileName);
            jarEntries = jf.entries();
            while (jarEntries.hasMoreElements()) {
                entryName = jarEntries.nextElement().getName();
                if (entryName.startsWith(packageName) && entryName.endsWith(".class")) {
                    entryName = entryName.substring(packageName.length(),
                            entryName.lastIndexOf('.'));
                    Class<?> theClass = Class.forName(originalPackageName + entryName);
                    if (theClass.getSuperclass() == DescriptionOfAlgorithm.class) {
                        DescriptionOfAlgorithm instance =
                                (DescriptionOfAlgorithm) theClass
                                        .getDeclaredConstructor().newInstance();
                        classes.add(instance);
                    }
                }
            }
            jf.close();

        } else {
            URI uri = new URI(packageURL.toString());
            File folder = new File(uri.getPath());
            File[] contenuti = folder.listFiles();
            String entryName;
            for (File actual : contenuti) {
                entryName = actual.getName();
                if (entryName.endsWith(".class")) {
                    entryName = entryName.substring(0, entryName.lastIndexOf('.'));
                    @SuppressWarnings("rawtypes")
                    Class theClass = Class.forName(originalPackageName + entryName);
                    if (theClass.getSuperclass().equals(DescriptionOfAlgorithm.class)) {
                        @SuppressWarnings("unchecked")
                        DescriptionOfAlgorithm instance =
                                (DescriptionOfAlgorithm) theClass
                                        .getDeclaredConstructor().newInstance();
                        classes.add(instance);
                    }
                }
            }
        }

        return classes;
    }

    // =========================================================================
    // Algorithm lookup
    // =========================================================================

    /**
     * Get the description of a specific algorithm by name.
     *
     * @param nameOfAlgorithm the name of the algorithm
     * @return the {@link DescriptionOfAlgorithm}, or {@code null} if not found
     */
    public DescriptionOfAlgorithm getDescriptionOfAlgorithm(String nameOfAlgorithm) {
        for (DescriptionOfAlgorithm algorithm : algorithms) {
            if (algorithm.getName().equals(nameOfAlgorithm)) {
                return algorithm;
            }
        }
        return null;
    }

    /**
     * Get the data-viewer algorithm suitable for the given input file types.
     *
     * @param inputtypes the input file types of the algorithm whose input is to be viewed
     * @return the matching {@link DescriptionOfAlgorithm}, or {@code null} if none found
     */
    public DescriptionOfAlgorithm getViewerFor(String[] inputtypes) {
        if (inputtypes == null || inputtypes.length == 0) {
            return null;
        }

        String typeToSearchFor = inputtypes[inputtypes.length - 1];
        for (DescriptionOfAlgorithm algorithm : algorithms) {
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