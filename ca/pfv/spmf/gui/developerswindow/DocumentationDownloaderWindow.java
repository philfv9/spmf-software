package ca.pfv.spmf.gui.developerswindow;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * Copyright (c) 2022 Philippe Fournier-Viger
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
 * JFrame to download an offline copy of the SPMF documentation.
 * 
 * @author Philippe Fournier-Viger
 *
 */
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import ca.pfv.spmf.tools.documentation_downloader.AlgoSPMFDownloadDoc;

/*
 * Copyright (c) 2022 Philippe Fournier-Viger
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
 * JFrame to download an offline copy of the SPMF documentation
 * 
 * @author Philippe Fournier-Viger
 *
 */
public class DocumentationDownloaderWindow {

	private static JButton btnDownload;
	private static JProgressBar progressBar;

	public void createAndShowGUI() {
		JFrame frame = new JFrame("SPMF Documentation Downloader");
//		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(600, 150);
		frame.setLayout(new FlowLayout());
		frame.setLocationRelativeTo(null);

		btnDownload = new JButton("Download Documentation");
		btnDownload.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// Disable the button
				btnDownload.setEnabled(false);

				// Set up the progress bar
				progressBar.setIndeterminate(true);

				// Run the download algorithm in a separate thread to keep the GUI responsive
				new Thread(new Runnable() {
					@Override
					public void run() {
						AlgoSPMFDownloadDoc downloader = new AlgoSPMFDownloadDoc();
						downloader.runAlgorithm();

						// After downloading, stop the progress bar and enable the button
						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								progressBar.setIndeterminate(false);
								btnDownload.setEnabled(true);
							}
						});
					}
				}).start();
			}
		});

		progressBar = new JProgressBar();
//        progressBar.setPreferredSize(new Dimension(100, 30));
		progressBar.setStringPainted(false);

		JTextArea area = new JTextArea();
		area.setLineWrap(true);
		area.setText(
				"This tool allows to download an offline copy of the SPMF documentation on your computer. The tool creates a /doc/ folder and download all the webpages. The file documentation.html is the index page of the documentation. Click the button below to run this algorithm.");

		area.setEditable(false);

		frame.getContentPane().setLayout(new BorderLayout());
		frame.getContentPane().add(area, BorderLayout.NORTH);
		frame.getContentPane().add(btnDownload, BorderLayout.CENTER);
		frame.getContentPane().add(progressBar, BorderLayout.SOUTH);
		frame.setVisible(true);
	}
}
