package ca.pfv.spmf.gui.pluginmanager;

import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultCellEditor;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.WelcomeWindow;
import ca.pfv.spmf.gui.preferences.PreferencesManager;

/*
 * Copyright (c) 2008-2019 Philippe Fournier-Viger
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
public class PluginWindow extends JDialog {
	private static final long serialVersionUID = 1L;

	JButton buttonInstall;
	private JButton buttonUpdate;
	private JButton buttonRemove;
	private JButton buttonConnectDefault;
	private JButton buttonConnect;
	private JLabel labelRemoteRepository;
	private JLabel labelDescription;
	private JLabel labelDescriptionInstalled;
	private JLabel labelPlugins;
	private JLabel labelInstalledPlugins;
	private JLabel labelInstalledPluginsLocalComputer;
	private JPanel panel;

	JTextArea textareaDescription;
	JTextArea textareaDescriptionInstalled;

	// Plugins from repository
	DefaultTableModel tableModelPlugins;
	JTable tablePlugins;
	private JScrollPane jScrollPane1;

	// Installed plugins
	DefaultTableModel tableModelInstalledPlugins;
	JTable jTableInstalledPlugins;
	private JScrollPane jScrollPaneInstalled;

	public PluginWindow(WelcomeWindow welcome) {
		this.setAlwaysOnTop(true);
		this.setModal(true);

		initComponents();
	}

	private void initComponents() {
		setTitle("SPMF-V." + Main.SPMF_VERSION + "-Plugin Manager");
		this.setLocation(400, 100);
		this.setSize(975, 619);
		this.setResizable(false);

		labelRemoteRepository = new JLabel();
		labelDescription = new JLabel();
		labelDescriptionInstalled = new JLabel();
		labelPlugins = new JLabel();
		labelInstalledPlugins = new JLabel();
		labelInstalledPluginsLocalComputer = new JLabel();

		buttonInstall = new JButton();
		buttonInstall.setIcon(new ImageIcon(PluginWindow.class.getResource("ico_down.gif")));

		buttonUpdate = new JButton();
		buttonUpdate.setIcon(new ImageIcon(PluginWindow.class.getResource("ico_update.gif")));

		buttonRemove = new JButton();
		buttonRemove.setIcon(new ImageIcon(PluginWindow.class.getResource("ico_remove.gif")));

		buttonConnect = new JButton();
		buttonConnectDefault = new JButton();

		textareaDescription = new JTextArea();
		textareaDescription.setEnabled(true);
		textareaDescription.setEditable(false);

		textareaDescriptionInstalled = new JTextArea();
		textareaDescriptionInstalled.setEnabled(true);
		textareaDescriptionInstalled.setEditable(false);

		panel = new JPanel();

		// ================ Table plugins
		{

			tableModelPlugins = new DefaultTableModel();
			tableModelPlugins.addColumn("Name");
			tableModelPlugins.addColumn("Author");
			tableModelPlugins.addColumn("Category");
			tableModelPlugins.addColumn("Version");
			tableModelPlugins.addColumn("Documentation");

			tablePlugins = new JTable(tableModelPlugins) {
				private static final long serialVersionUID = 3834308709152773954L;

				@Override
				public boolean isCellEditable(int row, int column) {
					return column == 4;
				}
			};

			tablePlugins.setAutoCreateRowSorter(true);

			jScrollPane1 = new JScrollPane();
			jScrollPane1.setViewportView(tablePlugins);

			tablePlugins.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			tablePlugins.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

				@Override
				public void valueChanged(ListSelectionEvent event) {
					// do some actions here, for example
					// print first column value from selected row
					// if the user has selected something
					if (tablePlugins.getSelectedRow() != -1) {
						String pluginName = (String) tablePlugins.getModel().getValueAt(tablePlugins.getSelectedRow(),
								0);
						Plugin plugin = PluginManager.getPluginByNameFromList(pluginName);

						textareaDescription.setText(plugin.getDescription());

						if (PluginManager.isPluginInstalled(plugin.getName())) {
							buttonInstall.setEnabled(false);
						} else {
							buttonInstall.setEnabled(true);
						}

						jTableInstalledPlugins.clearSelection();
					} else {
						buttonInstall.setEnabled(false);
						textareaDescription.setText("");
					}
				}
			});

			tablePlugins.getColumnModel().getColumn(4).setCellRenderer(new TableButtonRenderer());
			tablePlugins.getColumnModel().getColumn(4).setCellEditor(new TableButtonEditor(new JCheckBox()));
		}
		// ===================================================

		// ================ Table INSTALLED plugins
		{
			tableModelInstalledPlugins = new DefaultTableModel();
			tableModelInstalledPlugins.addColumn("Name");
			tableModelInstalledPlugins.addColumn("Author");
			tableModelInstalledPlugins.addColumn("Category");
			tableModelInstalledPlugins.addColumn("Version");
			tableModelInstalledPlugins.addColumn("Documentation");

			jTableInstalledPlugins = new JTable(tableModelInstalledPlugins) {
				private static final long serialVersionUID = 8607034856289480021L;

				@Override
				public boolean isCellEditable(int row, int column) {
					return column == 4;
				}
			};

			jTableInstalledPlugins.setAutoCreateRowSorter(true);

			fillInstalledPluginTable();

			jTableInstalledPlugins.setShowGrid(false);

			jScrollPaneInstalled = new JScrollPane();
			jScrollPaneInstalled.setViewportView(jTableInstalledPlugins);

			jTableInstalledPlugins.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			jTableInstalledPlugins.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

				@Override
				public void valueChanged(ListSelectionEvent e) {
					// TODO Auto-generated method stub
					// if the user has selected something
					if (jTableInstalledPlugins.getSelectedRow() != -1) {

						String pluginName = (String) jTableInstalledPlugins.getModel()
								.getValueAt(jTableInstalledPlugins.getSelectedRow(), 0);
						Plugin plugin = PluginManager.getInstalledPluginByNameFromList(pluginName);
						textareaDescriptionInstalled.setText(plugin.getDescription());

						buttonUpdate.setEnabled(true);
						buttonRemove.setEnabled(true);
						tablePlugins.clearSelection();
					} else {
						buttonRemove.setEnabled(false);
						buttonUpdate.setEnabled(false);
						textareaDescriptionInstalled.setText("");

					}
				}
			});

			jTableInstalledPlugins.getColumnModel().getColumn(4).setCellRenderer(new TableButtonRenderer());
			jTableInstalledPlugins.getColumnModel().getColumn(4).setCellEditor(new TableButtonEditor(new JCheckBox()));

		}
		// ===================================================

		labelRemoteRepository.setText("Plugin repository: ");
		labelDescription.setText("Selected plugin description:");
		labelDescriptionInstalled.setText("Selected plugin description:");
		labelPlugins.setText("Available plugins (that have not been installed):");
		labelInstalledPlugins.setText("Installed Plugins:");
		labelInstalledPluginsLocalComputer.setText("Local computer:");
		textareaDescription.setLineWrap(true);
		textareaDescriptionInstalled.setLineWrap(true);

		buttonInstall.setText("Install");
		buttonInstall.setEnabled(false);
		buttonInstall.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				jButtonInstallActionPerformed(evt);
			}
		});

		buttonUpdate.setText("Update");
		buttonUpdate.setEnabled(false);
		buttonUpdate.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				jButtonUpdateActionPerformed(evt);
			}
		});

		buttonRemove.setText("Remove");
		buttonRemove.setEnabled(false);
		buttonRemove.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				jButton3RemoveActionPerformed(evt);
			}
		});

		buttonConnect.setText("other repository");
		buttonConnect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				boolean succeed = fillPluginTable(evt, false);
				if (succeed) {
					buttonConnect.setVisible(false);
					buttonConnectDefault.setVisible(false);
				}
			}
		});

		buttonConnectDefault.setText("default repository");
		buttonConnectDefault.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				boolean succeed = fillPluginTable(evt, true);
				if (succeed) {
					buttonConnect.setVisible(false);
					buttonConnectDefault.setVisible(false);
				}
			}
		});

		buttonConnect.setSize(200, 20);
		buttonConnect.setLocation(360, 20);
		getContentPane().add(buttonConnect);

		buttonConnectDefault.setSize(200, 20);
		buttonConnectDefault.setLocation(145, 20);
		getContentPane().add(buttonConnectDefault);

		labelRemoteRepository.setBounds(25, 20, 180, 20);
		getContentPane().add(labelRemoteRepository);

		// -------- Description - available plugins
		labelDescription.setBounds(760, 70, 300, 20);
		getContentPane().add(labelDescription);

		textareaDescription.setSize(200, 120);
		textareaDescription.setLocation(755, 90);
		getContentPane().add(textareaDescription);

		// -------- Description - installed plugins
		labelDescriptionInstalled.setBounds(760, 360, 300, 20);
		getContentPane().add(labelDescriptionInstalled);

		textareaDescriptionInstalled.setSize(200, 120);
		textareaDescriptionInstalled.setLocation(755, 380);
		getContentPane().add(textareaDescriptionInstalled);

		// ===== Table plugins ======

		labelPlugins.setBounds(40, 50, 300, 20);
		getContentPane().add(labelPlugins);

		jScrollPane1.setSize(700, 220);
		jScrollPane1.setLocation(40, 75);
		getContentPane().add(jScrollPane1);
		// =================================

		buttonInstall.setSize(140, 30);
		buttonInstall.setLocation(315, 300);
		getContentPane().add(buttonInstall);

		// ===== Table installed plugins ======
		labelInstalledPluginsLocalComputer.setBounds(25, 320, 300, 20);
		getContentPane().add(labelInstalledPluginsLocalComputer);

		labelInstalledPlugins.setBounds(40, 340, 300, 20);
		getContentPane().add(labelInstalledPlugins);

		jScrollPaneInstalled.setSize(700, 180);
		jScrollPaneInstalled.setLocation(40, 360);
		getContentPane().add(jScrollPaneInstalled);
		// =================================

		buttonUpdate.setSize(140, 30);
		buttonUpdate.setLocation(410, 540);
		getContentPane().add(buttonUpdate);

		buttonRemove.setSize(140, 30);
		buttonRemove.setLocation(260, 540);
		getContentPane().add(buttonRemove);

		// HIDE REPOSITORY OBJECTS
		labelPlugins.setVisible(false);
		jScrollPane1.setVisible(false);
		buttonInstall.setVisible(false);
		labelDescription.setVisible(false);
//		jButton4Documentation.setVisible(false);
		textareaDescription.setVisible(false);

		getContentPane().add(panel);
		this.setVisible(true);
	}

	void fillPluginTable() {
		try {
			// Load the plugin names from repository
			PluginManager.pluginInit();

			if (tableModelPlugins.getRowCount() > 0) {
				// Delete all rows
				int rowCount = tableModelPlugins.getRowCount();
				for (int i = rowCount - 1; i >= 0; i--) {
					tableModelPlugins.removeRow(i);
				}

			}

			// count number of installed plugins in this list
			List<Plugin> notInstalledYet = new ArrayList<Plugin>();
			for (Plugin plugin : PluginManager.listPlugin) {
				if (!PluginManager.isPluginInstalled(plugin.getName())) {
					notInstalledYet.add(plugin);
				}
			}

			for (int i = 0; i < notInstalledYet.size(); i++) {
				Object[] object = new Object[] { notInstalledYet.get(i).getName(), notInstalledYet.get(i).getAuthor(),
						notInstalledYet.get(i).getCategory(), notInstalledYet.get(i).getVersion(), "Webpage" };

				tableModelPlugins.addRow(object);
			}

		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "Network error : " + e.getMessage());
		}

	}

	void fillInstalledPluginTable() {
		for (int i = 0; i < PluginManager.listInstalledPlugins.size(); i++) {
			Object[] object = new Object[] { PluginManager.listInstalledPlugins.get(i).getName(),
					PluginManager.listInstalledPlugins.get(i).getAuthor(),
					PluginManager.listInstalledPlugins.get(i).getCategory(),
					PluginManager.listInstalledPlugins.get(i).getVersion(), "Webpage" };
			tableModelInstalledPlugins.addRow(object);

		}
	}

	private void jButtonInstallActionPerformed(ActionEvent evt) {

		new DownloadWindow(PluginManager.getPluginFolderPath().getAbsolutePath(), false, this).setVisible(true);

	}

	private void jButtonUpdateActionPerformed(ActionEvent evt) {

		new DownloadWindow(PluginManager.getPluginFolderPath().getAbsolutePath(), true, this).setVisible(true);

		buttonRemove.setEnabled(false);
		buttonUpdate.setEnabled(false);

	}

	private void jButton3RemoveActionPerformed(ActionEvent evt) {

		try {
			String pluginName = (String) jTableInstalledPlugins.getModel()
					.getValueAt(jTableInstalledPlugins.getSelectedRow(), 0);

			// Remove the plugin
			PluginManager.removePlugin(pluginName);

			buttonRemove.setEnabled(false);
			buttonUpdate.setEnabled(false);

			// REMOVE FROM TABLE OF INSTALLED PLUGIN
			tableModelInstalledPlugins.removeRow(jTableInstalledPlugins.getSelectedRow());

			// ADD TO TABLE OF AVAILABLE PLUGINS
			// IF NECESSARY
			Plugin pluginFromRepository = PluginManager.getPluginByNameFromList(pluginName);
			if (pluginFromRepository != null) {
				Object[] object = new Object[] { pluginFromRepository.getName(), pluginFromRepository.getAuthor(),
						pluginFromRepository.getCategory(), pluginFromRepository.getVersion(), "Webpage" };
				tableModelPlugins.addRow(object);
			}

		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, e.getMessage());
		}
	}

//	private void jButton4WebpageActionPerformed(ActionEvent evt) {
//		String pluginName = (String) tablePlugins.getModel().getValueAt(tablePlugins.getSelectedRow(), 0);
//		Plugin plugin = PluginManager.getPluginByNameFromList(pluginName);
//
//		// =================================
//		// Create the URL:
//		// http://www.philippe-fournier-viger.com/spmf/plugins/{pluginname}/documentation.php
//		String url2 = plugin.getUrlOfDocumentation();
//		// =================================
//		// String url2 = Plugins.url2.replace("{pluginname}",
//		if (Desktop.isDesktopSupported()) {
//			try {
//				URI uri = URI.create(url2);
//				Desktop dp = Desktop.getDesktop();
//				if (dp.isSupported(Desktop.Action.BROWSE)) {
//					dp.browse(uri);
//				}
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//		} else {
//			System.out.println("Null!");
//		}
//	}

	/**
	 * 
	 * @param evt
	 * @param connectToDefault
	 * @return false if cannot establish connection
	 */
	private boolean fillPluginTable(ActionEvent evt, boolean connectToDefault) {

		String choice;
		if (!connectToDefault) {
			choice = JOptionPane.showInputDialog(this, "Enter the URL of a plugin repository.",
					PluginManager.DEFAULT_PLUGIN_REPOSITORY);

		} else {
			choice = PluginManager.DEFAULT_PLUGIN_REPOSITORY;
		}
		if (choice != null) {
			// check if the URL is really a repository
			boolean isARepository = PluginManager.checkIfURLisAPluginRepository(choice);
			if (isARepository) {
				// Remember the URL so that if we close the software we still
				// remember it.
				PreferencesManager.getInstance().setRepositoryURL(choice);

				// Refresh the list of plugins in the JTABLE...
				fillPluginTable();

				labelPlugins.setVisible(true);
				jScrollPane1.setVisible(true);
				buttonInstall.setVisible(true);
				labelDescription.setVisible(true);
				textareaDescription.setVisible(true);

				buttonConnect.setEnabled(false);
				buttonConnectDefault.setEnabled(false);

				return true;
			} else {
				JOptionPane.showMessageDialog(this, "Cannot establish connection!");
				return false;
			}
		}
		return false;

	}

	// ------------------------------- Code for buttons in tables (cell editor)

	public class TableButtonEditor extends DefaultCellEditor {
		private static final long serialVersionUID = 2344534534l;
		private JButton button;
		private String label;
		private boolean clicked;
		private JTable table;

		public TableButtonEditor(JCheckBox checkBox) {
			super(checkBox);
			button = new JButton();
			button.setOpaque(true);
			button.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					fireEditingStopped();
				}
			});
		}

		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row,
				int column) {
			this.table = table;

			button.setForeground(Color.black);
			button.setBackground(UIManager.getColor("Button.background"));
			label = (value == null) ? "" : value.toString();
			button.setText(label);
			clicked = true;
			return button;
		}

		public Object getCellEditorValue() {
			if (clicked) {
				// ============= OPEN THE DOCUMENTATION ==============
				String pluginName = (String) table.getModel().getValueAt(table.getSelectedRow(), 0);
				Plugin plugin = null;

				if (table == tablePlugins) {
					plugin = PluginManager.getPluginByNameFromList(pluginName);
				} else if (table == jTableInstalledPlugins) {
					plugin = PluginManager.getInstalledPluginByNameFromList(pluginName);
				}

				// =================================
				// Create the URL:
				// http://www.philippe-fournier-viger.com/spmf/plugins/{pluginname}/documentation.php
				String url2 = plugin.getUrlOfDocumentation();
				// =================================
				// String url2 = Plugins.url2.replace("{pluginname}",
				if (Desktop.isDesktopSupported()) {
					try {
						URI uri = URI.create(url2);
						Desktop dp = Desktop.getDesktop();
						if (dp.isSupported(Desktop.Action.BROWSE)) {
							dp.browse(uri);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					System.out.println("Null!");
				}
			}
			clicked = false;
			return new String(label);
		}

		public boolean stopCellEditing() {
			clicked = false;
			return super.stopCellEditing();
		}
	}

	// ------------------------------- Code for buttons in tables (cell renderer)

	class TableButtonRenderer extends JButton implements TableCellRenderer {
		/**
		 * serial iD
		 */
		private static final long serialVersionUID = 2276033826743007852L;

		public TableButtonRenderer() {
			setOpaque(true);
		}

		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {
			setForeground(Color.black);
			setBackground(UIManager.getColor("Button.background"));
			setText((value == null) ? "" : value.toString());
			return this;
		}
	}

}
