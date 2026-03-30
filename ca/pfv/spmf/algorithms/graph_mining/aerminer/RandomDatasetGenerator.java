package ca.pfv.spmf.algorithms.graph_mining.aerminer;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
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
 * A random dataset generator
 * @see AERMiner
 * @author Ganghuan He 2020
 */
public class RandomDatasetGenerator {
    private static int noTimestamps = 0;
    private static int noVertex = 0;
    private static int noEdges = 0;
    private static int noAttribute = 0;


    private static String rootPath = ParametersSettingAERMiner.projectPath+"/dataset/synthetic/";
    private static String attrMapFileName = "/attrMap.txt";
    private static String edgesFileName = "/edges.txt";
    private static String attributesFileName = "/attributes.txt";

    private static String attrMapFilePath;
    private static String edgesFilePath;
    private static String attributesFilePath;

    private static String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static String numbers = "0123456789";



    public static void main(String[] args) throws IOException {
        noTimestamps = 8;
        noVertex = 280;
        noEdges = 4;
        noAttribute = 8;
        generateDirectoryAndFiles();
        generateAttributeMapping();
        generateAttributes();
        generateEdges();
    }

    private static void generateAttributeMapping() throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(attrMapFilePath));
        int count = 0;
        for (int i = 0; i < numbers.length() && count < noAttribute; i++) {
            for (int j = 0; j < letters.length() && count < noAttribute; j++) {
                count++;
                String attribute = "" + letters.charAt(j) + numbers.charAt(i);
                bw.write(attribute + "\n");
            }
        }
        bw.close();
    }
    private static void generateEdges() throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(edgesFilePath));
        for (int t = 0; t < noTimestamps; t++) {
            StringBuilder sb = new StringBuilder();
            sb.append("T").append(t).append("\n");
            for (int v = 0; v < noVertex; v++) {
                sb.append(v);
                int noEdge = gaussianNumber(noEdges, 6);
                Set<Integer> edgeList = new HashSet<>();
                while (edgeList.size() < noEdge) {
                    int anotherV = new Random().nextInt(noVertex);
                    if (anotherV != v)
                        edgeList.add(anotherV);
                }
                for (int anotherV : edgeList) {
                    sb.append(" ").append(anotherV);
                }
                sb.append("\n");
            }
            bw.write(sb.toString());
        }
        bw.close();
    }


    private static void generateAttributes() throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(attributesFilePath));
        for (int t = 0; t < noTimestamps; t++) {
            StringBuilder sb = new StringBuilder();
            sb.append("T").append(t).append("\n");
            for (int v = 0; v < noVertex; v++) {
                sb.append(v);
                for (int a = 0; a < noAttribute; a++) {
                    sb.append(" ").append(gaussianNumber(0,1));
                }
                sb.append("\n");
            }
            bw.write(sb.toString());
        }
        bw.close();
    }

    private static int gaussianNumber(double mean, double stdV) {
        int num = (int) Math.floor(mean + stdV * new Random().nextGaussian());
        return num >= 0? num: -num;
    }

    private static void generateDirectoryAndFiles() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("t").append(noTimestamps).append("_v").append(noVertex).append("_e").append(noEdges)
                .append("_a").append(noAttribute);
        String dirName = sb.toString();
        String dirPath = rootPath + dirName;
        File dirFile = new File(dirPath);
        dirFile.mkdir();
        List<File> files = new LinkedList<>();

        attrMapFilePath = dirPath + attrMapFileName;
        edgesFilePath = dirPath + edgesFileName;
        attributesFilePath = dirPath + attributesFileName;
        files.add(new File(attrMapFilePath));
        files.add(new File(edgesFilePath));
        files.add(new File(attributesFilePath));

        for (File file : files) {
            file.createNewFile();
        }
    }
}
