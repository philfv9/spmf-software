package ca.pfv.spmf.gui.texteditor;

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
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.UndoManager;

/**
 * Class to add the undo functionality to a JTextArea It is inspired by public
 * domain code.
 * 
 * @author Philippe Fournier-Viger
 */
public class UndoTool {
	/** Redo */
	private static final String REDO_KEY = "redo";
	/** Undo */
	private static final String UNDO_KEY = "undo";

	/** keystroke for undo */
	private KeyStroke undo = KeyStroke.getKeyStroke("control Z");
	/** keystore for redo */
	private KeyStroke redo = KeyStroke.getKeyStroke("control Y");

	/** Undo manager */
	UndoManager manager;

	/**
	 * 
	 * @param component
	 */
	@SuppressWarnings("serial")
	public UndoTool(JTextComponent component) {
		manager = new UndoManager();
		manager.setLimit(100);

		Document document = component.getDocument();
		document.addUndoableEditListener(event -> manager.addEdit(event.getEdit()));

		// Add the redo key mapping
		component.getActionMap().put(REDO_KEY, new AbstractAction(REDO_KEY) {
			@Override
			public void actionPerformed(ActionEvent evt) {
				redo();
			}
		});
		component.getInputMap().put(redo, REDO_KEY);

		// Add the redo action key mapping
		component.getActionMap().put(UNDO_KEY, new AbstractAction(UNDO_KEY) {
			@Override
			public void actionPerformed(ActionEvent evt) {
				undo();
			}
		});
		component.getInputMap().put(undo, UNDO_KEY);
	}

	/**
	 * Redo
	 */
	void redo() {
		try {
			if (manager.canRedo()) {
				manager.redo();
			}
		} catch (CannotRedoException ignore) {
		}
	}

	/**
	 * Undo
	 */
	void undo() {
		try {
			if (manager.canUndo()) {
				manager.undo();
			}
		} catch (CannotRedoException ignore) {
		}
	}
}