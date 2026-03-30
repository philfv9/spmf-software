package ca.pfv.spmf.gui.viewers.graphviewer.graphlayout;

import ca.pfv.spmf.gui.viewers.graphviewer.GraphViewerPanel;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GNode;

import java.util.*;

/**
 * Automatically place the nodes at the right position using a graph layout
 * algorithm known as the Fruchterman/Reingold (1991) algorithm, which is a
 * force directed graph layout algorithm. This is used by the GraphViewerPanel
 * of SPMF. This version is implemented by Zevin Shaul and uses the Grid
 * optimization to accelerate the task of performing the layout.
 * 
 * @see GraphViewerPanel
 * @author Zevin Shaul
 */
public class GraphLayoutFruchtermanReingoldGrid extends GraphLayoutFruchtermanReingold {

	protected class GraphLayoutFruchtermanReingoldGridState extends GraphLayoutFruchtermanReingoldState {
		protected Grid grid;

		public GraphLayoutFruchtermanReingoldGridState(List<GNode> nodes, int canvasWidth, int canvasHeight) {
			super(nodes, canvasWidth, canvasHeight);
			grid = new Grid();
		}

		/**
		 * A Grid
		 */
		public class Grid {
			protected ArrayList<ArrayList<Set<Integer>>> grid;
			protected int rows;
			protected int columns;
			protected int newRows[];
			protected int newColumns[];

			public Grid() {

				// create grid with cell 2k x 2k
				columns = (int) Math.ceil(maxX / (2 * k));
				rows = (int) Math.ceil(maxY / (2 * k));
				grid = new ArrayList<ArrayList<Set<Integer>>>();
				for (int i = 0; i < rows; i++) {
					grid.add(new ArrayList<Set<Integer>>());
					for (int j = 0; j < columns; j++) {
						grid.get(i).add(new HashSet<Integer>());
					}
				}

				newRows = new int[nodeCount];
				newColumns = new int[nodeCount];
			}

			private int nodeRow(double posY) {
				return (int) Math.floor(posY / (2 * k));
			}

			private int nodeColumn(double posX) {
				return (int) Math.floor(posX / (2 * k));
			}
		}
		
		//======================

		@Override
		protected void initializePositions(Random random) {
			super.initializePositions(random);

			// add node to it's cell
			for (Integer vIndex = 0; vIndex < nodeCount; vIndex++) {
				grid.newRows[vIndex] = grid.nodeRow(newPosY[vIndex]);
				grid.newColumns[vIndex] = grid.nodeColumn(newPosX[vIndex]);

				grid.grid.get(grid.newRows[vIndex]).get(grid.newColumns[vIndex]).add(vIndex);
			}
		}

		@Override
		protected void updatePositions(double[] displacementX, double[] displacementY) {
			super.updatePositions(displacementX, displacementY);

			for (Integer vIndex = 0; vIndex < nodeCount; vIndex++) {
				int row = grid.nodeRow(newPosY[vIndex]);
				int column = grid.nodeColumn(newPosX[vIndex]);

				if (row != grid.newRows[vIndex] || column != grid.newColumns[vIndex]) {
					grid.grid.get(grid.newRows[vIndex]).get(grid.newColumns[vIndex]).remove(vIndex);
					grid.newRows[vIndex] = row;
					grid.newColumns[vIndex] = column;
					grid.grid.get(grid.newRows[vIndex]).get(grid.newColumns[vIndex]).add(vIndex);
				}
			}
		}
	}

	@Override
	protected GraphLayoutFruchtermanReingoldState getState(List<GNode> nodes, int canvasWidth, int canvasHeight) {
		return new GraphLayoutFruchtermanReingoldGridState(nodes, canvasWidth, canvasHeight);
	}

	@Override
	protected Iterator<Integer> getRepulsiveNodesIndexes(int vIndex, GraphLayoutFruchtermanReingoldState state) {
		GraphLayoutFruchtermanReingoldGridState gridState = (GraphLayoutFruchtermanReingoldGridState) state;
		List<Integer> repulsiveNodesIndexes = new LinkedList<Integer>();

		int row = gridState.grid.newRows[vIndex];
		int column = gridState.grid.newColumns[vIndex];

		for (int i = Math.max(0, row - 1); i < Math.min(gridState.grid.rows, row + 2); i++) {
			for (int j = Math.max(0, column - 1); j < Math.min(gridState.grid.columns, column + 2); j++) {
				Set<Integer> uIndexes = gridState.grid.grid.get(i).get(j);

				for (int uIndex : uIndexes) {
					if (uIndex == vIndex) {
						continue;
					}

					// Calculate the delta
					double deltaX = state.newPosX[vIndex] - state.newPosX[uIndex];
					double deltaY = state.newPosY[vIndex] - state.newPosY[uIndex];

					// Calculate the distance
					double distance = Math.sqrt(Math.pow(deltaY, 2d) + Math.pow(deltaX, 2d));

					if (distance <= gridState.k * 2) {
						repulsiveNodesIndexes.add(uIndex);
					}
				}
			}
		}

		return repulsiveNodesIndexes.iterator();
	}

	@Override
	public String getGeneratorName() {
		return "Fruchterman-Reingold-Grid";
	}
}
