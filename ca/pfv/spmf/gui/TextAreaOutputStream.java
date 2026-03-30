package ca.pfv.spmf.gui;

import java.io.OutputStream;

import javax.swing.JTextArea;

/**
 * A type of outputstream for displaying the console output in a JTextArea
 * component
 */
class TextAreaOutputStream extends OutputStream {
	/** The text area */
	JTextArea textArea;

	/**
	 * Constructor that receives a text area as parameter
	 * 
	 * @param textArea the text area
	 */
	public TextAreaOutputStream(JTextArea textArea) {
		this.textArea = textArea;
	}

	@Override
	public void flush() {
		textArea.repaint();
	}

	/**
	 * Write something to the text area
	 */
	public void write(int b) {
		textArea.append(new String(new byte[] { (byte) b }));
	}
}