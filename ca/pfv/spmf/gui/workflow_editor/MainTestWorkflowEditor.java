package ca.pfv.spmf.gui.workflow_editor;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;

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

//		String[] outputTypes = AlgorithmManager.getInstance().getDescriptionOfAlgorithm("FHM").getOutputFileTypes();
//
////		if (outputTypes == null) {
////			comboBoxRenderer.suggested.set(0, cardinality, false);
////		} else {
////			for (int i = 1; i < comboBoxAlgorithms.getItemCount(); i++) {
////				String algo = comboBoxAlgorithms.getItemAt(i);
//		String algo = "Visualize_High_Utility_itemsets";
//
//		if ("Open_text_file_with_SPMF_text_editor".equals(algo) || "Open_text_file_with_system_text_editor".equals(algo)
//				|| "Open_text_file_with_pattern_viewer".equals(algo)) {
//			System.out.println("HERE");
////					comboBoxRenderer.suggested.set(i, true);
//		} else if (algo.startsWith(" --")) {
//			System.out.println("HERE2");
////					comboBoxRenderer.suggested.set(i, true);
//		} else {
//			DescriptionOfAlgorithm algorithmCandidate = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algo);
//			String[] inputTypes = algorithmCandidate.getInputFileTypes();
//			if (inputTypes == null) {
////						comboBoxRenderer.suggested.set(i, cardinality, false);
//				System.out.println("HERE4");
//			} else {
////						String inputMainType = inputTypes[inputTypes.length - 1];
//				if (!hasCommonType(outputTypes, inputTypes)) {
////						if (!inputMainType.contains(mainOutputType)) {
//
//					System.out.println("HERE5");
////							comboBoxRenderer.suggested.set(i, false);
//				}
//
//			}
//
//		}
	}

//	/**
//	 * Check if there is something in common between an array of input types and an
//	 * array of output types
//	 * 
//	 * @param outputTypes the array of output types
//	 * @param inputTypes  the array of input types
//	 * @return true if there is something in common
//	 */
//	public static boolean hasCommonType(String[] outputTypes, String[] inputTypes) {
//		for (String inputType : inputTypes) {
//			for (String outputType : outputTypes) {
//				if (!"Patterns".equals(outputType) && !"Database of instances".equals(outputType)) {
//					if (outputType.equals(inputType)) {
//						return true;
//					}
//				}
//			}
//		}
//		return false;
//	}
}