package ca.pfv.spmf.algorithms.graph_mining.aerminer;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
 * An enlarged graph
 * @see AERMiner
 * @author Ganghuan He 2020
 */
public class EnlargeGraph {
    /** indicate whether to store all attribute values as type of double **/
    private static boolean ALLASDOUBLE = true;
    /** set maximal number of attribute */
    private static int TOTAL_NUM_ATTR = ParametersSettingAERMiner.TOTAL_NUM_ATTR;
    /** store path of file that record attributes of vertices each time */
    private static String ATTR_FILE_PATH = ParametersSettingAERMiner.ATTR_FILE_PATH;
    /** store path of file that record edges of vertices each time */
    private static String EDGE_FILE_PATH = ParametersSettingAERMiner.EDGE_FILE_PATH;
    private static String VERTICES_FILE_PATH = ParametersSettingAERMiner.VERTEX_MAP_NAME_PATH;
    private static int VERTEXNUM = ParametersSettingAERMiner.VERTEXNUM;

    private static int MOVENUM = 10000;
    public static void main(String[] args) throws IOException {
        int[] enlarge = {1,5,10,15,20,25,30,50};
        for(int i:enlarge){
            statGraph(i);
        }

    }

    public static void statGraph(int enlargeSize) throws IOException {
        Map<Integer, AttributedGraph> dyAG = readEnLargeGraph(enlargeSize);

        int numTimestamps = dyAG.size();
        int numVertices = dyAG.get(0).getVerNum();
        int totalCount4E = 0;
        for (Entry<Integer, AttributedGraph> entry : dyAG.entrySet()) {
        	int i = entry.getKey();
        	
            AttributedGraph aG = dyAG.get(i);
            for (Map.Entry<Integer, Set<Integer>> edgeLinkEntry: aG.getEdgesMap().entrySet()) {
                totalCount4E += edgeLinkEntry.getValue().size();
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("total timestamps: ").append(numTimestamps);
        sb.append("\ntotal vertices: ").append(numVertices);
        sb.append("\naverage edges for each vertex each timestamp: ").append(1.0 * totalCount4E/(numTimestamps * numVertices));
        System.out.println(sb.toString());

        FileWriter fileWriterAttr = new FileWriter(ATTR_FILE_PATH + "_" + enlargeSize+".txt");
        FileWriter fileWriterEdge = new FileWriter(EDGE_FILE_PATH + "_" + enlargeSize+".txt");
        for (int t = 0; t < dyAG.size(); t++){
            fileWriterAttr.write("T" + t + "\n");
            fileWriterEdge.write("T" + t + "\n");
            AttributedGraph attributedGraph = dyAG.get(t);
            Map<Integer, Set<Integer>> edgesMap = attributedGraph.getEdgesMap();
            for(Entry<Integer, Set<Integer>> entry2 : edgesMap.entrySet()){
            	Integer v = entry2.getKey();
            	
                fileWriterEdge.write(v+"");
                for(int otherV : edgesMap.get(v)){
                    fileWriterEdge.write(" " + otherV);
                }
                fileWriterEdge.write("\n");
            }

            for(int vId : attributedGraph.getAllVerticeId()){
                Vertex v = attributedGraph.getVertex(vId);
                fileWriterAttr.write(vId+"");

                for(int i = 1; i <= TOTAL_NUM_ATTR;i++){
                    fileWriterAttr.write(" " + v.getAttrDouMap().get(i));
                }
                fileWriterAttr.write("\n");
            }
        }

        fileWriterAttr.close();
        fileWriterEdge.close();


        FileWriter fileWriterMapping = new FileWriter(VERTICES_FILE_PATH + "_" + enlargeSize + ".txt");
        BufferedReader br = new BufferedReader(new FileReader(VERTICES_FILE_PATH));
        String line = br.readLine();

        while(line != null){
            String[] split = line.split(",");
            int id = Integer.parseInt(split[0]);
            for(int i = 0; i < enlargeSize; i++){
                fileWriterMapping.write(id+(MOVENUM+VERTEXNUM)*i +","+split[1]+"\n");
            }
            line = br.readLine();
        }
        fileWriterMapping.close();
        br.close();
    }


    public static Map<Integer, AttributedGraph> readEnLargeGraph(int size) throws IOException {
        System.out.println("@@@ start to read original graph ...");
        //create an empty DyAG, use a map denote this DyAG
        Map<Integer, AttributedGraph> DyAG = new HashMap<>();

        readEnLargeGraphAttributes(DyAG,size);
        System.out.println(DyAG.size());
        readEnLargeGraphEdges(DyAG,size);

        System.out.println("reading graph finish !");

        //test whether read attributes and edges successfully
        return DyAG;

    }

    private static void readEnLargeGraphEdges(Map<Integer,AttributedGraph> dyAG, int enlargerSize) throws IOException {
        BufferedReader brEdges = new BufferedReader(new FileReader(EDGE_FILE_PATH));
        String line2 = brEdges.readLine();
        while (line2 != null) {
            if (line2.startsWith("T")) {

                int aGId = Integer.parseInt(line2.split("T")[1]);
                AttributedGraph aG = dyAG.get(aGId);
                while ((line2 = brEdges.readLine()) != null && !line2.startsWith("T")) {
                    edgeLineProcessEnlarge(aG, line2,enlargerSize);
                }
            }
        }
        brEdges.close();
    }

    private static void edgeLineProcessEnlarge(AttributedGraph aG, String line, int enlargerSize) {

            String[] items = line.split(" ");

            for(int enlarge = 0; enlarge < enlargerSize; enlarge++){
                //value of first position denote id of the common vertex linking to rest vertices in the line
                int vId = Integer.parseInt(items[0]) + enlarge * (aG.getVerNum() + MOVENUM);
                // store ids of all other neighboring vertices
                List<Integer> neighbors = new LinkedList<>();
                //for each item other than the first one
                for (int i = 1; i < items.length; i++) {
                    //parse it to integer and add it to id list
                    neighbors.add(Integer.parseInt(items[i]) + enlarge * (VERTEXNUM + MOVENUM));
                }
                aG.addEdges(vId, neighbors);
            }

    }

    private static void readEnLargeGraphAttributes(Map<Integer,AttributedGraph> dyAG, int enlargeSize) throws IOException {

        //add vertices and attributes for an empty DyAG according to file "attributes.txt"
        BufferedReader brAttr = new BufferedReader(new FileReader(ATTR_FILE_PATH));


        //while still has unprocessed line

        String line1 = brAttr.readLine();
        int count = 0;

        while (line1 != null) {
            //if it indicate a new attributed graph
            if (line1.startsWith("T")) {
                AttributedGraph aG = new AttributedGraph(count);
                while ((line1 = brAttr.readLine()) != null && ! line1.startsWith("T")) {
                    attrLineProcessEnlarge(aG, line1,enlargeSize);
                }
                dyAG.put(count, aG);
            }
            count++;
        }
        brAttr.close();
    }

    private static void attrLineProcessEnlarge(AttributedGraph aG, String line, int enlargeSize) {
        String[] items = line.split(" ");
        //value of first position denote id of the vertex
        for(int enlarge = 0; enlarge < enlargeSize;enlarge++) {
            int vId = Integer.parseInt(items[0]) + (VERTEXNUM+MOVENUM)*enlarge;
            aG.addVertex(vId);

            if (ALLASDOUBLE) {
                //store all attribute values as type of double
                //attribute type list
                List<Integer> attrTypes = new LinkedList<>();
                //attribute value list
                List<Double> attrVals = new LinkedList<>();
                for (int i = 1; i < TOTAL_NUM_ATTR + 1; i++) {
                    Double val = Double.parseDouble(items[i] );
                    attrTypes.add(i);
                    attrVals.add(val);
                }
                //add attribute types and values
                aG.addAttrValForV(vId, attrTypes, attrVals);
            }
        }
    }
}
