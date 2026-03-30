package ca.pfv.spmf.algorithms.graph_mining.tkg;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GSPAN2GraphMLConverter {

    public List<Graph> readCGSPANGraphs(String path) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(new File(path)));
        List<Graph> graphDatabase = new ArrayList<>();

        String line = br.readLine();
        Boolean hasNextGraph = (line != null) && line.startsWith("t");

        // For each graph of the graph database
        while (hasNextGraph) {
            hasNextGraph = false;
            int gId = Integer.parseInt(line.split(" ")[2]);
            Map<Integer, Vertex> vMap = new HashMap<>();
            while ((line = br.readLine()) != null && !line.startsWith("t")) {

                String[] items = line.split(" ");

                if (line.startsWith("v")) {
                    // If it is a vertex
                    int vId = Integer.parseInt(items[1]);
                    int vLabel = Integer.parseInt(items[2]);
                    vMap.put(vId, new Vertex(vId, vLabel));
                } else if (line.startsWith("e")) {
                    // If it is an edge
                    int v1 = Integer.parseInt(items[1]);
                    int v2 = Integer.parseInt(items[2]);
                    int eLabel = Integer.parseInt(items[3]);
                    Edge e = new Edge(v1, v2, eLabel);
//                    System.out.println(v1 + " " + v2 + " " + vMap.get(v1).id + " " + vMap.get(v2).id);
                    vMap.get(v1).addEdge(e);
                    vMap.get(v2).addEdge(e);
                }
            }

            Graph graph = new Graph(gId, vMap);
            graph.precalculateVertexList();
            graphDatabase.add(graph);
            if (line != null) {
                hasNextGraph = true;
            }
        }

        br.close();
        return graphDatabase;
    }

    public void writeGraphMLGraphs(String path, List<Graph> graphDatabase) throws IOException, TransformerConfigurationException, SAXException {
        FileWriter writer = new FileWriter(path);
        SAXTransformerFactory factory =
                (SAXTransformerFactory) SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.setResult(new StreamResult(new PrintWriter(writer)));

        handler.startDocument();

        handler.startPrefixMapping("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        handler.endPrefixMapping("xsi");

        AttributesImpl headerAttribute = new AttributesImpl();
        headerAttribute.addAttribute("", "", "xsi:schemaLocation", "CDATA",
                        "http://graphml.graphdrawing.org/xmlns http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd");
        handler.startElement("http://graphml.graphdrawing.org/xmlns", "", "graphml", headerAttribute);


        AttributesImpl vertexLabelAttribute  = new AttributesImpl();
        vertexLabelAttribute.addAttribute("", "", "id", "CDATA", "vertex_label");
        vertexLabelAttribute.addAttribute("", "", "for", "CDATA", "node");
        vertexLabelAttribute.addAttribute("", "", "attr.name", "CDATA", "vertex_label");
        vertexLabelAttribute.addAttribute("", "", "attr.type", "CDATA", "int");
        handler.startElement("", "", "key", vertexLabelAttribute);
        handler.endElement("", "", "key");

        AttributesImpl edgeLabelAttribute  = new AttributesImpl();
        edgeLabelAttribute.addAttribute("", "", "id", "CDATA", "edge_label");
        edgeLabelAttribute.addAttribute("", "", "for", "CDATA", "edge");
        edgeLabelAttribute.addAttribute("", "", "attr.name", "CDATA", "edge_label");
        edgeLabelAttribute.addAttribute("", "", "attr.type", "CDATA", "int");
        handler.startElement("", "", "key", edgeLabelAttribute);
        handler.endElement("", "", "key");

        for (Graph graph: graphDatabase) {
            AttributesImpl graphAttribute = new AttributesImpl();
            graphAttribute.addAttribute("", "", "edgedefault", "CDATA", "undirected");
            handler.startElement("", "", "graph", graphAttribute);

            for (Vertex vertex: graph.vertices) {
                AttributesImpl vertexAttribute = new AttributesImpl();
                vertexAttribute.addAttribute("", "", "id", "CDATA", String.valueOf(vertex.getId()));
                handler.startElement("", "", "node", vertexAttribute);

                AttributesImpl labelAttribute = new AttributesImpl();
                labelAttribute.addAttribute("", "", "key", "CDATA", "vertex_label");
                handler.startElement("", "", "data", labelAttribute);
                handler.characters(String.valueOf(vertex.getLabel()).toCharArray(), 0, String.valueOf(vertex.getLabel()).length());
                handler.endElement("", "", "data");

                handler.endElement("", "", "node");
            }

            for (Edge edge: graph.getAllEdges()) {
                AttributesImpl edgeAttribute = new AttributesImpl();
                edgeAttribute.addAttribute("", "", "source", "CDATA", String.valueOf(graph.vMap.get(edge.v1).getId()));
                edgeAttribute.addAttribute("", "", "target", "CDATA", String.valueOf(graph.vMap.get(edge.v2).getId()));

                handler.startElement("", "", "edge", edgeAttribute);

                AttributesImpl labelAttribute = new AttributesImpl();
                labelAttribute.addAttribute("", "", "key", "CDATA", "edge_label");
                handler.startElement("", "", "data", labelAttribute);
                handler.characters(String.valueOf(edge.getEdgeLabel()).toCharArray(), 0, String.valueOf(edge.getEdgeLabel()).length());
                handler.endElement("", "", "data");

                handler.endElement("", "", "edge");
            }

            handler.endElement("", "", "graph");
        }

        handler.endElement("", "", "graphml");

        handler.endDocument();

        writer.flush();

        writer.close();
    }

    public static void main(String[] args) throws IOException, TransformerConfigurationException, SAXException {
        GSPAN2GraphMLConverter gspan2GraphMLConverter = new GSPAN2GraphMLConverter();

        String inputPath = "./ca/pfv/spmf/test/mico.lg.txt"  ;
        String outputPath = "./ca/pfv/spmf/test/mico.lg.graphml";
        List<Graph> graphsDatabase = gspan2GraphMLConverter.readCGSPANGraphs(inputPath);
        gspan2GraphMLConverter.writeGraphMLGraphs(outputPath, graphsDatabase);
    }
}
