package ca.pfv.spmf.gui.viewers.graphviewer.graphlayout;

import java.util.List;

import ca.pfv.spmf.gui.viewers.graphviewer.GraphViewerPanel;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GEdge;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GNode;

/**
 * Automatically place the nodes as a circle。 This can be used with the
 * GraphViewerPanel of SPMF.
 * 
 * @see GraphViewerPanel
 * @author Philippe Fournier-Viger
 */
public class GraphLayoutCircle extends AbstractGraphLayout {
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
//
//		// Calculate the real size of the canvas after we remove a margin
//		// that has the size of the radius.
		int realWidth = maxX - GNode.getRadius();
		int realHeigth = maxY - GNode.getRadius();
		
		// Circle center
		double circleCenterX = canvasWidth / 2d;
		double circleCenterY = canvasHeight / 2d;
		
		double circleRadius = Math.min(realWidth, realHeigth) / 2d;
	
//
//		// Number of node
		double nodeCount = nodes.size();
		
		// Angle increment
		final double increment = (2.0d * Math.PI )/ nodeCount;  // angles are in radians
//		System.out.println(increment);
		

		// Give random positions to the nodes, whithin the space
		// that we can use in the canvas so that node dont appear outside the canvas
		double angle = 0d;
		System.out.println("===================================");
		for (GNode node : nodes) {
			
//			x=rcos(θ)
//			y=rsin(θ)
			int newX = (int) (circleCenterX + (circleRadius * Math.cos(angle)));
			int newY = (int) (circleCenterY + (circleRadius * Math.sin(angle)));
//			System.out.println("angle " + angle  + "   x = " + newX +  "  y = " + newY);
			node.updatePosition(newX, newY);

			angle += increment;
		}

	}

	@Override
	public String getGeneratorName() {
		return "Circle layout";
	}
}
