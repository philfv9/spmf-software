package ca.pfv.spmf.gui.viewers.graph_transition_viewer;

import java.util.List;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger
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
 * A force-directed layout algorithm for positioning graph nodes. Co-occurrence
 * edges use a shorter ideal length to pull co-occurring items closer together.
 * <p>
 * Only nodes that have not been previously positioned are randomized.
 * Calling layout on already-positioned nodes refines their positions without
 * destroying manual adjustments.
 * <p>
 * After layout converges, a post-processing step rescales and recenters the
 * graph to fill the target canvas, eliminating the "clustered in the middle"
 * problem for large graphs.
 * <p>
 * Optimized for large graphs: pre-computes edge index pairs to avoid HashMap
 * lookups per iteration, and uses squared-distance comparisons where possible
 * to reduce sqrt calls.
 *
 * @author Philippe Fournier-Viger 2025
 */
public class ForceDirectedLayout {

	/** Repulsion force strength between nodes */
	private double repulsionStrength = 5000.0;
	/** Attraction force strength along edges */
	private double attractionStrength = 0.01;
	/** Damping factor to stabilize layout */
	private double damping = 0.85;
	/** Base ideal edge length for transition edges */
	private double baseIdealLength = 120.0;
	/** Ratio of co-occurrence ideal length to transition ideal length */
	private double coOccurrenceRatio = 0.5;
	/** Maximum displacement per iteration */
	private double maxDisplacement = 50.0;

	/** Canvas width */
	private double width = 800;
	/** Canvas height */
	private double height = 600;
	/** Padding from canvas edges */
	private double padding = 40;

	/**
	 * Sets the repulsion strength between nodes.
	 * @param v repulsion strength value
	 */
	public void setRepulsionStrength(double v) { this.repulsionStrength = v; }

	/**
	 * Sets the attraction strength along edges.
	 * @param v attraction strength value
	 */
	public void setAttractionStrength(double v) { this.attractionStrength = v; }

	/**
	 * Sets the canvas dimensions.
	 * @param w canvas width
	 * @param h canvas height
	 */
	public void setCanvasSize(double w, double h) { this.width = w; this.height = h; }

	/**
	 * Compute scaled ideal edge lengths based on the number of nodes.
	 * For larger graphs, edges need to be longer to give nodes room to spread.
	 */
	private double computeIdealLength(int nodeCount) {
		if (nodeCount <= 20) return baseIdealLength;
		// Scale ideal length with sqrt of node count for better spacing
		double scale = Math.sqrt(nodeCount / 20.0);
		return baseIdealLength * Math.min(scale, 5.0);
	}

	/**
	 * Compute repulsion strength scaled for the number of nodes.
	 * Larger graphs need stronger repulsion to prevent clustering.
	 */
	private double computeRepulsion(int nodeCount) {
		if (nodeCount <= 20) return repulsionStrength;
		// Scale repulsion linearly with node count
		double scale = nodeCount / 20.0;
		return repulsionStrength * Math.min(scale, 10.0);
	}

	/**
	 * Runs the layout algorithm. Nodes without initialized positions are placed
	 * randomly first. Already-positioned nodes are refined from their current location.
	 * <p>
	 * After the force simulation converges, positions are rescaled and recentered
	 * to fill the target canvas area, so the graph uses the available space
	 * regardless of how tightly the forces pulled nodes together.
	 *
	 * @param nodes      the nodes to position
	 * @param edges      the edges acting as springs
	 * @param iterations number of layout iterations
	 */
	public void layout(List<TransitionGraph.GraphNode> nodes,
			List<TransitionGraph.GraphEdge> edges, int iterations) {
		if (nodes == null || nodes.isEmpty()) return;

		int n = nodes.size();

		// Compute adaptive parameters based on graph size
		double idealLength = computeIdealLength(n);
		double coOccurrenceIdealLength = idealLength * coOccurrenceRatio;
		double effectiveRepulsion = computeRepulsion(n);

		// Use a larger initial spread for the random placement so the simulation
		// starts with nodes further apart
		double initW = Math.max(width, n * 15.0);
		double initH = Math.max(height, n * 15.0);

		// Only randomize nodes that haven't been positioned yet
		for (int i = 0; i < n; i++) {
			TransitionGraph.GraphNode node = nodes.get(i);
			if (!node.isPositionInitialized()) {
				node.setX(padding + Math.random() * (initW - 2 * padding));
				node.setY(padding + Math.random() * (initH - 2 * padding));
				node.setPositionInitialized(true);
			}
		}

		// Pre-compute edge index pairs to avoid HashMap lookups every iteration
		int edgeCount = edges != null ? edges.size() : 0;
		int[] edgeSrcIdx = new int[edgeCount];
		int[] edgeTgtIdx = new int[edgeCount];
		double[] edgeIdealLen = new double[edgeCount];
		int validEdgeCount = 0;

		if (edges != null && !edges.isEmpty()) {
			// Build index map once
			java.util.HashMap<String, Integer> indexMap = new java.util.HashMap<>(
					(int) (n * 1.4) + 1);
			for (int i = 0; i < n; i++) {
				indexMap.put(nodes.get(i).getItemLabel(), i);
			}

			for (TransitionGraph.GraphEdge edge : edges) {
				Integer si = indexMap.get(edge.getSourceItem());
				Integer ti = indexMap.get(edge.getTargetItem());
				if (si == null || ti == null) continue;
				edgeSrcIdx[validEdgeCount] = si;
				edgeTgtIdx[validEdgeCount] = ti;
				edgeIdealLen[validEdgeCount] =
						(edge.getEdgeType() == TransitionGraph.EdgeType.CO_OCCURRENCE)
						? coOccurrenceIdealLength : idealLength;
				validEdgeCount++;
			}
		}

		// Use parallel arrays for positions and forces for better cache locality
		double[] posX = new double[n];
		double[] posY = new double[n];
		double[] dx = new double[n];
		double[] dy = new double[n];

		// Initialize position arrays
		for (int i = 0; i < n; i++) {
			posX[i] = nodes.get(i).getX();
			posY[i] = nodes.get(i).getY();
		}

		// Use centroid as gravity center (not fixed canvas center) so the
		// simulation is independent of canvas size
		double grav = 0.002;
		double maxDispSq = maxDisplacement * maxDisplacement;

		// Increase max displacement for larger graphs so nodes can move further
		double effectiveMaxDisp = maxDisplacement * Math.max(1.0, Math.sqrt(n / 20.0));
		double effectiveMaxDispSq = effectiveMaxDisp * effectiveMaxDisp;

		for (int iter = 0; iter < iterations; iter++) {
			// Compute centroid for gravity
			double cx = 0, cy = 0;
			for (int i = 0; i < n; i++) { cx += posX[i]; cy += posY[i]; }
			cx /= n; cy /= n;

			// Clear forces
			for (int i = 0; i < n; i++) { dx[i] = 0; dy[i] = 0; }

			// Repulsion (O(N^2) — could use Barnes-Hut for very large graphs)
			for (int i = 0; i < n; i++) {
				double xi = posX[i], yi = posY[i];
				for (int j = i + 1; j < n; j++) {
					double diffX = xi - posX[j];
					double diffY = yi - posY[j];
					double distSq = diffX * diffX + diffY * diffY;
					if (distSq < 0.25) {
						diffX = (Math.random() - 0.5) * 2;
						diffY = (Math.random() - 0.5) * 2;
						distSq = 1.0;
					}
					double dist = Math.sqrt(distSq);
					double forceDivDist = effectiveRepulsion / (distSq * dist);
					double fx = forceDivDist * diffX;
					double fy = forceDivDist * diffY;
					dx[i] += fx; dy[i] += fy;
					dx[j] -= fx; dy[j] -= fy;
				}
			}

			// Attraction (using pre-computed index pairs)
			for (int e = 0; e < validEdgeCount; e++) {
				int si = edgeSrcIdx[e];
				int ti = edgeTgtIdx[e];
				double diffX = posX[si] - posX[ti];
				double diffY = posY[si] - posY[ti];
				double distSq = diffX * diffX + diffY * diffY;
				if (distSq < 0.25) distSq = 0.25;
				double dist = Math.sqrt(distSq);

				double force = attractionStrength * (dist - edgeIdealLen[e]);
				double fx = force * diffX / dist;
				double fy = force * diffY / dist;

				dx[si] -= fx; dy[si] -= fy;
				dx[ti] += fx; dy[ti] += fy;
			}

			// Gravity toward centroid (not fixed canvas center)
			for (int i = 0; i < n; i++) {
				dx[i] += grav * (cx - posX[i]);
				dy[i] += grav * (cy - posY[i]);
			}

			// Apply displacements with damping
			double curDamp = damping * (1.0 - 0.8 * (double) iter / iterations);
			if (curDamp < 0.05) curDamp = 0.05;

			for (int i = 0; i < n; i++) {
				double dispX = dx[i] * curDamp;
				double dispY = dy[i] * curDamp;
				double dispSq = dispX * dispX + dispY * dispY;
				if (dispSq > effectiveMaxDispSq) {
					double scale = effectiveMaxDisp / Math.sqrt(dispSq);
					dispX *= scale;
					dispY *= scale;
				}
				posX[i] += dispX;
				posY[i] += dispY;
				// NOTE: No boundary clamping during simulation — we rescale at the end
			}
		}

		// ================================================================
		// Post-processing: rescale and recenter to fill the target canvas
		// ================================================================
		rescaleToCanvas(posX, posY, n);

		// Write positions back to nodes
		for (int i = 0; i < n; i++) {
			nodes.get(i).setX(posX[i]);
			nodes.get(i).setY(posY[i]);
		}
	}

	/**
	 * Rescale and recenter node positions so they fill the target canvas
	 * (width x height) with appropriate padding.
	 */
	private void rescaleToCanvas(double[] posX, double[] posY, int n) {
		if (n <= 1) {
			// Single node: just center it
			if (n == 1) {
				posX[0] = width / 2.0;
				posY[0] = height / 2.0;
			}
			return;
		}

		// Find bounding box of computed positions
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		for (int i = 0; i < n; i++) {
			if (posX[i] < minX) minX = posX[i];
			if (posX[i] > maxX) maxX = posX[i];
			if (posY[i] < minY) minY = posY[i];
			if (posY[i] > maxY) maxY = posY[i];
		}

		double graphW = maxX - minX;
		double graphH = maxY - minY;

		// Target area with padding
		double targetW = width - 2 * padding;
		double targetH = height - 2 * padding;

		if (targetW <= 0) targetW = width;
		if (targetH <= 0) targetH = height;

		// Compute uniform scale factor (preserve aspect ratio)
		double scaleX = (graphW > 1e-6) ? targetW / graphW : 1.0;
		double scaleY = (graphH > 1e-6) ? targetH / graphH : 1.0;
		double scale = Math.min(scaleX, scaleY);

		// Don't scale up if the graph naturally fits — only if it's too small
		// relative to the canvas. This avoids blowing up tiny graphs excessively.
		// However, for the "clustered in middle" problem, we DO want to scale up.
		// So we always apply the scale.

		// Center of the graph
		double graphCx = (minX + maxX) / 2.0;
		double graphCy = (minY + maxY) / 2.0;

		// Center of the canvas
		double canvasCx = width / 2.0;
		double canvasCy = height / 2.0;

		// Apply: translate to origin, scale, translate to canvas center
		for (int i = 0; i < n; i++) {
			posX[i] = (posX[i] - graphCx) * scale + canvasCx;
			posY[i] = (posY[i] - graphCy) * scale + canvasCy;
		}
	}

	/**
	 * Resets all node positions and runs layout from scratch.
	 *
	 * @param nodes      the nodes to position
	 * @param edges      the edges acting as springs
	 * @param iterations number of layout iterations
	 */
	public void layoutFromScratch(List<TransitionGraph.GraphNode> nodes,
			List<TransitionGraph.GraphEdge> edges, int iterations) {
		if (nodes == null) return;
		for (TransitionGraph.GraphNode node : nodes) {
			node.setPositionInitialized(false);
		}
		layout(nodes, edges, iterations);
	}
}