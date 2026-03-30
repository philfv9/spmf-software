package ca.pfv.spmf.gui.viewers.graphviewer.graphlayout;

import java.util.List;

import ca.pfv.spmf.gui.viewers.graphviewer.GraphViewerPanel;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GEdge;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GNode;

/**
 * Automatically place the nodes as a grid。 This can be used with the
 * GraphViewerPanel of SPMF.
 * 
 * @see GraphViewerPanel
 * @author Philippe Fournier-Viger
 */
public class GraphLayoutGrid extends AbstractGraphLayout {
	/** The number of iterations to perform */

	/**
	 * Calculate the layout of the graph
	 * 
	 * @param edges        a list of edges
	 * @param nodes        a list of nodes
	 * @param canvasWidth  the width of the canvas
	 * @param canvasHeight the height of the canvas
	 */
	public void autoLayout(List<GEdge> edges, List<GNode> nodes, int canvasWidth, int canvasHeight) {

		// Calculate the maximum values of X and Y that we can use for node positions
		// if we assume that the node have a given radius so that node do not appear
		// outside the canvas.
		int maxX = canvasWidth - GNode.getRadius();
		int maxY = canvasHeight - GNode.getRadius();

		// Calculate the real size of the canvas after we remove a margin
		// that has the size of the radius.
		int realWidth = maxX - GNode.getRadius();
		int realHeigth = maxY - GNode.getRadius();

		// Number of node
		int nodeCount = nodes.size();
		
		int squareRootX = (int) Math.ceil(Math.sqrt(nodeCount));
		int squareRootY = (int) Math.floor(Math.sqrt(nodeCount));
		
		if(squareRootX * squareRootY < nodeCount) {
			squareRootY = (int) Math.ceil(Math.sqrt(nodeCount));
		}
		
		int cellWidthX = realWidth  / squareRootX;
		int cellWidthY = realHeigth  / squareRootY;
		

		int halfcellWidthX = cellWidthX / 2;
		int halfcellWidthY = cellWidthY / 2;

		// Give random positions to the nodes, whithin the space
		// that we can use in the canvas so that node dont appear outside the canvas
		int cellXPos =0;
		int cellYPos =0;
		for (GNode node : nodes) {
			int newX = (int) (GNode.getRadius() + cellXPos * cellWidthX + halfcellWidthX);
			int newY = (int) (GNode.getRadius() + cellYPos * cellWidthY + halfcellWidthY);
			node.updatePosition(newX, newY);
			cellXPos++;
			if(cellXPos == squareRootX) {
				cellXPos = 0;
				cellYPos++;
			}
		}

	}

	@Override
	public String getGeneratorName() {
		return "Grid layout";
	}
}
