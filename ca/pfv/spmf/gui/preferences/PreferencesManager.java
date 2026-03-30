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
		// read back from registry HKCurrentUser
		Preferences p = Preferences.userRoot();
		return p.get(InputFilePath, null);
	}

	/**
	 * Store an input file path in the registry
	 * 
	 * @param filepath a path as a string
	 */
	public void setInputFilePath(String filepath) {
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(InputFilePath, filepath);
	}

	/**
	 * Get the output file path stored in the registry
	 * 
	 * @return a path as a string
	 */
	public String getOutputFilePath() {
		// read back from registry HKCurrentUser
		Preferences p = Preferences.userRoot();
		return p.get(OutputFilePath, null);
	}

	/**
	 * Store an output file path in the registry
	 * 
	 * @param filepath a path as a string
	 */
	public void setOutputFilePath(String filepath) {
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(OutputFilePath, filepath);
	}

	/**
	 * Get the experiment directory path stored in the registry
	 * 
	 * @return a path as a string
	 */
	public String getExperimentDirectoryPath() {
		// read back from registry HKCurrentUser
		Preferences p = Preferences.userRoot();
		return p.get(ExperimentDirectoryPath, null);
	}

	/**
	 * Store an output file path in the registry
	 * 
	 * @param filepath a path as a string
	 */
	public void setExperimentDirectoryPath(String filepath) {
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(ExperimentDirectoryPath, filepath);
	}
	// REGKEY_SPMF_EXPERIMENT_DIRECTORY_PATH

	/**
	 * Store the path to spmf.jar in the registry
	 * 
	 * @param path the path
	 */
	public void setSPMFJarFilePath(String path) {
		// write into HKCurrentUser
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
//        //      read back from registry HKCurrentUser
//        Preferences p = Preferences.userRoot();
//        return p.get(REGKEY_SPMF_PLUGIN_FOLDER_PATH, null);
//    }
//
//    /**
//     * Store an output file path in the registry
//     * @param filepath a path as a string
//     */
//    public void setPluginFolderFilePath(String filepath) {
//        // write into HKCurrentUser
//        Preferences p = Preferences.userRoot();
//        p.put(REGKEY_SPMF_PLUGIN_FOLDER_PATH, filepath);
//    }
//    
//    /**
//     * Delete the plugin file path from the registry
//     * @param filepath a path as a string
//     */
//    public void deletePluginFolderFilePath() {
//        // write into HKCurrentUser
//        Preferences p = Preferences.userRoot();
//        p.remove(REGKEY_SPMF_PLUGIN_FOLDER_PATH);
//    }
//    
//    
	// ---
	/**
	 * Store a repository URL in the registry
	 * 
	 * @param filepath a repository URL as a string
	 */
	public void setRepositoryURL(String filepath) {
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(RepositoryURL, filepath);
	}

	/**
	 * Get the repository URL path stored in the registry
	 * 
	 * @return a path as a string
	 */
	public String getRepositoryURL() {
		// read back from registry HKCurrentUser
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
		// read back from registry HKCurrentUser
		Preferences p = Preferences.userRoot();
		String charsetName = p.get(PreferedCharset, null);
		
		if(charsetName != null && charsetName.equals("false")) {
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
		// write into HKCurrentUser
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
		// read back from registry HKCurrentUser
		Preferences p = Preferences.userRoot();
		String value = p.get(RunAsExternalProgram, null);

		return (value == null) ? false : Boolean.parseBoolean(value);
	}

	/**
	 * Store the preference if algorithms should be run as an external program by
	 * SPMF's GUI
	 * 
	 * @param value true of false
	 */
	public void setRunAsExternalProgram(boolean value) {
		// write into HKCurrentUser
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
		// read back from registry HKCurrentUser
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
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(LastMemoryUsage, Double.toString(lastMemoryUsage));
	}

	/**
	 * Get the preference about whether the night mode is on or off (this is stored
	 * in a registry key)
	 * 
	 * @return the memory usage as a double
	 */
	public boolean getNightMode() {
		// read back from registry HKCurrentUser
		Preferences p = Preferences.userRoot();
		String value = p.get(NightMode, null);

		return (value == null) ? false : Boolean.parseBoolean(value);
	}

	/**
	 * Store the preference if night mode is activated or not
	 * 
	 * @param value true of false
	 */
	public void setNightMode(boolean value) {
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(NightMode, Boolean.toString(value));
	}

	/**
	 * Get the font size for the text editor (this is stored in a registry key)
	 * 
	 * @return thefont size
	 */
	public int getTextEditorFontSize() {
		// read back from registry HKCurrentUser
		Preferences p = Preferences.userRoot();
		String value = p.get(TextEditorFontSize, null);
		if (value == null) {
			return 12;
		}
		return Integer.parseInt(value);
	}

	/**
	 * Store the font size for the text edito
	 * 
	 * @param fontsize a number
	 */
	public void setTextEditorFontSize(int fontsize) {
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(TextEditorFontSize, Integer.toString(fontsize));
	}

//    /**
//     * Store the preference about how many seconds an algorithm should run at most in the GUI
//     * @param text the number of seconds
//     */
//	public void setMaxSeconds(int number) {
//		 // write into HKCurrentUser
//        Preferences p = Preferences.userRoot();
//        p.put(REGKEY_SPMF_MAX_SECONDS, Integer.toString(number));
//	}
//	
//    /**
//     * Get the preference about how many seconds an algorithm should run at most in the GUI
//     * @return a string containing a number (integer)
//     */
//    public int getMaxSeconds() {
//        //      read back from registry HKCurrentUser
//        Preferences p = Preferences.userRoot();
//        String value = p.get(REGKEY_SPMF_MAX_SECONDS, null);
//
//        return (value == null) ? Integer.MAX_VALUE : Integer.parseInt(value);
//    }

	/**
	 * Get the preference about line wrap is on or off for the text editor(this is
	 * stored in a registry key)
	 * 
	 * @return the memory usage as a double
	 */
	public boolean getTextEditorLineWrap() {
		// read back from registry HKCurrentUser
		Preferences p = Preferences.userRoot();
		String value = p.get(TextEditorLineWrap, null);

		return (value == null) ? true : Boolean.parseBoolean(value);
	}

	/**
	 * Store the preference if line wrap mode is activated or not
	 * 
	 * @param value true of false
	 */
	public void setTextEditorLineWrap(boolean value) {
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(TextEditorLineWrap, Boolean.toString(value));
	}

	/**
	 * Get the preference about word wrap is on or off for the text editor(this is
	 * stored in a registry key)
	 * 
	 * @return the memory usage as a double
	 */
	public boolean getTextEditorWordWrap() {
		// read back from registry HKCurrentUser
		Preferences p = Preferences.userRoot();
		String value = p.get(TextEditorWordWrap, null);

		return (value == null) ? false : Boolean.parseBoolean(value);
	}

	/**
	 * Store the preference if line wrap mode is activated or not
	 * 
	 * @param value true of word
	 */
	public void setTextEditorWordWrap(boolean value) {
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(TextEditorWordWrap, Boolean.toString(value));
	}

	// ---
	/**
	 * Store the text editor font familly in the registry
	 * 
	 * @param filepath a font familly
	 */
	public void setFontFamilly(String filepath) {
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(FontFamilly, filepath);
	}

	/**
	 * Get the text editor font familly stored in the registry
	 * 
	 * @return a string (font familly)
	 */
	public String getFontFamilly() {
		// read back from registry HKCurrentUser
		Preferences p = Preferences.userRoot();
		String result = p.get(FontFamilly, null);
		return (result == null) ? "Dialog" : result;
	}

//   public static final String REGKEY_TEXTEDITOR_WIDTH = "ca.pfv.spmf.gui.texteditor.width";
//   public static final String REGKEY_TEXTEDITOR_HEIGTH =

	/**
	 * Get the width of the text editor (this is stored in a registry key)
	 * 
	 * @return the width
	 */
	public int getTextEditorWidth() {
		// read back from registry HKCurrentUser
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
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(TextEditorWidth, Integer.toString(width));
	}

	/**
	 * Get the height of the text editor (this is stored in a registry key)
	 * 
	 * @return the height
	 */
	public int getTextEditorHeight() {
		// read back from registry HKCurrentUser
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
	 * @param heigth the height
	 */
	public void setTextEditorHeight(int height) {
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(TextEditorHeight, Integer.toString(height));
	}

	// =================================

	/**
	 * Get the text area width of the text editor (this is stored in a registry key)
	 * 
	 * @return the text area width
	 */
	public int getTextEditorAreaWidth() {
		// read back from registry HKCurrentUser
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
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(TextEditorAreaWidth, Integer.toString(width));
	}

	/**
	 * Get the text area height of the text editor (this is stored in a registry
	 * key)
	 * 
	 * @return the height
	 */
	public int getTextEditorAreaHeight() {
		// read back from registry HKCurrentUser
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
	 * @param heigth the height
	 */
	public void setTextEditorAreaHeight(int height) {
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(TextEditorAreaHeight, Integer.toString(height));
	}

	// =============================
	/**
	 * Get the X position of the text editor (this is stored in a registry key)
	 * 
	 * @return the height
	 */
	public int getTextEditorX() {
		// read back from registry HKCurrentUser
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
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(TextEditorX, Integer.toString(position));
	}

	/**
	 * Get the Y position of the text editor (this is stored in a registry key)
	 * 
	 * @return the height
	 */
	public int getTextEditorY() {
		// read back from registry HKCurrentUser
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
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(TextEditorY, Integer.toString(position));
	}
	
	/**
	 * Get the preference if SPMF should use the system text editor
	 * SPMF's GUI
	 * 
	 * @return true or false
	 */
	public boolean getShouldUseSystemTextEditor() {
		// read back from registry HKCurrentUser
		Preferences p = Preferences.userRoot();
		String value = p.get(ShouldUseSystemTextEditor, null);

		return (value == null) ? false : Boolean.parseBoolean(value);
	}

	/**
	 * Store the preference if SPMF should use the system text editor
	 * 
	 * @param true = system text editor, false = not
	 */
	public void setShouldUseSystemTextEditor(boolean value) {
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(ShouldUseSystemTextEditor, Boolean.toString(value));
	}
	
	// =============================
	/**
	 * Get the font size of the console (this is stored in a registry key)
	 * 
	 * @return the size or null if no preference was set
	 */
	public Integer getConsoleFontSize() {
		// read back from registry HKCurrentUser
		Preferences p = Preferences.userRoot();
		String value = p.get(ConsoleFontSize, null);
		if (value == null) {
			return null;
		}
		return Integer.parseInt(value);
	}

	/**
	 * Store X position of the text editor
	 * 
	 * @param size the font size
	 */
	public void setConsoleFontSize(int size) {
		// write into HKCurrentUser
		Preferences p = Preferences.userRoot();
		p.put(ConsoleFontSize, Integer.toString(size));
	}

	/**
	 * Reset all the preferences for SPMF stored in the system properties (e.g. Windows registry or similar on other OSes)
	 */
	public void resetPreferences() {
		setInputFilePath("");
		setOutputFilePath("");
		setPreferedCharset(Charset.defaultCharset().toString());
		setShouldUseSystemTextEditor(true);
		setRunAsExternalProgram(false);
		setNightMode(false);
	}

}
