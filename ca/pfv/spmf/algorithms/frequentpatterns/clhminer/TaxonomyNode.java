package ca.pfv.spmf.algorithms.frequentpatterns.clhminer;

import java.util.ArrayList;
import java.util.List;



/**
 * Implementation of a taxonomy node as used by CLH-Miner
 * 
 * @see AlgoCLHMiner
 * 
 * @author Bay Vo et al.
 */
public class TaxonomyNode {
	private int data ;
	
	public int getData() {
		return data;
	}

	public TaxonomyNode(int data) {
		this.data = data;
	}

	public void setData(int data) {
		this.data = data;
	}

	public List<TaxonomyNode> getChildren() {
		return children;
	}

	public void setChildren(List<TaxonomyNode> children) {
		this.children = children;
	}

	public TaxonomyNode getParent() {
		return parent;
	}

	public void setParent(TaxonomyNode parent) {
		this.parent = parent;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	private List<TaxonomyNode> children = new ArrayList<>();
	
	private TaxonomyNode parent = null;
	
	private int level;
	
	public TaxonomyNode addChildren(TaxonomyNode child){
		child.setParent(this);
		this.children.add(child);
		return child;
	}
	public void addChildren(List<TaxonomyNode> children) {
		children.forEach(each->each.setParent(this));
		this.children.addAll(children);
	}
	
}
