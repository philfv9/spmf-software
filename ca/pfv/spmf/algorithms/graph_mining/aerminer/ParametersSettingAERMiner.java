package ca.pfv.spmf.algorithms.graph_mining.aerminer;
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
 * Parameter settings of the AER Miner algorithm
 * @see AERMiner
 * @author Ganghuan He 2020
 */
public class ParametersSettingAERMiner {

	
	/** This flag specify different dataset
     *  case 0: DBLP
     *  case 1: USA flight
     */
//    public static int TASK_FLAG = 0;
    public static String projectPath = System.getProperty("user.dir");
    
    //follow parameters are specific for different datasets
    // main parameters
    /** minimal support ratio */
    public static double MINSUP ;

    public static double MINCONF;

    public static double MINLIFT;

    //input and output file path
    /** path of file recording attribute mapping */

    public static String ATTRI_MAPPING_PATH;
    /** path of file describing attributes of vertices*/
    public static String OLD_ATTR_FILE_PATH;
    public static String ATTR_FILE_PATH;
    /** path of file describing edges of vertices */
    public static String OLD_EDGE_FILE_PATH;
    public static String EDGE_FILE_PATH;
    /** path of file recording mined patterns */
    public static String PATTERN_PATH;

    public static String OLD_VERTEX_MAP_NAME_PATH;
    public static String VERTEX_MAP_NAME_PATH;

    public static int VERTEXNUM;
    public static int TOTAL_NUM_ATTR;

    public static int TIME_NUM;
    public static int REPEAT = 0;


//    static {
//        switch (TASK_FLAG) {
//            case 0: {
//                //for DBLP dataset
//                TOTAL_NUM_ATTR = 43;
//                VERTEXNUM = 2723;
//                MINSUP = 0.004;
//                MINCONF = 0.3d;
//                MINLIFT = 1.5d;
//                TIME_NUM = 8;
//                ATTRI_MAPPING_PATH = projectPath + "/dataset/DBLP/attributes_mapping.txt";
//
//                ATTR_FILE_PATH = projectPath + "/dataset/DBLP/attributes.txt";
//
//                EDGE_FILE_PATH = projectPath + "/dataset/DBLP/graph - Copie.txt";
//                VERTEX_MAP_NAME_PATH = projectPath + "/dataset/DBLP/vertices_mapping.txt";
//                PATTERN_PATH = projectPath + "/dataset/DBLP/pattern/patterns.txt";
//                OLD_ATTR_FILE_PATH = ATTR_FILE_PATH;
//                OLD_EDGE_FILE_PATH = EDGE_FILE_PATH;
//                OLD_VERTEX_MAP_NAME_PATH = VERTEX_MAP_NAME_PATH;
//                break;
//            }
//
//            case 1:{
//                TOTAL_NUM_ATTR = 8;
//                VERTEXNUM = 280;
//                MINSUP = 0.004;
//                MINCONF = 0.2d;
//                MINLIFT = 1.3d;
//
//                TIME_NUM = 7;
//                ATTRI_MAPPING_PATH = projectPath + "/dataset/USFlight/attributes_mapping.txt";
//                ATTR_FILE_PATH = projectPath + "/dataset/USFlight/attributesKatrina.txt";
//                EDGE_FILE_PATH = projectPath + "/dataset/USFlight/graphFlightsKatrina.txt";
//                VERTEX_MAP_NAME_PATH = projectPath + "/dataset/USFlight/vertices_mapping.txt";
//
//                PATTERN_PATH = projectPath + "/dataset/USFlight/pattern/patterns.txt";
//                OLD_ATTR_FILE_PATH = ATTR_FILE_PATH;
//                OLD_EDGE_FILE_PATH = EDGE_FILE_PATH;
//                OLD_VERTEX_MAP_NAME_PATH = VERTEX_MAP_NAME_PATH;
//                break;
//            }
//            case 2:{
//                TOTAL_NUM_ATTR = 43;
//                VERTEXNUM = 2723;
//                MINSUP = 0.004;
//                MINCONF = 0.3d;
//                MINLIFT = 1.5d;
//
//                TIME_NUM = 8;
//                ATTRI_MAPPING_PATH = projectPath + "/dataset/synthetic/t9_v2723_e4_a43/attrMap.txt";
//                ATTR_FILE_PATH = projectPath + "/dataset/synthetic/t9_v2723_e4_a43/attributes.txt";
//                EDGE_FILE_PATH = projectPath + "/dataset/synthetic/t9_v2723_e4_a43/edges.txt";
//                VERTEX_MAP_NAME_PATH = projectPath + "/dataset/synthetic/t9_v2723_e4_a43/vertices_mapping.txt";
//                break;
//            }
//
//            case 3:{
//                VERTEXNUM = 280;
//                TOTAL_NUM_ATTR = 8;
//                MINSUP = 0.004;
//                MINCONF = 0.3d;
//                MINLIFT = 1.2d;
//
//                TIME_NUM = 7;
//                ATTRI_MAPPING_PATH = projectPath + "/dataset/synthetic/t8_v280_e4_a8/attrMap.txt";
//                ATTR_FILE_PATH = projectPath + "/dataset/synthetic/t8_v280_e4_a8/attributes.txt";
//                EDGE_FILE_PATH = projectPath + "/dataset/synthetic/t8_v280_e4_a8/vIdEdges.txt";
//                VERTEX_MAP_NAME_PATH = projectPath + "/dataset/synthetic/t8_v280_e4_a8/vertices_mapping.txt";
//
//                break;
//            }
//        }
//
//    }
//
//
//    public static void switchData(int task_flag) {
//        TASK_FLAG = task_flag;
//
//        switch (TASK_FLAG) {
//            case 0: {
//                //for DBLP dataset
//                TOTAL_NUM_ATTR = 43;
//                VERTEXNUM = 2723;
//                MINSUP = 0.004;
//                MINCONF = 0.3d;
//                MINLIFT = 1.5d;
//                TIME_NUM = 8;
//
//                ATTRI_MAPPING_PATH = projectPath + "/dataset/DBLP/attributes_mapping.txt";
//
//                ATTR_FILE_PATH = projectPath + "/dataset/DBLP/attributes.txt";
//
//                EDGE_FILE_PATH = projectPath + "/dataset/DBLP/graph - Copie.txt";
//                VERTEX_MAP_NAME_PATH = projectPath + "/dataset/DBLP/vertices_mapping.txt";
//                PATTERN_PATH = projectPath + "/dataset/DBLP/pattern/patterns.txt";
//                OLD_ATTR_FILE_PATH = ATTR_FILE_PATH;
//                OLD_EDGE_FILE_PATH = EDGE_FILE_PATH;
//                OLD_VERTEX_MAP_NAME_PATH = VERTEX_MAP_NAME_PATH;
//                break;
//            }
//
//            case 1: {
//                TOTAL_NUM_ATTR = 8;
//                VERTEXNUM = 280;
//                MINSUP = 0.004;
//                MINCONF = 0.3d;
//                MINLIFT = 1.3d;
//
//                TIME_NUM = 7;
//                ATTRI_MAPPING_PATH = projectPath + "/dataset/USFlight/attributes_mapping.txt";
//                ATTR_FILE_PATH = projectPath + "/dataset/USFlight/attributesKatrina.txt";
//                EDGE_FILE_PATH = projectPath + "/dataset/USFlight/graphFlightsKatrina.txt";
//                VERTEX_MAP_NAME_PATH = projectPath + "/dataset/USFlight/vertices_mapping.txt";
//
//                PATTERN_PATH = projectPath + "/dataset/USFlight/pattern/patterns.txt";
//                OLD_ATTR_FILE_PATH = ATTR_FILE_PATH;
//                OLD_EDGE_FILE_PATH = EDGE_FILE_PATH;
//                OLD_VERTEX_MAP_NAME_PATH = VERTEX_MAP_NAME_PATH;
//                break;
//            }
//            case 2: {
//                TOTAL_NUM_ATTR = 43;
//                VERTEXNUM = 2723;
//                MINSUP = 0.004;
//                MINCONF = 0.1d;
//                MINLIFT = 1.5d;
//
//                TIME_NUM = 8;
//                ATTRI_MAPPING_PATH = projectPath + "/dataset/synthetic/t9_v2723_e4_a43/attrMap.txt";
//                ATTR_FILE_PATH = projectPath + "/dataset/synthetic/t9_v2723_e4_a43/attributes.txt";
//                EDGE_FILE_PATH = projectPath + "/dataset/synthetic/t9_v2723_e4_a43/edges.txt";
//                VERTEX_MAP_NAME_PATH = projectPath + "/dataset/synthetic/t9_v2723_e4_a43/vertices_mapping.txt";
//                break;
//            }
//
//            case 3: {
//                VERTEXNUM = 280;
//                TOTAL_NUM_ATTR = 8;
//                MINSUP = 0.004;
//                MINCONF = 0.3d;
//                MINLIFT = 1.2d;
//
//                TIME_NUM = 7;
//                ATTRI_MAPPING_PATH = projectPath + "/dataset/synthetic/t8_v280_e4_a8/attrMap.txt";
//                ATTR_FILE_PATH = projectPath + "/dataset/synthetic/t8_v280_e4_a8/attributes.txt";
//                EDGE_FILE_PATH = projectPath + "/dataset/synthetic/t8_v280_e4_a8/vIdEdges.txt";
//                VERTEX_MAP_NAME_PATH = projectPath + "/dataset/synthetic/t8_v280_e4_a8/vertices_mapping.txt";
//
//                break;
//            }
//        }
//    }
}
