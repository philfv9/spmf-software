package ca.pfv.spmf.algorithms.graph_mining.aerminer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
/* This file is copyright (c) 2020 by Ganghuan He
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
 * The USFlight map
 * @see AERMiner
 * @author Ganghuan He 2020
 */
public class USFlightMap {
    public static void main(String[] args) throws IOException {
        transformEdges();
    }

    public static Map<Integer,Integer> id2vId() throws IOException {
        String mapAddress = ParametersSettingAERMiner.projectPath+"/dataset/USFlight/idMapKatrina.txt";
        Map<Integer,Integer> id2vId = new HashMap<>();
        BufferedReader bf = new BufferedReader(new FileReader(mapAddress));
        String line = bf.readLine();
        while (line != null){
            String[] nums = line.split("\\s");
            //System.out.println(nums[0]);
            //System.out.println(nums[1]);
            int id = Integer.parseInt(nums[1]);
            int vID = Integer.parseInt(nums[0]);
            id2vId.put(id,vID);
            line = bf.readLine();
        }
        bf.close();

        return id2vId;
    }



    public static void transformEdges() throws IOException {
        Map<Integer,Integer> id2vId  = id2vId();
        String edgesAddress = ParametersSettingAERMiner.projectPath+"\\dataset\\synthetic\\t8_v280_e4_a8\\edges.txt";
        String vIdEdgesAddress = ParametersSettingAERMiner.projectPath+"\\dataset\\synthetic\\t8_v280_e4_a8\\vIdEdges.txt";
        BufferedReader br = new BufferedReader(new FileReader(edgesAddress));
        BufferedWriter bw = new BufferedWriter(new FileWriter(vIdEdgesAddress));
        String line ;
        while ((line= br.readLine()) !=null){
            if (line.startsWith("T")){
                bw.write(line);
                bw.newLine();
                continue;
            }
            String[] nums = line.split("\\s");
            StringBuilder sb = new StringBuilder();
            for (String num :nums){
                sb.append(id2vId.get(Integer.parseInt(num))+" ");
            }
             bw.write(sb.toString());
            bw.newLine();
        }
        br.close();
        bw.close();
    }

}
