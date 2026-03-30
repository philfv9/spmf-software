package ca.pfv.spmf.gui.viewers.graphviewer.graphlayout;

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
import java.util.*;
import java.util.stream.IntStream;

import ca.pfv.spmf.gui.viewers.graphviewer.GraphViewerPanel;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GEdge;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GNode;

/**
 * Automatically place the nodes at the right position using a graph layout
 * algorithm known as the Fruchterman/Reingold (1991) algorithm, which is a
 * force directed graph layout algorithm. This is used by the GraphViewerPanel
 * of SPMF.
 * 
 * @see GraphViewerPanel
 * @author Philippe Fournier-Viger
 */
public class GraphLayoutFruchtermanReingold extends AbstractGraphLayout {
	/** The number of iterations to perform */
	double ITERATION_COUNT = 100;

	/** The starting temperature */
	double STARTING_TEMPERATURE = 50;

	/**
	 * Calculate the layout of the graph
	 * 
	 * @param edges        a list of edges
	 * @param nodes        a list of nodes
	 * @param canvasWidth  the width of the canvas
	 * @param canvasHeight the height of the canvas
	 */
	public void autoLayout(List<GEdge> edges, List<GNode> nodes, int canvasWidth, int canvasHeight) {

		// Random number generator
		// It is always initialized using the same number on purpose so that the
		// generated graph is consistent between each execution.
		Random random = new Random(2);

		GraphLayoutFruchtermanReingoldState state = getState(nodes, canvasWidth, canvasHeight);

		// Give random positions to the nodes, within the space
		// that we can use in the canvas so that node don't appear outside the canvas
		state.initializePositions(random);

		// Create some temporary array to store the displacement for X and Y of each
		// node
		// The arrays are indexed by the node
		double[] displacementX = new double[state.nodeCount];
		double[] displacementY = new double[state.nodeCount];

		// Create a mapping of node to their positions in the list of nodes to
		// accelerate the algorithm
		Map<GNode, Integer> mapNodeToIndex = new HashMap<GNode, Integer>();
		for (int i = 0; i < state.nodeCount; i++) {
			mapNodeToIndex.put(nodes.get(i), i);
		}

		// Loop for the number of iterations
		for (int i = 0; i < ITERATION_COUNT; i++) {

			long start = System.currentTimeMillis();

			// ===== Calculate the repulsion =====
			// For each node v
			for (int vIndex = 0; vIndex < state.nodeCount; vIndex++) {
				displacementX[vIndex] = 0;
				displacementY[vIndex] = 0;

				// For each node u that is different from v
				Iterator<Integer> uIndexes = getRepulsiveNodesIndexes(vIndex, state);
				while (uIndexes.hasNext()) {
					int uIndex = uIndexes.next();
					if (vIndex != uIndex) {

						// Calculate delta
						double deltaX = state.newPosX[vIndex] - state.newPosX[uIndex];
						double deltaY = state.newPosY[vIndex] - state.newPosY[uIndex];

						// Calculate the distance
						double distance = Math.sqrt(Math.pow(deltaY, 2) + Math.pow(deltaX, 2));
						if (distance > 0) {
							// Calculation the repulsion
							double repulsionForceDelta = state.kPow2 / distance;

							// Update the displacement for node V
							displacementX[vIndex] += (repulsionForceDelta * deltaX) / distance;
							displacementY[vIndex] += (repulsionForceDelta * deltaY) / distance;
						}
					}
				}
			}

			// ===== Calculate the attraction =====
			// For each edge from a node u to a node v
			for (GEdge edge : edges) {
				int uIndex = mapNodeToIndex.get(edge.getFromNode());
				int vIndex = mapNodeToIndex.get(edge.getToNode());

				// Calculate the delta
				double deltaX = state.newPosX[vIndex] - state.newPosX[uIndex];
				double deltaY = state.newPosY[vIndex] - state.newPosY[uIndex];

				// Calculate the distance
				double distance = Math.sqrt(Math.pow(deltaY, 2) + Math.pow(deltaX, 2));

				if (distance > 0) {
					// Calculate the attraction
					double attractionForce = (Math.pow(distance, 2)) / state.k;

					// Update the displacement for node V
					displacementX[uIndex] += (attractionForce * deltaX) / distance;
					displacementY[uIndex] += (attractionForce * deltaY) / distance;

					// Update the displacement for node U
					displacementX[vIndex] -= (attractionForce * deltaX) / distance;
					displacementY[vIndex] -= (attractionForce * deltaY) / distance;

				}
			}

			// Apply temperature and frame restrictions
			state.updatePositions(displacementX, displacementY);

			// Reduce the temperature as the layout approaches a better configuration
			state.temperature -= state.temperatureDecreaseByIteration;

			long end = System.currentTimeMillis();

			long elapsed = end - start;

//			System.out.println("iteration " + i +" time " + elapsed + " ms");
		}

		// We finished computing the layout
		// Now we can assign the new positions to the actual nodes
		for (int vIndex = 0; vIndex < state.nodeCount; vIndex++) {
			GNode noveV = nodes.get(vIndex);
			noveV.setPosX(state.newPosX[vIndex]);
			noveV.setPosY(state.newPosY[vIndex]);
		}
	}

	protected GraphLayoutFruchtermanReingoldState getState(List<GNode> nodes, int canvasWidth, int canvasHeight) {
		return new GraphLayoutFruchtermanReingoldState(nodes, canvasWidth, canvasHeight);
	}

	protected Iterator<Integer> getRepulsiveNodesIndexes(int vIndex, GraphLayoutFruchtermanReingoldState state) {
		return IntStream.range(0, state.nodeCount).iterator();
	}

	@Override
	public String getGeneratorName() {
		return "Fruchterman-Reingold";
	}

	protected class GraphLayoutFruchtermanReingoldState {
		protected double temperature;
		protected double temperatureDecreaseByIteration;
		protected int maxX;
		protected int maxY;
		protected int realWidth;
		protected int realHeight;
		protected int area;
		protected int nodeCount;
		protected double k;
		protected double kPow2;
		protected double[] newPosX;
		protected double[] newPosY;

		public GraphLayoutFruchtermanReingoldState(List<GNode> nodes, int canvasWidth, int canvasHeight) {

			// Initialize the temperature
			temperature = STARTING_TEMPERATURE;
			temperatureDecreaseByIteration = temperature / ITERATION_COUNT;

			// Calculate the maximum values of X and Y that we can use for node positions
			// if we assume that the node have a given radius so that node do not appear
			// outside the canvas.
			maxX = canvasWidth - GNode.getRadius();
			maxY = canvasHeight - GNode.getRadius();

			// Calculate the real size of the canvas after we remove a margin
			// that has the size of the radius.
			realWidth = maxX - GNode.getRadius();
			realHeight = maxY - GNode.getRadius();

			// Calculate the area size of the canvas
			area = realWidth * realHeight;

			// Number of node
			nodeCount = nodes.size();

			// Calculate the node distance k
			k = Math.sqrt(area / nodeCount);
			// Calculate the power 2 of k
			kPow2 = Math.pow(k, 2);

			// Create some temporary arrays to store the new positions of nodes
			newPosX = new double[nodeCount];
			newPosY = new double[nodeCount];
		}

		protected void initializePositions(Random random) {
			// Give random positions to the nodes, within the space
			// that we can use in the canvas so that node don't appear outside the canvas
			for (int vIndex = 0; vIndex < nodeCount; vIndex++) {
				newPosX[vIndex] = GNode.getRadius() + (realWidth * random.nextDouble());
				newPosY[vIndex] = GNode.getRadius() + (realHeight * random.nextDouble());
			}
		}

		protected void updatePositions(double[] displacementX, double[] displacementY) {
			// Apply temperature and frame restrictions
			for (int vIndex = 0; vIndex < nodeCount; vIndex++) {

				// Add the displacement of X to the position,
				// while ensuring that it is not greater than the temperature
				if (displacementX[vIndex] > temperature) {
					newPosX[vIndex] += temperature;
				} else if (displacementX[vIndex] < -temperature) {
					newPosX[vIndex] -= temperature;
				} else {
					newPosX[vIndex] += displacementX[vIndex];
				}

				// Add the displacement of Y to the position,
				// while ensuring that it is not greater than the temperature
				if (displacementY[vIndex] > temperature) {
					newPosY[vIndex] += temperature;
				} else if (displacementY[vIndex] < -temperature) {
					newPosY[vIndex] -= temperature;
				} else {
					newPosY[vIndex] += displacementY[vIndex];
				}

				// Set the new position of X but make sure that the position is not outside the
				// canvas
				if (newPosX[vIndex] < GNode.getRadius()) {
					newPosX[vIndex] = GNode.getRadius();
				} else if (newPosX[vIndex] > maxX) {
					newPosX[vIndex] = maxX;
				}

				// Set the new position of Y but make sure that the position is not outside the
				// canvas
				if (newPosY[vIndex] < GNode.getRadius()) {
					newPosY[vIndex] = GNode.getRadius();
				} else if (newPosY[vIndex] > maxY) {
					newPosY[vIndex] = maxY;
				}
			}
		}
	}
}
