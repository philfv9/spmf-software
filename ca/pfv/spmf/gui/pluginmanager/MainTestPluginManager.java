package ca.pfv.spmf.gui.pluginmanager;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

public class MainTestPluginManager {

	public static void main(String[] args) throws IOException, URISyntaxException {
		// Initialize the plugin manager
		PluginManager.pluginInit();
		
		// Get the list of available plugins from the repository
		List<Plugin> listPlugin = PluginManager.listPlugin;
		
		// Print the name and description of each plugin
		for(Plugin plugin : listPlugin) {
			System.out.println("Plugin name: " + plugin.getName());
			System.out.println("Plugin description: " + plugin.getDescription());
			System.out.println();
		}
		
		// Get the information of a specific plugin by name
		Plugin plugin = PluginManager.getPluginByNameFromList("removelongtransactions");
		if(plugin != null) {
			System.out.println("Plugin name: " + plugin.getName());
			System.out.println("Plugin author: " + plugin.getAuthor());
			System.out.println("Plugin version: " + plugin.getVersion());
			System.out.println("Plugin category: " + plugin.getCategory());
			System.out.println("Plugin URL of documentation: " + plugin.getUrlOfDocumentation());
			System.out.println("Plugin input file types: " + plugin.getInputFileTypes());
			System.out.println("Plugin output file types: " + plugin.getOutputFileTypes());
			System.out.println("Plugin number of parameters: " + plugin.getParameterCount());
			System.out.println("Plugin parameters: " + plugin.getParameters());
			System.out.println();
		}
		
		// Check if a plugin is installed
		boolean isInstalled = PluginManager.isPluginInstalled("removelongtransactions");
		System.out.println("Is removelongtransactions installed? " + isInstalled);
		System.out.println();
		
		
		// Uninstall a plugin
		PluginManager.removePlugin("removelongtransactions");
		System.out.println("Plugin removelongtransactions uninstalled.");
		System.out.println();
		
		// Install a plugin
		PluginManager.installPlugin(plugin);
		System.out.println("Plugin removelongtransactions installed.");
		System.out.println();
		
		// Update a plugin
		PluginManager.updatePlugin(plugin);
		System.out.println("Plugin removelongtransactions updated.");
		System.out.println();

	}

}
