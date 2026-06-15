package ca.pfv.spmf.gui.workflow_editor;

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
 * 
 * Do not remove copyright and license information.
 */
/**
 * Class for testing the workflow editor of SPMF
 */
public class MainTestWorkflowEditor {
	/**
	 * The main method
	 * 
	 * @param args the parameters of the method
	 * @throws Exception if something bad happen
	 */
	@SuppressWarnings("unused")
	public static void main(String[] args) throws Exception {
		boolean runAsStandalone = true;
		// Create an instance of the draw frame
		WorkflowEditorWindow drawFrame = new WorkflowEditorWindow(runAsStandalone);

	}

}