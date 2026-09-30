package ca.pfv.spmf.algorithms.sequentialpatterns.sui;
/*
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
 *
 * Copyright (c) Philipe Fournier-Viger et al.
 *
 * Do not remove copyright and license information.
 */
/**
 * A wrapper class that holds either a DataIDList or a PseudoIDList for a pattern.
 * This is used so patterns can be handled uniformly regardless of their IDList type.
 *
 * @author Philippe Fournier-Viger
 */
public class IDListEntry {

    /** The data-IDList, non-null if this entry is a data-IDList */
    private DataIDList dataIDList;

    /** The pseudo-IDList, non-null if this entry is a pseudo-IDList */
    private PseudoIDList pseudoIDList;

    /** True if this entry contains a pseudo-IDList (s-extension), false for data-IDList (i-extension or 1-pattern) */
    private boolean isPseudo;

    /**
     * Constructor for a DataIDList entry
     * @param dataIDList the data-IDList
     */
    public IDListEntry(DataIDList dataIDList) {
        this.dataIDList = dataIDList;
        this.pseudoIDList = null;
        this.isPseudo = false;
    }

    /**
     * Constructor for a PseudoIDList entry
     * @param pseudoIDList the pseudo-IDList
     */
    public IDListEntry(PseudoIDList pseudoIDList) {
        this.pseudoIDList = pseudoIDList;
        this.dataIDList = null;
        this.isPseudo = true;
    }

    /**
     * Check if this entry is a pseudo-IDList
     * @return true if pseudo-IDList
     */
    public boolean isPseudo() {
        return isPseudo;
    }

    /**
     * Get the DataIDList
     * @return data-IDList or null
     */
    public DataIDList getDataIDList() {
        return dataIDList;
    }

    /**
     * Get the PseudoIDList
     * @return pseudo-IDList or null
     */
    public PseudoIDList getPseudoIDList() {
        return pseudoIDList;
    }

    /**
     * Get the support of this IDList (regardless of type)
     * @return support count
     */
    public int getSupport() {
        return isPseudo ? pseudoIDList.getSupport() : dataIDList.getSupport();
    }

    /**
     * Get the pattern associated with this IDList
     * @return the pattern
     */
    public Pattern getPattern() {
        return isPseudo ? pseudoIDList.getPattern() : dataIDList.getPattern();
    }

    /**
     * Check if the associated pattern has an i-extension
     * @return true if i-extension
     */
    public boolean isIExtension() {
        return getPattern().isIExtension();
    }

    @Override
    public String toString() {
        return isPseudo ? pseudoIDList.toString() : dataIDList.toString();
    }
}