package ca.pfv.spmf.gui.viewers.graphviewer.graphlayout;

import java.util.List;

import ca.pfv.spmf.gui.viewers.graphviewer.GraphViewerPanel;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GEdge;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GNode;

/**
 * Automatically place the nodes as a rectangle. This can be used with the
 * GraphViewerPanel of SPMF.
 * 
 * @see GraphViewerPanel
 * @author Philippe Fournier-Viger
 */
public class GraphLayoutRectangle extends AbstractGraphLayout {
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
		
		// Rectangle center
		double rectangleCenterX = canvasWidth / 2d;
		double rectangleCenterY = canvasHeight / 2d;
		
		double rectangleWidth = realWidth / 2d;
		double rectangleHeight = realHeigth / 2d;
	
//
//		// Number of node
		double nodeCount = nodes.size();
		
		// Angle increment
		final double increment = (2.0d * Math.PI )/ nodeCount;  // angles are in radians
//		

        // Give random positions to the nodes, whithin the space
        // that we can use in the canvas so that node dont appear outside the canvas
        double angle = 0d;
        for (GNode node : nodes) {
            
            // Calculate the x and y coordinates on a unit circle
            double x = Math.cos(angle);
            double y = Math.sin(angle);
            
            // Scale and translate the coordinates to fit the rectangle shape
            int newX = (int) (rectangleCenterX + (rectangleWidth * x / Math.max(Math.abs(x), Math.abs(y))));
            int newY = (int) (rectangleCenterY + (rectangleHeight * y / Math.max(Math.abs(x), Math.abs(y))));
            
            node.updatePosition(newX, newY);

            angle += increment;
        }

    }

    @Override
    public String getGeneratorName() {
        return "Rectangle layout";
    }
}
