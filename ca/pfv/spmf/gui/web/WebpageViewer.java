package ca.pfv.spmf.gui.web;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Stack;

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

/*
 * Copyright (c) 2023 Philippe Fournier-Viger
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
 * This is a simple user interface to display a webpage in a window using a
 * JEditorPane
 * 
 * @author Philippe Fournier-Viger 2021
 */
@SuppressWarnings("serial")
public class WebpageViewer extends JFrame {

	/** The JEditorPane */
	JEditorPane editorPane;

	/** Address bar */
	JTextField addressBar;

	/** BACK BUTTON */
	private JButton backButton;

	/** FORWARD BUTTON */
	private JButton forwardButton;

	/** HOME BUTTON */
	private JButton homeButton;

	/** Stack of urls */
	Stack<String> backwardStack;

	/** Stack of forward urls */
	Stack<String> forwardStack;

	/** Default url */
	String defaultUrl = "https://www.philippe-fournier-viger.com/spmf/documentations.php";

	/** Status label */
	JLabel statusLabel;

	public static void main(String[] args) throws Exception {
		new WebpageViewer("https://www.philippe-fournier-viger.com/spmf/documentations.php");
	}

	/**
	 * Constructor
	 * 
	 * @param url the URL to display
	 * @throws Exception
	 */
	public WebpageViewer(String url) throws Exception {
		JFrame frame = new JFrame("Webpage viewer");
		frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		frame.setSize(800, 600);

		backwardStack = new Stack<String>();
		forwardStack = new Stack<String>();

		addressBar = new JTextField();
		addressBar.setEditable(false);

		editorPane = new JEditorPane();
		editorPane.setEditable(false);

		backButton = new JButton("Back");
		backButton.setEnabled(false);
		backButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				goBack();
			}
		});

		forwardButton = new JButton("Forward");
		forwardButton.setEnabled(false);
		forwardButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				goForward();
			}
		});

		homeButton = new JButton("Home");
		homeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				goHome();
			}
		});

		statusLabel = new JLabel("Ready");

		// Create the bar panel
		JPanel barPanel = new JPanel();
		barPanel.setLayout(new BorderLayout());
		barPanel.add(addressBar, BorderLayout.CENTER);

		// Create the button panel
		JPanel buttonPanel = new JPanel();
		buttonPanel.add(backButton);
		buttonPanel.add(forwardButton);
		buttonPanel.add(homeButton);

		barPanel.add(buttonPanel, BorderLayout.EAST);

//        frame.getContentPane().add(addressBar, BorderLayout.NORTH);
		frame.getContentPane().add(barPanel, BorderLayout.NORTH);
		frame.getContentPane().add(new JScrollPane(editorPane), BorderLayout.CENTER);
		frame.getContentPane().add(statusLabel, BorderLayout.SOUTH);
		frame.setVisible(true);

		editorPane.addHyperlinkListener(new HyperlinkListener() {
			@Override
			public void hyperlinkUpdate(HyperlinkEvent event) {
				if (HyperlinkEvent.EventType.ACTIVATED.equals(event.getEventType())) {
					setPage(event.getURL().toString(), null);
				}
			}
		});
		setPage(url, null);
	}

	/**
	 * Load a webpage
	 * 
	 * @param url           the url of that webpage
	 * @param goingBackward
	 */
	private void setPage(String url, String forwardURL) {
		try {
			editorPane.setPage(url);
			addressBar.setText(url);
			if (forwardURL != null) {
				forwardStack.add(forwardURL);
			} else {
				backwardStack.add(url);
			}

			updateBackButton();
			updateForwardButton();
			statusLabel.setText("Done");
		} catch (Exception e) {
			editorPane.setText(
					"Error retrieving webpage. " + System.lineSeparator() + System.lineSeparator() + e.getStackTrace());
//            e.printStackTrace();
			statusLabel.setText("Error");
		}
	}

	/**
	 * Update the status of the back button
	 */
	public void updateBackButton() {
		if (backwardStack.size() > 1) {
			backButton.setEnabled(true);
		} else {
			backButton.setEnabled(false);
		}
	}

	/**
	 * Update the status of the forward button
	 */
	public void updateForwardButton() {
		if (forwardStack.size() > 0) {
			forwardButton.setEnabled(true);
		} else {
			forwardButton.setEnabled(false);
		}
	}

	/**
	 * Go back to previous webpage
	 */
	public void goBack() {
		String previousURL = addressBar.getText();
		backwardStack.pop();
		String url = backwardStack.peek();
		setPage(url, previousURL);
		updateBackButton();
		updateForwardButton();
	}

	/**
	 * Go to next webpage in the stack
	 */
	public void goForward() {
		String url = forwardStack.pop();
		setPage(url, null);
//		System.out.println(url);
		updateBackButton();
		updateForwardButton();
	}

	/**
	 * Go to the default webpage
	 */
	public void goHome() {
		setPage(defaultUrl, null);
		System.out.println(defaultUrl);
	}

}
