package ca.pfv.spmf.algorithms.classifiers.decisiontree.id3;
/* This file is copyright (c) 2008-2021 Philippe Fournier-Viger
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
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
import java.io.Serializable;

import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.general.Classifier;

/**
 * A classifier of type  ID3 (a decision tree)
 * @author Philippe Fournier-Viger, 2021
 * @see AlgoID3
 */
public class ClassifierID3 extends Classifier implements Serializable{
	
    /**
	 * UID
	 */
	private static final long serialVersionUID = 3461643460044366L;
	
	/** A decision tree */
	private DecisionTree tree;

	public ClassifierID3(DecisionTree tree) {
		this.tree = tree;
	}

	@Override
	public String getName() {
		return "ID3";
	}

	@Override
	public short predict(Instance instance) {
//		System.out.println(instance);
		return tree.predictTargetAttributeValue(instance);
	}

	public void print() {
		tree.print();
	}

}
