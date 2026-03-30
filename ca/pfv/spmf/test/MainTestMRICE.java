package ca.pfv.spmf.test;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.mrice.AlgoMRICE;
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
* 
*/
/**
 * This class shows how to run the MRI-CE algorithm from the source code.
 * @see AlgoMRICE
 */
public class MainTestMRICE {
    public static void main(String[] args) throws IOException {
    	
        String input=fileToPath("contextZart.txt");
        String output="output.txt";
        
        final double min_sup_percentage = 0.6;
        
        AlgoMRICE mriCE =new AlgoMRICE();
        mriCE.runAlgorithm(input,output,min_sup_percentage);
        mriCE.printStats();
    }
    public static  String fileToPath(String filename) throws UnsupportedEncodingException{
        URL url= MainTestMRICE.class.getResource((filename));
        return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
    }
}
