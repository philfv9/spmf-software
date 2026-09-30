package ca.pfv.spmf.gui.texteditor;

/*
 * Copyright (c) 2008-2022 Philippe Fournier-Viger
 * This file is part of the SPMF DATA MINING SOFTWARE (http://www.philippe-fournier-viger.com/spmf).
 * SPMF is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version. SPMF is distributed WITHOUT ANY
 * WARRANTY. See the GNU General Public License for more details.
 */
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;

/**
 * Class to add the undo functionality to a JTextArea. It is inspired by public
 * domain code.
 * 
 * @author Philippe Fournier-Viger
 */
public class UndoTool {
	private static final String REDO_KEY = "redo";
	private static final String UNDO_KEY = "undo";

	private final KeyStroke undo = KeyStroke.getKeyStroke(KeyEvent.VK_Z,
			Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
	private final KeyStroke redo = KeyStroke.getKeyStroke(KeyEvent.VK_Y,
			Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());

	/** Undo manager */
	final UndoManager manager;

	/** Edit currently being grouped (null if not grouping) */
	private CompoundEdit compoundEdit = null;

	@SuppressWarnings("serial")
	public UndoTool(JTextComponent component) {
		manager = new UndoManager();
		manager.setLimit(100);

		Document document = component.getDocument();
		document.addUndoableEditListener(event -> {
			if (compoundEdit != null) {
				compoundEdit.addEdit(event.getEdit());
			} else {
				manager.addEdit(event.getEdit());
			}
		});

		component.getActionMap().put(REDO_KEY, new AbstractAction(REDO_KEY) {
			@Override
			public void actionPerformed(ActionEvent evt) {
				redo();
			}
		});
		component.getInputMap().put(redo, REDO_KEY);

		component.getActionMap().put(UNDO_KEY, new AbstractAction(UNDO_KEY) {
			@Override
			public void actionPerformed(ActionEvent evt) {
				undo();
			}
		});
		component.getInputMap().put(undo, UNDO_KEY);
	}

	/** Redo */
	void redo() {
		try {
			if (manager.canRedo()) {
				manager.redo();
			}
		} catch (CannotRedoException ignore) {
		}
	}

	/** Undo */
	void undo() {
		try {
			if (manager.canUndo()) {
				manager.undo();
			}
		} catch (CannotUndoException ignore) {
		}
	}

	/** Forget all recorded edits (e.g. after loading a file) */
	void discardAllEdits() {
		compoundEdit = null;
		manager.discardAllEdits();
	}

	/**
	 * Start grouping all following edits into a single undoable step. Must be
	 * paired with {@link #endCompoundEdit()} (use try/finally).
	 */
	void beginCompoundEdit() {
		if (compoundEdit == null) {
			compoundEdit = new CompoundEdit();
		}
	}

	/** Stop grouping edits and record the group as one undoable step */
	void endCompoundEdit() {
		if (compoundEdit != null) {
			CompoundEdit edit = compoundEdit;
			compoundEdit = null;
			edit.end();
			if (edit.isSignificant()) {
				manager.addEdit(edit);
			}
		}
	}
}