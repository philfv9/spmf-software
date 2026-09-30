package ca.pfv.spmf.gui.viewers.instanceviewer;

/* This file is copyright (c) 2008-2016 Philippe Fournier-Viger
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

/**
 * Listener of an {@link InstancesViewerPanel} ("listener" design pattern).
 *
 * @author Philippe Fournier-Viger 2016
 */
public interface InstanceViewerPanelListener {

	/**
	 * Notify listeners of the new chart position of the mouse
	 * @param x the x coordinate (in data units)
	 * @param y the y coordinate (in data units)
	 */
	void notifyOfNewMousePosition(double x, double y);

	/**
	 * Notify listeners that the mouse is outside of the chart
	 */
	void notifyMouseOutOfChart();

	/**
	 * Notify listeners of the instance under the mouse (optional).
	 * @param index index of the instance in the list, or -1 if none
	 * @param x the x value of that instance (selected X attribute)
	 * @param y the y value of that instance (selected Y attribute)
	 */
	default void notifyOfInstanceUnderMouse(int index, double x, double y) {
		// optional
	}
}