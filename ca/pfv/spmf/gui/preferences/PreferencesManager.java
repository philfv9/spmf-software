package ca.pfv.spmf.gui.preferences;

import java.nio.charset.Charset;
/*
 * Copyright (c) 2008-2013 Philippe Fournier-Viger
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
 */
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import ca.pfv.spmf.gui.MainWindow;
/*
 * Copyright (c) 2008-2022 Philippe Fournier-Viger
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
 */
/**
 * This class is used to manage registry keys for storing user preferences for
 * the SPMF GUI.
 *
 * @see MainWindow
 * @author Philippe Fournier-Viger
 */
public class PreferencesManager {

    // We use two registry key to store
    // the paths of the last folders used by the user
    // for input and output files.
    public static final String InputFilePath = "ca.pfv.spmf.gui.input";
    public static final String OutputFilePath = "ca.pfv.spmf.gui.output";
//    public static final String REGKEY_SPMF_PLUGIN_FOLDER_PATH = "ca.pfv.spmf.plugin.folderpath";
    public static final String RepositoryURL = "ca.pfv.spmf.plugin.repositoryurl";
    public static final String PreferedCharset = "ca.pfv.spmf.gui.charset";
    public static final String RunAsExternalProgram = "ca.pfv.spmf.gui.runexternal";
//    public static final String REGKEY_SPMF_MAX_SECONDS = "ca.pfv.spmf.gui.maxseconds";;
    public static final String SPMFJarFilePath = "ca.pfv.spmf.jar_file_path";
    public static final String ExperimentDirectoryPath = "ca.pfv.spmf.experiment_directory_path";
    public static final String LastMemoryUsage = "ca.pfv.spmf.experiments.lastmemory";
    public static final String NightMode = "ca.pfv.spmf.experiments.nightmode";
    public static final String TextEditorFontSize = "ca.pfv.spmf.gui.texteditor.fontsize";
    public static final String TextEditorLineWrap = "ca.pfv.spmf.gui.texteditor.linewrap";
    public static final String TextEditorWordWrap = "ca.pfv.spmf.gui.texteditor.wordwrap";
    public static final String FontFamilly = "ca.pfv.spmf.gui.texteditor.fontfamilly";
    public static final String TextEditorWidth = "ca.pfv.spmf.gui.texteditor.width";
    public static final String TextEditorHeight = "ca.pfv.spmf.gui.texteditor.height";
    public static final String TextEditorAreaWidth = "ca.pfv.spmf.gui.texteditor.areawidth";
    public static final String TextEditorAreaHeight = "ca.pfv.spmf.gui.texteditor.areaheight";
    public static final String TextEditorX = "ca.pfv.spmf.gui.texteditor.x";
    public static final String TextEditorY = "ca.pfv.spmf.gui.texteditor.y";
    public static final String ShouldUseSystemTextEditor = "ca.pfv.spmf.system_text_editor";
    public static final String ConsoleFontSize = "ca.pfv.spmf.gui.console.fontsize";

    /** Registry key prefix for recent input files (private — no public getter via reflection) */
    private static final String RECENT_INPUT_PREFIX = "ca.pfv.spmf.gui.recent.input.";

    /** Registry key prefix for recent output files (private — no public getter via reflection) */
    private static final String RECENT_OUTPUT_PREFIX = "ca.pfv.spmf.gui.recent.output.";

    /** Registry key prefix for recent algorithms (private — no public getter via reflection) */
    private static final String RECENT_ALGORITHM_PREFIX = "ca.pfv.spmf.gui.recent.algorithm.";

    /** Registry key prefix for recent workflow files (private — no public getter via reflection) */
    private static final String RECENT_WORKFLOW_PREFIX = "ca.pfv.spmf.gui.recent.workflow.";

    /** Maximum number of recent files or algorithms to remember */
    private static final int MAX_RECENT = 10;

    // Implemented as a singleton
    private static PreferencesManager instance;

    /**
     * Default constructor
     */
    private PreferencesManager() {
    }

    /**
     * Get the only instance of this class (a singleton)
     *
     * @return the instance
     */
    public static PreferencesManager getInstance() {
        if (instance == null) {
            instance = new PreferencesManager();
        }
        return instance;
    }

    /**
     * Get the input file path stored in the registry
     *
     * @return a path as a string
     */
    public String getInputFilePath() {
        Preferences p = Preferences.userRoot();
        return p.get(InputFilePath, null);
    }

    /**
     * Store an input file path in the registry
     *
     * @param filepath a path as a string
     */
    public void setInputFilePath(String filepath) {
        Preferences p = Preferences.userRoot();
        p.put(InputFilePath, filepath);
    }

    /**
     * Get the output file path stored in the registry
     *
     * @return a path as a string
     */
    public String getOutputFilePath() {
        Preferences p = Preferences.userRoot();
        return p.get(OutputFilePath, null);
    }

    /**
     * Store an output file path in the registry
     *
     * @param filepath a path as a string
     */
    public void setOutputFilePath(String filepath) {
        Preferences p = Preferences.userRoot();
        p.put(OutputFilePath, filepath);
    }

    /**
     * Get the experiment directory path stored in the registry
     *
     * @return a path as a string
     */
    public String getExperimentDirectoryPath() {
        Preferences p = Preferences.userRoot();
        return p.get(ExperimentDirectoryPath, null);
    }

    /**
     * Store an experiment directory path in the registry
     *
     * @param filepath a path as a string
     */
    public void setExperimentDirectoryPath(String filepath) {
        Preferences p = Preferences.userRoot();
        p.put(ExperimentDirectoryPath, filepath);
    }

    /**
     * Store the path to spmf.jar in the registry
     *
     * @param path the path
     */
    public void setSPMFJarFilePath(String path) {
        Preferences p = Preferences.userRoot();
        p.put(SPMFJarFilePath, path);
    }

    /**
     * Get the path to the spmf.jar file stored in a registry key
     *
     * @return the path as a string
     */
    public String getSPMFJarFilePath() {
        Preferences p = Preferences.userRoot();
        return p.get(SPMFJarFilePath, null);
    }

//    /**
//     * Get the output file path stored in the registry
//     * @return a path as a string
//     */
//    public String getPluginFolderFilePath() {
//        Preferences p = Preferences.userRoot();
//        return p.get(REGKEY_SPMF_PLUGIN_FOLDER_PATH, null);
//    }
//
//    /**
//     * Store an output file path in the registry
//     * @param filepath a path as a string
//     */
//    public void setPluginFolderFilePath(String filepath) {
//        Preferences p = Preferences.userRoot();
//        p.put(REGKEY_SPMF_PLUGIN_FOLDER_PATH, filepath);
//    }
//
//    /**
//     * Delete the plugin file path from the registry
//     */
//    public void deletePluginFolderFilePath() {
//        Preferences p = Preferences.userRoot();
//        p.remove(REGKEY_SPMF_PLUGIN_FOLDER_PATH);
//    }

    // ---
    /**
     * Store a repository URL in the registry
     *
     * @param filepath a repository URL as a string
     */
    public void setRepositoryURL(String filepath) {
        Preferences p = Preferences.userRoot();
        p.put(RepositoryURL, filepath);
    }

    /**
     * Get the repository URL path stored in the registry
     *
     * @return a path as a string
     */
    public String getRepositoryURL() {
        Preferences p = Preferences.userRoot();
        String url = p.get(RepositoryURL, null);
        return (url == null) ? "http://www.philippe-fournier-viger.com/spmf/plugins/" : url;
    }

    // ---

    /**
     * Get the prefered charset stored in the registry
     *
     * @return Charset the prefered charset
     */
    public Charset getPreferedCharset() {
        Preferences p = Preferences.userRoot();
        String charsetName = p.get(PreferedCharset, null);

        if (charsetName != null && charsetName.equals("false")) {
            return Charset.defaultCharset();
        }

        return (charsetName == null) ? Charset.defaultCharset() : Charset.forName(charsetName);
    }

    /**
     * Store the prefered charset in the registry
     *
     * @param charsetName the prefered charset
     */
    public void setPreferedCharset(String charsetName) {
        Preferences p = Preferences.userRoot();
        p.put(PreferedCharset, charsetName);
    }

    /**
     * Get the preference if algorithms should be run as an external program by
     * SPMF's GUI
     *
     * @return true or false
     */
    public boolean getRunAsExternalProgram() {
        Preferences p = Preferences.userRoot();
        String value = p.get(RunAsExternalProgram, null);
        return (value == null) ? false : Boolean.parseBoolean(value);
    }

    /**
     * Store the preference if algorithms should be run as an external program by
     * SPMF's GUI
     *
     * @param value true or false
     */
    public void setRunAsExternalProgram(boolean value) {
        Preferences p = Preferences.userRoot();
        p.put(RunAsExternalProgram, Boolean.toString(value));
    }

    /**
     * Get the memory usage of the last algorithm that was run (this is stored in a
     * registry key)
     *
     * @return the memory usage as a double
     */
    public double getLastMemoryUsage() {
        Preferences p = Preferences.userRoot();
        String value = p.get(LastMemoryUsage, null);
        if (value == null) {
            return 0;
        }
        return Double.parseDouble(value);
    }

    /**
     * Store the memory usage of the last execution in the registry
     *
     * @param lastMemoryUsage a number representing the memory usage in megabytes
     */
    public void setLastMemoryUsage(double lastMemoryUsage) {
        Preferences p = Preferences.userRoot();
        p.put(LastMemoryUsage, Double.toString(lastMemoryUsage));
    }

    /**
     * Get the preference about whether the night mode is on or off (this is stored
     * in a registry key)
     *
     * @return true if night mode is on, false otherwise
     */
    public boolean getNightMode() {
        Preferences p = Preferences.userRoot();
        String value = p.get(NightMode, null);
        return (value == null) ? false : Boolean.parseBoolean(value);
    }

    /**
     * Store the preference if night mode is activated or not
     *
     * @param value true or false
     */
    public void setNightMode(boolean value) {
        Preferences p = Preferences.userRoot();
        p.put(NightMode, Boolean.toString(value));
    }

    /**
     * Get the font size for the text editor (this is stored in a registry key)
     *
     * @return the font size
     */
    public int getTextEditorFontSize() {
        Preferences p = Preferences.userRoot();
        String value = p.get(TextEditorFontSize, null);
        if (value == null) {
            return 12;
        }
        return Integer.parseInt(value);
    }

    /**
     * Store the font size for the text editor
     *
     * @param fontsize a number
     */
    public void setTextEditorFontSize(int fontsize) {
        Preferences p = Preferences.userRoot();
        p.put(TextEditorFontSize, Integer.toString(fontsize));
    }

//    /**
//     * Store the preference about how many seconds an algorithm should run at most in the GUI
//     * @param number the number of seconds
//     */
//    public void setMaxSeconds(int number) {
//        Preferences p = Preferences.userRoot();
//        p.put(REGKEY_SPMF_MAX_SECONDS, Integer.toString(number));
//    }
//
//    /**
//     * Get the preference about how many seconds an algorithm should run at most in the GUI
//     * @return a string containing a number (integer)
//     */
//    public int getMaxSeconds() {
//        Preferences p = Preferences.userRoot();
//        String value = p.get(REGKEY_SPMF_MAX_SECONDS, null);
//        return (value == null) ? Integer.MAX_VALUE : Integer.parseInt(value);
//    }

    /**
     * Get the preference about line wrap is on or off for the text editor
     * (this is stored in a registry key)
     *
     * @return true if line wrap is on, false otherwise
     */
    public boolean getTextEditorLineWrap() {
        Preferences p = Preferences.userRoot();
        String value = p.get(TextEditorLineWrap, null);
        return (value == null) ? true : Boolean.parseBoolean(value);
    }

    /**
     * Store the preference if line wrap mode is activated or not
     *
     * @param value true or false
     */
    public void setTextEditorLineWrap(boolean value) {
        Preferences p = Preferences.userRoot();
        p.put(TextEditorLineWrap, Boolean.toString(value));
    }

    /**
     * Get the preference about word wrap is on or off for the text editor
     * (this is stored in a registry key)
     *
     * @return true if word wrap is on, false otherwise
     */
    public boolean getTextEditorWordWrap() {
        Preferences p = Preferences.userRoot();
        String value = p.get(TextEditorWordWrap, null);
        return (value == null) ? false : Boolean.parseBoolean(value);
    }

    /**
     * Store the preference if word wrap mode is activated or not
     *
     * @param value true or false
     */
    public void setTextEditorWordWrap(boolean value) {
        Preferences p = Preferences.userRoot();
        p.put(TextEditorWordWrap, Boolean.toString(value));
    }

    // ---
    /**
     * Store the text editor font family in the registry
     *
     * @param filepath a font family name
     */
    public void setFontFamilly(String filepath) {
        Preferences p = Preferences.userRoot();
        p.put(FontFamilly, filepath);
    }

    /**
     * Get the text editor font family stored in the registry
     *
     * @return a string (font family)
     */
    public String getFontFamilly() {
        Preferences p = Preferences.userRoot();
        String result = p.get(FontFamilly, null);
        return (result == null) ? "Dialog" : result;
    }

    /**
     * Get the width of the text editor (this is stored in a registry key)
     *
     * @return the width
     */
    public int getTextEditorWidth() {
        Preferences p = Preferences.userRoot();
        String value = p.get(TextEditorWidth, null);
        if (value == null) {
            return 800;
        }
        return Integer.parseInt(value);
    }

    /**
     * Store window width for the text editor
     *
     * @param width the width
     */
    public void setTextEditorWidth(int width) {
        Preferences p = Preferences.userRoot();
        p.put(TextEditorWidth, Integer.toString(width));
    }

    /**
     * Get the height of the text editor (this is stored in a registry key)
     *
     * @return the height
     */
    public int getTextEditorHeight() {
        Preferences p = Preferences.userRoot();
        String value = p.get(TextEditorHeight, null);
        if (value == null) {
            return 800;
        }
        return Integer.parseInt(value);
    }

    /**
     * Store window height for the text editor
     *
     * @param height the height
     */
    public void setTextEditorHeight(int height) {
        Preferences p = Preferences.userRoot();
        p.put(TextEditorHeight, Integer.toString(height));
    }

    /**
     * Get the text area width of the text editor (this is stored in a registry key)
     *
     * @return the text area width
     */
    public int getTextEditorAreaWidth() {
        Preferences p = Preferences.userRoot();
        String value = p.get(TextEditorAreaWidth, null);
        if (value == null) {
            return 800;
        }
        return Integer.parseInt(value);
    }

    /**
     * Store text area width for the text editor
     *
     * @param width the width
     */
    public void setTextEditorAreaWidth(int width) {
        Preferences p = Preferences.userRoot();
        p.put(TextEditorAreaWidth, Integer.toString(width));
    }

    /**
     * Get the text area height of the text editor (this is stored in a registry key)
     *
     * @return the height
     */
    public int getTextEditorAreaHeight() {
        Preferences p = Preferences.userRoot();
        String value = p.get(TextEditorAreaHeight, null);
        if (value == null) {
            return 800;
        }
        return Integer.parseInt(value);
    }

    /**
     * Store text area height for the text editor
     *
     * @param height the height
     */
    public void setTextEditorAreaHeight(int height) {
        Preferences p = Preferences.userRoot();
        p.put(TextEditorAreaHeight, Integer.toString(height));
    }

    /**
     * Get the X position of the text editor (this is stored in a registry key)
     *
     * @return the X position
     */
    public int getTextEditorX() {
        Preferences p = Preferences.userRoot();
        String value = p.get(TextEditorX, null);
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    /**
     * Store X position of the text editor
     *
     * @param position the position
     */
    public void setTextEditorX(int position) {
        Preferences p = Preferences.userRoot();
        p.put(TextEditorX, Integer.toString(position));
    }

    /**
     * Get the Y position of the text editor (this is stored in a registry key)
     *
     * @return the Y position
     */
    public int getTextEditorY() {
        Preferences p = Preferences.userRoot();
        String value = p.get(TextEditorY, null);
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    /**
     * Store Y position of the text editor
     *
     * @param position the position
     */
    public void setTextEditorY(int position) {
        Preferences p = Preferences.userRoot();
        p.put(TextEditorY, Integer.toString(position));
    }

    /**
     * Get the preference if SPMF should use the system text editor
     *
     * @return true or false
     */
    public boolean getShouldUseSystemTextEditor() {
        Preferences p = Preferences.userRoot();
        String value = p.get(ShouldUseSystemTextEditor, null);
        return (value == null) ? false : Boolean.parseBoolean(value);
    }

    /**
     * Store the preference if SPMF should use the system text editor
     *
     * @param value true = system text editor, false = not
     */
    public void setShouldUseSystemTextEditor(boolean value) {
        Preferences p = Preferences.userRoot();
        p.put(ShouldUseSystemTextEditor, Boolean.toString(value));
    }

    /**
     * Get the font size of the console (this is stored in a registry key)
     *
     * @return the size or null if no preference was set
     */
    public Integer getConsoleFontSize() {
        Preferences p = Preferences.userRoot();
        String value = p.get(ConsoleFontSize, null);
        if (value == null) {
            return null;
        }
        return Integer.parseInt(value);
    }

    /**
     * Store the font size for the console
     *
     * @param size the font size
     */
    public void setConsoleFontSize(int size) {
        Preferences p = Preferences.userRoot();
        p.put(ConsoleFontSize, Integer.toString(size));
    }

    // =========================================================================
    // Recent files and algorithms — persisted via private prefix keys so that
    // the PreferencesViewer reflection loop does not try to call getters for them
    // =========================================================================

    /**
     * Adds a file path to the list of recently used input files. If the path
     * already appears in the list it is moved to the front. The list is capped
     * at {@value #MAX_RECENT} entries and persisted to the system preferences
     * store so it survives application restarts.
     *
     * @param filePath the full path of the input file to record
     */
    public void addRecentInputFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return;
        }
        List<String> recent = getRecentInputFiles();
        recent.remove(filePath);
        recent.add(0, filePath);
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        saveRecentList(RECENT_INPUT_PREFIX, recent);
    }

    /**
     * Adds a file path to the list of recently used output files. If the path
     * already appears in the list it is moved to the front. The list is capped
     * at {@value #MAX_RECENT} entries and persisted to the system preferences
     * store so it survives application restarts.
     *
     * @param filePath the full path of the output file to record
     */
    public void addRecentOutputFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return;
        }
        List<String> recent = getRecentOutputFiles();
        recent.remove(filePath);
        recent.add(0, filePath);
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        saveRecentList(RECENT_OUTPUT_PREFIX, recent);
    }

    /**
     * Adds an algorithm name to the list of recently used algorithms. If the
     * name already appears in the list it is moved to the front. The list is
     * capped at {@value #MAX_RECENT} entries and persisted to the system
     * preferences store so it survives application restarts.
     *
     * @param algorithmName the name of the algorithm to record
     */
    public void addRecentAlgorithm(String algorithmName) {
        if (algorithmName == null || algorithmName.trim().isEmpty()) {
            return;
        }
        List<String> recent = getRecentAlgorithms();
        recent.remove(algorithmName);
        recent.add(0, algorithmName);
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        saveRecentList(RECENT_ALGORITHM_PREFIX, recent);
    }

    /**
     * Adds a workflow file path to the list of recently opened workflow files. If the path
     * already appears in the list it is moved to the front. The list is capped
     * at {@value #MAX_RECENT} entries and persisted to the system preferences
     * store so it survives application restarts.
     *
     * @param filePath the full path of the workflow file to record
     */
    public void addRecentWorkflowFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return;
        }
        List<String> recent = getRecentWorkflowFiles();
        recent.remove(filePath);
        recent.add(0, filePath);
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        saveRecentList(RECENT_WORKFLOW_PREFIX, recent);
    }

    /**
     * Returns the list of recently used input file paths, ordered from most
     * recent to least recent. The list is loaded from the system preferences
     * store and may be empty but is never null.
     *
     * @return a mutable list of recent input file paths
     */
    public List<String> getRecentInputFiles() {
        return loadRecentList(RECENT_INPUT_PREFIX);
    }

    /**
     * Returns the list of recently used output file paths, ordered from most
     * recent to least recent. The list is loaded from the system preferences
     * store and may be empty but is never null.
     *
     * @return a mutable list of recent output file paths
     */
    public List<String> getRecentOutputFiles() {
        return loadRecentList(RECENT_OUTPUT_PREFIX);
    }

    /**
     * Returns the list of recently used algorithm names, ordered from most
     * recent to least recent. The list is loaded from the system preferences
     * store and may be empty but is never null.
     *
     * @return a mutable list of recent algorithm names
     */
    public List<String> getRecentAlgorithms() {
        return loadRecentList(RECENT_ALGORITHM_PREFIX);
    }

    /**
     * Returns the list of recently opened workflow file paths, ordered from most
     * recent to least recent. The list is loaded from the system preferences
     * store and may be empty but is never null.
     *
     * @return a mutable list of recent workflow file paths
     */
    public List<String> getRecentWorkflowFiles() {
        return loadRecentList(RECENT_WORKFLOW_PREFIX);
    }

    /**
     * Loads a list of strings from the system preferences store using the
     * given key prefix. Entries are stored at keys
     * {@code prefix + "0"} through {@code prefix + (MAX_RECENT-1)}.
     *
     * @param prefix the registry key prefix that identifies the list
     * @return a mutable list of stored strings (never null, may be empty)
     */
    private List<String> loadRecentList(String prefix) {
        Preferences p = Preferences.userRoot();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < MAX_RECENT; i++) {
            String value = p.get(prefix + i, null);
            if (value != null && !value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    /**
     * Persists a list of strings to the system preferences store using the
     * given key prefix. Existing entries beyond the new list size are cleared
     * so that stale entries do not appear on the next load.
     *
     * @param prefix the registry key prefix that identifies the list
     * @param items  the ordered list of strings to store
     */
    private void saveRecentList(String prefix, List<String> items) {
        Preferences p = Preferences.userRoot();
        for (int i = 0; i < items.size(); i++) {
            p.put(prefix + i, items.get(i));
        }
        // Clear any slots that are no longer occupied
        for (int i = items.size(); i < MAX_RECENT; i++) {
            p.remove(prefix + i);
        }
    }

    /**
     * Reset all the preferences for SPMF stored in the system properties
     * (e.g. Windows registry or similar on other OSes).
     * This also clears the recent input-file, output-file, algorithm, and workflow-file lists.
     */
    public void resetPreferences() {
        setInputFilePath("");
        setOutputFilePath("");
        setPreferedCharset(Charset.defaultCharset().toString());
        setShouldUseSystemTextEditor(true);
        setRunAsExternalProgram(false);
        setNightMode(false);
        // Clear all recent lists
        saveRecentList(RECENT_INPUT_PREFIX,     new ArrayList<>());
        saveRecentList(RECENT_OUTPUT_PREFIX,    new ArrayList<>());
        saveRecentList(RECENT_ALGORITHM_PREFIX, new ArrayList<>());
        saveRecentList(RECENT_WORKFLOW_PREFIX,  new ArrayList<>());
    }
}