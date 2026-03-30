package ca.pfv.spmf.algorithms.frequentpatterns.HMiner_CLosed;

/* This file is copyright (c) 2018+  by Siddharth Dawar et al.
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
 * This class represents an Element of a utility list as used by the HUI-Miner algorithm.
 * <br/>
 * The code was obtained from Github repository of user "limuhangk" under the GPL license (since
 * the code is derived from GPL code from SPMF).
 * @see AlgoHMiner_Closed
 * @author Siddharth Dawar et al.
 * @see MCUL_List
 */
class Element_MCUL_List {
    // The three variables as described in the paper:
    /**
     * transaction id
     */
    final int tid;
    /**
     * non closed itemset utility
     */
    long Nu;
    /**
     * prefix utility
     */
    long Npu;
    /**
     * non closed remaining utility
     */
    long Nru;
    /**
     * W(X,Tj)
     */
    long WXTj;//the weight of X in Tj   W(X,Tj)=|{Tk∈D|SR(X,Tj)=SR(X,Tk)}|;   SR(X,Tk)=|{i∈Tj : i∈E(X)}|;
    /**
     * ppos
     */
    int Ppos;


    /**
     * Constructor
     *
     * @param tid  transaction id
     * @param nu   itemset utility
     * @param nru  remaining utility
     * @param Npu   prefix utility
     * @param ppos
     */
    public Element_MCUL_List(int tid, long nu, long nru, long Npu, int ppos) {
        this.tid = tid;
        this.Nu = nu;
        this.Nru = nru;
        this.Npu = Npu;
        this.Ppos = ppos;
    }


    /**
     *
     * Constructor
     * Each transaction contains sextuple <Tj,NU,NPU,NRU,W,PPOS>
     *
     * @param tid
     * @param nu
     * @param nru
     * @param Npu
     * @param WXTj
     * @param ppos
     *
     */
    public Element_MCUL_List(int tid, long nu, long nru, long Npu, long WXTj, int ppos) {
        this.tid = tid;
        this.Nu = nu;
        this.Nru = nru;
        this.Npu = Npu;
        this.WXTj = WXTj;
        this.Ppos = ppos;
    }
}
