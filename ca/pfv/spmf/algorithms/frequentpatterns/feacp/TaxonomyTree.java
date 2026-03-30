package ca.pfv.spmf.algorithms.frequentpatterns.feacp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
/* This file is copyright (c) 2022 Tung et al.
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/

/**
 * A taxonomy tree
 * @author Tung et al.
 * @see AlgoFEACP
 */
public class TaxonomyTree {
	
	HashMap<Integer, TaxonomyNode> mapItemToTaxonomyNode;
	
	public int MaxItem=0;

	private TaxonomyNode Root;
	
	private int GI;
	
	private int I;
	
	private int MaxLevel;
	
	
	public TaxonomyNode getRoot() {
		return Root;
	}

	public void setRoot(TaxonomyNode root) {
		Root = root;
	}

	public int getGI() {
		return GI;
	}

	public void setGI(int gI) {
		GI = gI;
	}

	public int getI() {
		return I;
	}

	public void setI(int i) {
		I = i;
	}

	public int getMaxLevel() {
		return MaxLevel;
	}

	public void setMaxLevel(int maxLevel) {
		MaxLevel = maxLevel;
	}
	
	public TaxonomyTree() {
		Root = new TaxonomyNode(-1);
		mapItemToTaxonomyNode = new HashMap<Integer, TaxonomyNode>();
		mapItemToTaxonomyNode.put(-1, Root);
		GI = 0;
		I = 0;
		MaxLevel = 0;
	}
	
	public void ReadDataFromPath(String Path) throws IOException {
		BufferedReader	reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(Path))));
		String line;
		try {
			while ((line = reader.readLine())!=null) {			// scanning through the text file
				if (line.isEmpty() == true || line.charAt(0)=='#' || line.charAt(0)=='@') { 
					continue;									// skipping comments and empty lines
				}											
				String	tokens[] = line.split(",");				// splitting string using ','														
				Integer	child = Integer.parseInt(tokens[0]);	// child comes first								
				Integer	parent = Integer.parseInt(tokens[1]);	// then its parent		
				if (child>MaxItem) {
					MaxItem=child;
				}
				if (parent>MaxItem) {
					MaxItem=parent;
				}
				TaxonomyNode nodeParent = mapItemToTaxonomyNode.get(parent);
				if (nodeParent==null) {
					nodeParent = new TaxonomyNode(parent);
					TaxonomyNode nodeChildren = mapItemToTaxonomyNode.get(child);
					if (nodeChildren==null) {
						//parent not exist - child not exist
						nodeChildren = new TaxonomyNode(child);
						nodeParent.addChildren(nodeChildren);
						mapItemToTaxonomyNode.put(child, nodeChildren);
					}
					else {
						//parent not exist - child  exist
						nodeParent.addChildren(nodeChildren);
					}
					mapItemToTaxonomyNode.put(parent, nodeParent);
				}
				else {				
					TaxonomyNode nodeChildren = mapItemToTaxonomyNode.get(child);
					if (nodeChildren==null) {
						//parent exist = child not exist
						nodeChildren = new TaxonomyNode(child);
						nodeParent.addChildren(nodeChildren);
						mapItemToTaxonomyNode.put(child, nodeChildren);
					}
					else {
						//parent exist = child not exist
						nodeParent.addChildren(nodeChildren);
					}
					
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			if(reader != null) { 
				reader.close(); 
			}
			for (Integer item : mapItemToTaxonomyNode.keySet()) {
				if (item!=-1) {
					TaxonomyNode node = mapItemToTaxonomyNode.get(item);
					if (node.getParent()==null) {
						Root.addChildren(node);
					}
				}
				
			}
			SetLevelForNode();
		}
	}
	public void SetLevelForNode() {
		for(Integer item: mapItemToTaxonomyNode.keySet()){		
			// create an empty Utieylity List that we will fill later.
			
			// add the item to the list of high TWU items
			//add map level
			int currentLevel = 0;
			if(item!=-1) {
				currentLevel = 1;
				TaxonomyNode parent = mapItemToTaxonomyNode.get(item).getParent();
				while(parent.getData()!=-1) {
					currentLevel++;
					parent = parent.getParent();
				}
			}
			if (mapItemToTaxonomyNode.get(item).getChildren().size()==0) {
				I++;
			}
			else {
				GI++;
			}
			mapItemToTaxonomyNode.get(item).setLevel(currentLevel);
			if (currentLevel>MaxLevel) {
				MaxLevel=currentLevel;
			}
			
		}
		
	}

	public HashMap<Integer, TaxonomyNode> getMapItemToTaxonomyNode() {
		return mapItemToTaxonomyNode;
	}

	public void setMapItemToTaxonomyNode(HashMap<Integer, TaxonomyNode> mapItemToTaxonomyNode) {
		this.mapItemToTaxonomyNode = mapItemToTaxonomyNode;
	}

}
