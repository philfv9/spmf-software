package ca.pfv.spmf.algorithms.graph_mining.closegraph;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import ca.pfv.spmf.algorithms.graph_mining.tkg.DFSCode;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Edge;
import ca.pfv.spmf.algorithms.graph_mining.tkg.ExtendedEdge;
import ca.pfv.spmf.algorithms.graph_mining.tkg.FrequentSubgraph;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Graph;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Vertex;
import ca.pfv.spmf.algorithms.graph_mining.tkg.VizGraph;

/**
 * Helper class used by {@link AlgoCloseGraph} gathering all the file I/O:
 * reading the input graph database, writing the closed frequent subgraphs
 * found to the output file, and generating the (optional) GraphViz DOT
 * output.
 *
 * Extracted out of AlgoCloseGraph simply to keep the code split into smaller,
 * easier-to-navigate files; the logic itself is unchanged.
 */
class CloseGraphIO {

	/** larger I/O buffer to reduce the number of underlying read/write syscalls */
	private static final int IO_BUFFER_SIZE = 1 << 16; // 64 KB

	/** initial StringBuilder capacity used when writing pattern lines */
	private static final int INITIAL_STRINGBUILDER_CAPACITY = 256;

	private CloseGraphIO() {
		// utility class, not meant to be instantiated
	}

	static void outputDotFile(String outputPath) throws IOException {
		String dirName = outputPath + "_dotfile";
		File dir = new File(dirName);
		if (!dir.exists())
			dir.mkdir();
		VizGraph.visulizeFromFile(outputPath, dirName);
	}

	// =====================================================================
	// I/O
	// =====================================================================

	static List<Graph> readGraphs(String path) throws IOException {
	    // bottleneck fix: explicit larger buffer size reduces the number of
	    // underlying read() calls when parsing large graph database files
	    BufferedReader br = new BufferedReader(new FileReader(new File(path)), IO_BUFFER_SIZE);
	    List<Graph> graphDatabase = new ArrayList<>();

	    // skip any header/comment lines starting with '@' before looking for the first graph
	    String line;
	    do {
	        line = br.readLine();
	    } while (line != null && line.startsWith("@"));

	    Boolean hasNextGraph = (line != null) && line.startsWith("t");

	    while (hasNextGraph) {
	        hasNextGraph = false;
	        // bottleneck fix: StringTokenizer avoids the String[] array allocation
	        // (and the "special-cased-but-still-not-free" internal work) that
	        // String.split(" ") performs on every call.
	        StringTokenizer headerTok = new StringTokenizer(line, " ");
	        headerTok.nextToken(); // "t"
	        headerTok.nextToken(); // "#"
	        int gId = Integer.parseInt(headerTok.nextToken());
	        Map<Integer, Vertex> vMap = new HashMap<>();
	        while ((line = br.readLine()) != null && !line.startsWith("t")) {
	            if (line.isEmpty() || line.charAt(0) == '@') {
	                continue;
	            }
	            if (line.charAt(0) == 'v') {
	                StringTokenizer tok = new StringTokenizer(line, " ");
	                tok.nextToken(); // "v"
	                int vId = Integer.parseInt(tok.nextToken());
	                int vLabel = Integer.parseInt(tok.nextToken());
	                vMap.put(vId, new Vertex(vId, vLabel));
	            } else if (line.charAt(0) == 'e') {
	                StringTokenizer tok = new StringTokenizer(line, " ");
	                tok.nextToken(); // "e"
	                int v1 = Integer.parseInt(tok.nextToken());
	                int v2 = Integer.parseInt(tok.nextToken());
	                int eLabel = Integer.parseInt(tok.nextToken());
	                Edge e = new Edge(v1, v2, eLabel);
	                vMap.get(v1).addEdge(e);
	                vMap.get(v2).addEdge(e);
	            }
	        }
	        graphDatabase.add(new Graph(gId, vMap));
	        if (line != null) {
	            hasNextGraph = true;
	        }
	    }
	    br.close();

	    return graphDatabase;
	}

	static void writeResultToFile(String outputPath, List<FrequentSubgraph> frequentSubgraphs, boolean outputGraphIds)
			throws IOException {
		// bottleneck fix: explicit larger buffer size reduces the number of
		// underlying write() calls when producing large result files
		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(outputPath)), IO_BUFFER_SIZE);
		bw.write("@FILETYPE=\"Frequent subgraphs\"");
		bw.append(System.lineSeparator());
		bw.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		bw.append(System.lineSeparator());
		
		// bottleneck fix: reuse a single StringBuilder across all patterns instead
		// of allocating a new one for each pattern, reducing GC pressure when
		// there are many frequent subgraphs. An initial capacity avoids repeated
		// internal buffer growth/copy for typical pattern sizes.
		StringBuilder sb = new StringBuilder(INITIAL_STRINGBUILDER_CAPACITY);

		int i = 0;
		for (FrequentSubgraph subgraph : frequentSubgraphs) {
			sb.setLength(0);

			DFSCode dfsCode = subgraph.dfsCode;
			sb.append("t # ").append(i).append(" * ").append(subgraph.support).append(System.lineSeparator());
			if (dfsCode.size() == 1) {
				ExtendedEdge ee = dfsCode.getEeL().get(0);
				if (ee.getEdgeLabel() == -1) {
					sb.append("v 0 ").append(ee.getvLabel1()).append(System.lineSeparator());
				} else {
					sb.append("v 0 ").append(ee.getvLabel1()).append(System.lineSeparator());
					sb.append("v 1 ").append(ee.getvLabel2()).append(System.lineSeparator());
					sb.append("e 0 1 ").append(ee.getEdgeLabel()).append(System.lineSeparator());
				}
			} else {
				List<Integer> vLabels = dfsCode.getAllVLabels();
				for (int j = 0; j < vLabels.size(); j++) {
					sb.append("v ").append(j).append(" ").append(vLabels.get(j)).append(System.lineSeparator());
				}
				for (ExtendedEdge ee : dfsCode.getEeL()) {
					sb.append("e ").append(ee.getV1()).append(" ").append(ee.getV2()).append(" ").append(ee.edgeLabel)
							.append(System.lineSeparator());
				}
			}
			if (outputGraphIds) {
				sb.append("x");
				for (int id : subgraph.setOfGraphsIDs) {
					sb.append(" ").append(id);
				}
			}
			sb.append(System.lineSeparator()).append(System.lineSeparator());
			bw.write(sb.toString());
			i++;
		}
		bw.close();
	}
}