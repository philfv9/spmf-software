package ca.pfv.spmf.gui.viewers.fastaviewer;

/* Copyright (c) 2008-2024 Philippe Fournier-Viger
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
 * Represents a single sequence entry in a FASTA file, consisting of a header
 * and the corresponding sequence data.
 * 
 * @author Philippe Fournier-Viger
 */
public class FastaSequenceEntry {
    
    /** The header/identifier of this sequence */
    private final String header;
    
    /** The sequence data */
    private final String sequence;

    /**
     * Constructs a new FASTA sequence entry.
     * 
     * @param header the sequence header/identifier
     * @param sequence the sequence data
     */
    public FastaSequenceEntry(String header, String sequence) {
        this.header = header;
        this.sequence = sequence;
    }

    /**
     * Gets the header of this sequence entry.
     * 
     * @return the sequence header
     */
    public String getHeader() {
        return header;
    }

    /**
     * Gets the sequence data of this entry.
     * 
     * @return the sequence data
     */
    public String getSequence() {
        return sequence;
    }

    /**
     * Gets the length of the sequence.
     * 
     * @return the number of characters in the sequence
     */
    public int getLength() {
        return sequence.length();
    }

    /**
     * Returns a string representation of this sequence entry in FASTA format.
     * 
     * @return a string in FASTA format
     */
    @Override
    public String toString() {
        return ">" + header + "\n" + sequence;
    }
}