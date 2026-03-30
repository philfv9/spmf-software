package ca.pfv.spmf.algorithms.graph_mining.aerminer;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
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
 * To do preprocessing
 * @see AERMiner
 * @author Ganghuan He 2020
 */
public class Preprocess {
    private static String[] trends = new String[] {"-", "=", "+"};

    /** map from attribute type(integer) -> attribute name */
    static Map<Integer, String> attrMapping;
    /** map from event type(integer) -> enent type name(String) */
    static Map<Integer, String> eventTypeMapping;
    /** map from event type name(String) -> event type(integer) */
    static Map<String, Integer> eventTypeMappingRe;


    public static void main(String[] args) throws IOException {
        Map<Integer,AttributedGraph> trendGraph = convertToTrendGraph();

        System.out.println(trendGraph.size());
    }

    public static Map<Integer,AttributedGraph> convertToTrendGraph() throws IOException {
       
        System.out.println("Start to convert to trend graph");
        findEventTypeMapping();

        //construct dynamic item attributed graph using DyAG which indicate trend of evolution
        Map<Integer, AttributedGraph> oriDyAG = ReadGraph.readGraph();
        //System.out.println(oriDyAG.size());
        //System.out.println(oriDyAG.get(0).getVertex(10754).getAttrDouMap());
        Map<Integer,AttributedGraph> trendDyAG = new HashMap<>();
        //for each position in DyAG, other than the last
        for (int i = 0; i < oriDyAG.size() - 1; i++) {
            //get 2 consecutive attributed graphs that are needed to find trend
            AttributedGraph aG1 = oriDyAG.get(i), aG2 = oriDyAG.get(i + 1);

            AttributedGraph tmpGraph = new AttributedGraph(i);
            //for each vertex
            for(int vId:aG1.getAllVerticeId()){
                tmpGraph.addVertex(vId);
                //get attribute maps for these 2 attributed graphs
                Map<Integer, Double> attrMap1 = aG1.getVertex(vId).getAttrDouMap();
                Map<Integer, Double> attrMap2 = aG2.getVertex(vId).getAttrDouMap();

                //for each attribute type
                for (Entry<Integer, Double> entry : attrMap1.entrySet()) {
                	Integer attrType = entry.getKey();
                	
                    //find trend of the values
                    double val1 = attrMap1.get(attrType);
                    double val2 = attrMap2.get(attrType);
                    int trendFlag = -999;
                    double diff = val2 - val1;
                    if (diff >= 1) trendFlag = 2;
                    else if (diff <= -1) trendFlag = 0;
                    //else trendFlag = 1;
                    else if(val2 > 0) trendFlag = 1;
                    int mapAttri = 3 * (attrType - 1) + trendFlag + 1;
                    if (mapAttri>0 && mapAttri <= ParametersSettingAERMiner.TOTAL_NUM_ATTR*3)
                        tmpGraph.getVertex(vId).addAttrValForV(mapAttri, 0d);

                }
            }

            for (int v1:aG1.getEdgesMap().keySet()){
                for(int v2:aG1.getEdgesMap().get(v1)){
                    tmpGraph.addEdge(v1,v2);
                }
            }
            trendDyAG.put(i,tmpGraph);
        }
        System.out.println("preprocessing finish !");
        repeatGraph(trendDyAG);


        return trendDyAG;
    }


    /**
     * This method do naive discretization for original attribute types
     * @throws IOException
     */
    public static Map<Integer, String> findEventTypeMapping() throws IOException {
        attrMapping = readAttrMapping();
        int count = 1;
        for (Entry<Integer, String> entry : attrMapping.entrySet()) {
        	Integer attrType = entry.getKey();
        	
            String attrName = attrMapping.get(attrType);
            for (String trend: trends){
                String eventName = attrName + trend;
                if(eventTypeMapping == null) eventTypeMapping = new HashMap<>();
                if(eventTypeMappingRe == null) eventTypeMappingRe = new HashMap<>();
                eventTypeMapping.put(count, eventName);
                eventTypeMappingRe.put(eventName, count);

                //System.out.println(eventName+":"+count);
                count++;
            }
        }
        return eventTypeMapping;
    }


    /**
     * This method read attribute mapping from integer to attribute name
     * @return attribute mapping
     * @throws IOException
     */
    public static Map<Integer, String> readAttrMapping() throws IOException {
//        /** set maximal number of attribute */
//        int totalNumberAttributes = ParametersSettingAERMiner.TOTAL_NUM_ATTR;
        
        //use a map to store relationship between attribute type and integer
        Map<Integer, String> attrMap = new LinkedHashMap<>();
        BufferedReader br = new BufferedReader(new FileReader(ParametersSettingAERMiner.ATTRI_MAPPING_PATH));
        int count = 1;
        String line = br.readLine();
		// for each line (transactions) until the end of the file
		while ((line != null)) { 
			// if the line is  a comment, is  empty or is a
			// kind of metadata
			if (line.isEmpty() == true ||
					line.charAt(0) == '#' || line.charAt(0) == '%'
							|| line.charAt(0) == '@') {
				continue;
			}
//			System.out.println(line + " " + count);
            attrMap.put(count++, line);
            line = br.readLine();
        }
//		System.out.println("ATTR COUNT " + count-1);
		ParametersSettingAERMiner.TOTAL_NUM_ATTR =  attrMap.size();
        //test if read successfully
//        for (Integer i : attrMap.keySet()) {
//            System.out.println(i + " " + attrMap.get(i));
//        }
        br.close();
        return attrMap;
    }

    private static void repeatGraph(Map<Integer, AttributedGraph> tempItemDyAG) {
        int repeatNum = ParametersSettingAERMiner.REPEAT;
        int oriSize = tempItemDyAG.size();
        for (int timeStamp = 0; timeStamp < oriSize; timeStamp++) {
            AttributedGraph itemAG = tempItemDyAG.get(timeStamp);
            for (int i = 1; i < repeatNum; i++) {
                tempItemDyAG.put(oriSize * i + timeStamp, itemAG);
            }
        }
    }
}

