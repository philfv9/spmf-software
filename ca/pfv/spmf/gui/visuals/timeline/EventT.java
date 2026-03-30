package ca.pfv.spmf.gui.visuals.timeline;
/*
 * Copyright (c) 2022 Philippe Fournier-Viger
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
 * Represents an event as used by the TimelineViewer of SPMF
 * 
 * @author Philippe Fournier-Viger
 */
public class EventT extends ElementT{
    /** The timestamp of the event **/
    private final long time; 

    /**
     * Constructor
     * @param name the event name
     * @param time the timestamp
     */
    public EventT(String name, long time) {
        super(name);
        this.time = time;
    }

    /**
     * Get the timestamp of the event
     * @return the timestamp
     */
    public long getTime() {
        return time;
    }
}