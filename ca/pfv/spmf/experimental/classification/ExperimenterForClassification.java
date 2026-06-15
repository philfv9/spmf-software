package ca.pfv.spmf.experimental.classification;

/*
 * This file is copyright (c) 2024 Philippe Fournier-Viger
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

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import ca.pfv.spmf.algorithms.classifiers.data.StringDataset;
import ca.pfv.spmf.algorithms.classifiers.general.ClassificationAlgorithm;
import ca.pfv.spmf.algorithms.classifiers.general.Evaluator;
import ca.pfv.spmf.algorithms.classifiers.general.OverallResults;

/**
 * Class that allows to run classification experiments by comparing
 * multiple classification algorithms using either k-fold cross validation
 * or holdout validation.
 *
 * @author Philippe Fournier-Viger, 2024
 */
public class ExperimenterForClassification {

    /** The root package where all classifiers are located */
    private static final String CLASSIFIERS_PACKAGE = "ca.pfv.spmf.algorithms.classifiers";

    /**
     * Object to properly display double numbers with two decimals
     */
    DecimalFormat formatTwoDecimals;

    /**
     * Constructor
     */
    public ExperimenterForClassification() {
        // Setup the object to format double numbers with two decimals
        formatTwoDecimals = (DecimalFormat) NumberFormat.getNumberInstance();
        formatTwoDecimals.applyPattern("#.##");
    }

    /**
     * Run classification experiments using holdout validation
     *
     * @param algorithmNames  names of classification algorithms to compare
     * @param datasetPath     path to the dataset file
     * @param targetClassName name of the target class attribute
     * @param percentage      percentage of data for training (between 0 and 1)
     * @param outputDirectory directory where results will be saved
     * @throws Exception if some error occurs
     */
    public void runHoldoutExperiment(String[] algorithmNames, String datasetPath,
            String targetClassName, double percentage, String outputDirectory) throws Exception {

        // Create output directory if it does not exist
        File directory = new File(outputDirectory);
        directory.mkdir();

        System.out.println("********************************************");
        System.out.println("*****  RUNNING CLASSIFICATION EXPERIMENTS (HOLDOUT) *****");
        System.out.println("********************************************");
        System.out.println(" DATASET: " + datasetPath);
        System.out.println(" TARGET CLASS: " + targetClassName);
        System.out.println(" TRAINING PERCENTAGE: " + percentage);
        System.out.println(" OUTPUT DIRECTORY: " + outputDirectory);
        System.out.println();

        // Load the dataset
        StringDataset dataset = new StringDataset(datasetPath, targetClassName);
        dataset.printStats();

        // Create algorithm instances using reflection
        ClassificationAlgorithm[] algorithms = instantiateAlgorithms(algorithmNames);

        // Create evaluator and run experiment
        Evaluator evaluator = new Evaluator();
        OverallResults allResults = evaluator.trainAndRunClassifiersHoldout(algorithms, dataset, percentage);

        // Save results
        String forTrainingPath = outputDirectory + File.separatorChar + "report_for_training.txt";
        String onTrainingPath = outputDirectory + File.separatorChar + "report_on_training.txt";
        String onTestingPath = outputDirectory + File.separatorChar + "report_on_testing.txt";
        allResults.saveMetricsResultsToFile(forTrainingPath, onTrainingPath, onTestingPath);

        // Print results to console
        System.out.println();
        System.out.println("********************************************");
        System.out.println("*****             RESULTS              *****");
        System.out.println("********************************************");
        allResults.printStats();

        System.out.println();
        System.out.println(" Results saved to: " + outputDirectory);
    }

    /**
     * Run classification experiments using k-fold cross validation
     *
     * @param algorithmNames  names of classification algorithms to compare
     * @param datasetPath     path to the dataset file
     * @param targetClassName name of the target class attribute
     * @param kFoldCount      the number of folds for cross validation
     * @param outputDirectory directory where results will be saved
     * @throws Exception if some error occurs
     */
    public void runKFoldExperiment(String[] algorithmNames, String datasetPath,
            String targetClassName, int kFoldCount, String outputDirectory) throws Exception {

        // Create output directory if it does not exist
        File directory = new File(outputDirectory);
        directory.mkdir();

        System.out.println("********************************************");
        System.out.println("*****  RUNNING CLASSIFICATION EXPERIMENTS (K-FOLD) *****");
        System.out.println("********************************************");
        System.out.println(" DATASET: " + datasetPath);
        System.out.println(" TARGET CLASS: " + targetClassName);
        System.out.println(" K (NUMBER OF FOLDS): " + kFoldCount);
        System.out.println(" OUTPUT DIRECTORY: " + outputDirectory);
        System.out.println();

        // Load the dataset
        StringDataset dataset = new StringDataset(datasetPath, targetClassName);
        dataset.printStats();

        // Create algorithm instances using reflection
        ClassificationAlgorithm[] algorithms = instantiateAlgorithms(algorithmNames);

        // Create evaluator and run experiment
        Evaluator evaluator = new Evaluator();
        OverallResults allResults = evaluator.trainAndRunClassifiersKFold(algorithms, dataset, kFoldCount);

        // Save results
        String forTrainingPath = outputDirectory + File.separatorChar + "report_for_training.txt";
        String onTrainingPath = outputDirectory + File.separatorChar + "report_on_training.txt";
        String onTestingPath = outputDirectory + File.separatorChar + "report_on_testing.txt";
        allResults.saveMetricsResultsToFile(forTrainingPath, onTrainingPath, onTestingPath);

        // Print results to console
        System.out.println();
        System.out.println("********************************************");
        System.out.println("*****             RESULTS              *****");
        System.out.println("********************************************");
        allResults.printStats();

        System.out.println();
        System.out.println(" Results saved to: " + outputDirectory);
    }

    /**
     * Instantiate classification algorithm objects from their simple class names
     * using reflection. This method searches all subpackages of
     * ca.pfv.spmf.algorithms.classifiers to find the class and tries to create
     * instances using the default (no-argument) constructor.
     *
     * @param algorithmNames the simple class names (e.g. "AlgoACAC") or
     *                       fully-qualified class names of the algorithms
     * @return an array of ClassificationAlgorithm instances
     * @throws Exception if instantiation fails
     */
    private ClassificationAlgorithm[] instantiateAlgorithms(String[] algorithmNames) throws Exception {
        ClassificationAlgorithm[] algorithms = new ClassificationAlgorithm[algorithmNames.length];
        for (int i = 0; i < algorithmNames.length; i++) {
            try {
                // Try to load the class directly (works if fully-qualified name is given)
                Class<?> clazz = Class.forName(algorithmNames[i]);
                algorithms[i] = (ClassificationAlgorithm) clazz.getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException e) {
                throw new Exception("Algorithm class not found: " + algorithmNames[i]
                        + ". Please use the fully-qualified class name.");
            } catch (NoSuchMethodException e) {
                throw new Exception("Algorithm " + algorithmNames[i]
                        + " does not have a no-argument constructor.");
            } catch (ClassCastException e) {
                throw new Exception("Class " + algorithmNames[i]
                        + " is not a ClassificationAlgorithm.");
            }
        }
        return algorithms;
    }

    /**
     * Get a list of all available classification algorithm class names by scanning
     * the ca.pfv.spmf.algorithms.classifiers package and all its sub-packages
     * using standard Java reflection and classpath scanning.
     *
     * @return a list of fully-qualified class names of concrete
     *         ClassificationAlgorithm subclasses
     */
    public static List<String> getAvailableClassificationAlgorithms() {
        List<String> result = new ArrayList<String>();
        try {
            // Get all classes in the classifiers package and sub-packages
            List<Class<?>> classes = getClassesInPackage(CLASSIFIERS_PACKAGE);
            for (Class<?> clazz : classes) {
                // Only include concrete (non-abstract) subclasses of ClassificationAlgorithm
                if (!Modifier.isAbstract(clazz.getModifiers())
                        && !Modifier.isInterface(clazz.getModifiers())
                        && ClassificationAlgorithm.class.isAssignableFrom(clazz)) {
                    result.add(clazz.getName());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Scan the given package name and all its sub-packages recursively,
     * returning all classes found. Works both when running from a directory
     * (IDE) and from a JAR file.
     *
     * @param packageName the root package to scan (e.g.
     *                    "ca.pfv.spmf.algorithms.classifiers")
     * @return a list of Class objects found in the package tree
     * @throws Exception if scanning fails
     */
    private static List<Class<?>> getClassesInPackage(String packageName) throws Exception {
        List<Class<?>> classes = new ArrayList<Class<?>>();

        // Convert the package name to a path
        String packagePath = packageName.replace('.', '/');

        // Get the class loader
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        // Get all resources matching the package path
        Enumeration<URL> resources = classLoader.getResources(packagePath);

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            String protocol = resource.getProtocol();

            if ("file".equals(protocol)) {
                // Running from a directory (e.g. in an IDE)
                File directory = new File(java.net.URLDecoder.decode(resource.getPath(), "UTF-8"));
                findClassesInDirectory(directory, packageName, classes);

            } else if ("jar".equals(protocol)) {
                // Running from a JAR file
                // The URL looks like: jar:file:/path/to/spmf.jar!/ca/pfv/spmf/...
                String jarPath = resource.getPath();
                // Extract the actual jar file path (before the "!")
                String jarFilePath = jarPath.substring("file:".length(), jarPath.indexOf('!'));
                jarFilePath = java.net.URLDecoder.decode(jarFilePath, "UTF-8");
                findClassesInJar(jarFilePath, packagePath, packageName, classes);
            }
        }

        return classes;
    }

    /**
     * Recursively find all classes in a directory and its sub-directories.
     *
     * @param directory   the directory to scan
     * @param packageName the package name corresponding to this directory
     * @param classes     the list to which found Class objects are added
     */
    private static void findClassesInDirectory(File directory, String packageName,
            List<Class<?>> classes) {
        if (!directory.exists()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // Recurse into sub-directory
                findClassesInDirectory(file, packageName + "." + file.getName(), classes);
            } else if (file.getName().endsWith(".class")) {
                // Strip the ".class" suffix to get the simple class name
                String className = packageName + "."
                        + file.getName().substring(0, file.getName().length() - 6);
                tryLoadClass(className, classes);
            }
        }
    }

    /**
     * Find all classes inside a JAR file that belong to the given package
     * or any of its sub-packages.
     *
     * @param jarFilePath the path to the JAR file on disk
     * @param packagePath the package path (with '/' separators) to look for
     * @param packageName the package name (with '.' separators)
     * @param classes     the list to which found Class objects are added
     */
    private static void findClassesInJar(String jarFilePath, String packagePath,
            String packageName, List<Class<?>> classes) {
        try (JarFile jarFile = new JarFile(jarFilePath)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // Only process .class files that are inside the target package or sub-packages
                if (entryName.startsWith(packagePath) && entryName.endsWith(".class")
                        && !entry.isDirectory()) {
                    // Convert path to fully-qualified class name
                    // e.g. "ca/pfv/spmf/algorithms/classifiers/acac/AlgoACAC.class"
                    //   -> "ca.pfv.spmf.algorithms.classifiers.acac.AlgoACAC"
                    String className = entryName.replace('/', '.').replace('\\', '.');
                    // Remove the ".class" suffix
                    className = className.substring(0, className.length() - 6);
                    tryLoadClass(className, classes);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Try to load a class by its fully-qualified name and add it to the list.
     * Silently skips classes that cannot be loaded (e.g. inner anonymous classes).
     *
     * @param className the fully-qualified class name
     * @param classes   the list to which the loaded Class is added if successful
     */
    private static void tryLoadClass(String className, List<Class<?>> classes) {
        // Skip inner/anonymous classes indicated by '$'
        if (className.contains("$")) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(className, false,
                    Thread.currentThread().getContextClassLoader());
            classes.add(clazz);
        } catch (Throwable e) {
            // Silently skip classes that cannot be loaded
        }
    }
}