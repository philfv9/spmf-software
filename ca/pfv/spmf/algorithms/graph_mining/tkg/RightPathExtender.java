package ca.pfv.spmf.algorithms.graph_mining.tkg;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

/* This file is copyright (c) 2022 by Shaul Zevin
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
 * This is implementation of the DFS code right path extension.
 * Right path extension is executed in parallel by multiple instances of this class.
 *  <br/><br/>
 *
 * The cgspan algorithm is described in : <br/>
 * <br/>
 * <p>
 * cgSpan: Closed Graph-Based Substructure Pattern Mining, by Zevin Shaul, Sheikh Naaz
 * IEEE BigData 2021 7th Special Session on Intelligent Data Mining
 * <p>
 *
 * <br/>
 *
 * The CGspan algorithm finds all the closed subgraphs and their support in a
 * graph provided by the user.
 * <br/><br/>
 *
 * This implementation saves the result to a file
 *
 * @see ProjectedCompact
 * @author Shaul Zevin
 */
public class RightPathExtender implements Callable<Integer> {
    // number of projections that could be extended with extended edge
    private Map<ExtendedEdge, Integer> extensionPDFSCounter;
    // projections iterator
    private ProjectedIteratorConsumer iterator;
    // activation class
    private AlgoCGSPANAbstract algoCGSPANAbstractCompact;
    // DFS code to be right extended
    private DFSCode c;
    // graphs database
    private List<DatabaseGraph> graphDatabase;
    // global right extensions
    private Map<ExtendedEdge, ProjectedCompact> extensions;
    // right extensions produced by this thread
    private Map<ExtendedEdge, ProjectedCompact> extensionsLocal;
    // number of projections that could be extended with extended edge by this thread
    private Map<ExtendedEdge, Integer> extensionPDFSCounterLocal;

    public RightPathExtender(Map<ExtendedEdge, Integer> extensionPDFSCounter, ProjectedIteratorConsumer iterator, AlgoCGSPANAbstract algoCGSPANAbstractCompact, DFSCode c, List<DatabaseGraph> graphDatabase, Map<ExtendedEdge, ProjectedCompact> extensions) {
        this.extensionPDFSCounter = extensionPDFSCounter;
        this.iterator = iterator;
        this.algoCGSPANAbstractCompact = algoCGSPANAbstractCompact;
        this.c = c;
        this.graphDatabase = graphDatabase;
        this.extensions = extensions;
        this.extensionsLocal = new HashMap<ExtendedEdge, ProjectedCompact>();
        this.extensionPDFSCounterLocal = new HashMap<ExtendedEdge, Integer>();
    }

    @Override
    public Integer call() throws Exception {
        int myCount = 0;

        int rightMost = c.getRightMost();

        //long start = System.currentTimeMillis();

        // iterate on projections to be extended
        while (true) {
            PDFSCompact pdfs;
            synchronized (iterator) {
                if (!iterator.hasNext()) {
                    break;
                }
                pdfs = iterator.next();
            }

            DatabaseGraph g = pdfs.getDatabaseGraph();

            if (algoCGSPANAbstractCompact.EDGE_COUNT_PRUNING && c.size() >= g.getEdgeCount()) {
                synchronized (algoCGSPANAbstractCompact.pruneByEdgeCountCount) {
                    algoCGSPANAbstractCompact.pruneByEdgeCountCount++;
                }
                continue;
            }

            Map<Integer, Integer> isom = pdfs.subgraphIsomorphism(c);

            // backward extensions from rightmost child
            if (algoCGSPANAbstractCompact.pdfsAutomorphismOptimization) {
                rightMostPathBackwardExtensionsAutomorphism(c, rightMost, graphDatabase, g, pdfs, isom);
            } else {
                rightMostPathBackwardExtensions(c, rightMost, graphDatabase, g, pdfs, isom);
            }

            // forward extensions from nodes on rightmost path
            if (algoCGSPANAbstractCompact.pdfsAutomorphismOptimization) {
                rightMostPathForwardExtensionsAutomorphism(c, rightMost, graphDatabase, g, pdfs, isom);
            } else {
                rightMostPathForwardExtensions(c, rightMost, graphDatabase, g, pdfs, isom);
            }
        }

        // merge local extensions with global
        synchronized(extensions) {
            for (ExtendedEdge extendedEdge: extensionsLocal.keySet()) {
                if (!extensions.containsKey(extendedEdge)) {
                    extensions.put(extendedEdge, extensionsLocal.get(extendedEdge));
                }
                else {
                    extensions.get(extendedEdge).addProjection(extensionsLocal.get(extendedEdge));
                }
            }
        }

        // merge local extensions count with global
        synchronized (extensionPDFSCounter) {
            for (ExtendedEdge extendedEdge: extensionPDFSCounterLocal.keySet()) {
                if (!extensionPDFSCounter.containsKey(extendedEdge)) {
                    extensionPDFSCounter.put(extendedEdge, extensionPDFSCounterLocal.get(extendedEdge));
                }
                else {
                    extensionPDFSCounter.put(extendedEdge, extensionPDFSCounter.get(extendedEdge) + extensionPDFSCounterLocal.get(extendedEdge));
                }
            }
        }

        return myCount;
    }

    protected void rightMostPathBackwardExtensions(DFSCode c, int rightMost, List<DatabaseGraph> graphDatabase, DatabaseGraph g, PDFSCompact pdfs, Map<Integer, Integer> isom) throws IOException, ClassNotFoundException {
        Map<Integer, Integer> invertedISOM = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : isom.entrySet()) {
            invertedISOM.put(entry.getValue(), entry.getKey());
        }
        int mappedRM = isom.get(rightMost);
        int mappedRMlabel = g.getVLabel(mappedRM);
        for (Vertex x : g.getAllNeighbors(mappedRM)) {
            Integer invertedX = invertedISOM.get(x.getId());
            if (invertedX != null && c.onRightMostPath(invertedX) && c.notPreOfRM(invertedX)
                    && !c.containEdge(rightMost, invertedX)) {
                // rightmost and invertedX both have correspondings in g, so label of vertices
                // and edge all
                // can be found by correspondings
                ExtendedEdge ee = new ExtendedEdge(rightMost, invertedX, mappedRMlabel, x.getLabel(),
                        g.getEdgeLabel(mappedRM, x.getId()));

                ProjectedCompact extensionProjected;
                extensionProjected = extensionsLocal.get(ee);
                if (extensionProjected == null) {
                    DFSCode newC = c.copy();
                    newC.add(ee);
                    extensionProjected = new ProjectedCompact(newC, graphDatabase);
                    extensionProjected.setGraphIds(new HashSet<>());
                    extensionsLocal.put(ee, extensionProjected);
                    extensionPDFSCounterLocal.put(ee, 0);
                }

                extensionPDFSCounterLocal.put(ee, extensionPDFSCounterLocal.get(ee) + 1);

                Edge e = g.getEdge(mappedRM, x.getId());
                EdgeEnumeration edgeEnumeration = g.getEdgeEnumeration(e);
                boolean isReversed = e.v1 != mappedRM;
                ProjectedEdge projectedEdge = ProjectedEdge.get(edgeEnumeration, isReversed);

                extensionProjected.addProjection(pdfs, projectedEdge);

            }
        }
    }

    protected void rightMostPathBackwardExtensionsAutomorphism(DFSCode c, int rightMost, List<DatabaseGraph> graphDatabase, DatabaseGraph g, PDFSCompact pdfs, Map<Integer, Integer> isom) throws IOException, ClassNotFoundException {
        IsomorphicSubgraphProjections projections = (IsomorphicSubgraphProjections) pdfs;
        Map<Integer, Integer> invertedISOM = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : isom.entrySet()) {
            invertedISOM.put(entry.getValue(), entry.getKey());
        }
        // rightmost vertex does not belong to isomorphic subgraph on the projection
        if (projections.getRightMostVertexVertexRightMostPathIndexProjections().size() == 0) {
            if (projections.containsRightmost(rightMost)) {
                return;
            }
            int mappedRM = isom.get(rightMost);
            int mappedRMlabel = g.getVLabel(mappedRM);
            for (Vertex x : g.getAllNeighbors(mappedRM)) {
                // 'to' vertex of the backward edge does not belong to isomorphic graph
                if (!projections.getVertexRightMostPathIndexProjections().containsKey(x.getId())) {
                    Integer invertedX = invertedISOM.get(x.getId());
                    if (invertedX != null && c.onRightMostPath(invertedX) && c.notPreOfRM(invertedX)
                            && !c.containEdge(rightMost, invertedX)) {
                        // rightmost and invertedX both have correspondings in g, so label of vertices
                        // and edge all
                        // can be found by correspondings
                        ExtendedEdge ee = new ExtendedEdge(rightMost, invertedX, mappedRMlabel, x.getLabel(),
                                g.getEdgeLabel(mappedRM, x.getId()));

                        ProjectedCompact extensionProjected;
                        extensionProjected = extensionsLocal.get(ee);
                        if (extensionProjected == null) {
                            DFSCode newC = c.copy();
                            newC.add(ee);
                            extensionProjected = new ProjectedCompact(newC, graphDatabase);
                            extensionProjected.setGraphIds(new HashSet<>());
                            extensionsLocal.put(ee, extensionProjected);
                            extensionPDFSCounterLocal.put(ee, 0);
                        }

                        extensionPDFSCounterLocal.put(ee, extensionPDFSCounterLocal.get(ee) + projections.getNumProjections());

                        Edge e = g.getEdge(mappedRM, x.getId());
                        EdgeEnumeration edgeEnumeration = g.getEdgeEnumeration(e);
                        boolean isReversed = e.v1 != mappedRM;
                        ProjectedEdge projectedEdge = ProjectedEdge.get(edgeEnumeration, isReversed);
                        extensionProjected.addProjection(projections.getProjections(), projections.getNumProjections(), projectedEdge);
                    }
                } else {
                    // 'to' vertex of the backward edge belongs to isomorphic graph on the projection
                    for (int invertedX : projections.getVertexRightMostPathIndexProjections().get(x.getId()).keySet()) {
                        if (c.onRightMostPath(invertedX) && c.notPreOfRM(invertedX)
                                && !c.containEdge(rightMost, invertedX)) {
                            // rightmost and invertedX both have correspondings in g, so label of vertices
                            // and edge all
                            // can be found by correspondings
                            ExtendedEdge ee = new ExtendedEdge(rightMost, invertedX, mappedRMlabel, x.getLabel(),
                                    g.getEdgeLabel(mappedRM, x.getId()));

                            ProjectedCompact extensionProjected;
                            extensionProjected = extensionsLocal.get(ee);
                            if (extensionProjected == null) {
                                DFSCode newC = c.copy();
                                newC.add(ee);
                                extensionProjected = new ProjectedCompact(newC, graphDatabase);
                                extensionProjected.setGraphIds(new HashSet<>());
                                extensionsLocal.put(ee, extensionProjected);
                                extensionPDFSCounterLocal.put(ee, 0);
                            }

                            extensionPDFSCounterLocal.put(ee, extensionPDFSCounterLocal.get(ee) + projections.getVertexRightMostPathIndexProjectionsCount().get(x.getId()).get(invertedX));

                            Edge e = g.getEdge(mappedRM, x.getId());
                            EdgeEnumeration edgeEnumeration = g.getEdgeEnumeration(e);
                            boolean isReversed = e.v1 != mappedRM;
                            ProjectedEdge projectedEdge = ProjectedEdge.get(edgeEnumeration, isReversed);
                            extensionProjected.addProjection(projections.getVertexRightMostPathIndexProjections().get(x.getId()).get(invertedX), projections.getVertexRightMostPathIndexProjectionsCount().get(x.getId()).get(invertedX), projectedEdge);
                        }
                    }
                }
            }
        } else {
            // rightmost vertex belongs to isomorphic subgraph on the projection
            for (int mappedRM : projections.getRightMostVertexVertexRightMostPathIndexProjections().keySet()) {
                int mappedRMlabel = g.getVLabel(mappedRM);
                for (Vertex x : g.getAllNeighbors(mappedRM)) {
                    // 'to' vertex of the backward edge does not belong to isomorphic subgraph on the projection
                    if (!projections.getRightMostVertexVertexRightMostPathIndexProjections().get(mappedRM).containsKey(x.getId())) {
                        // if x belongs to isomorphism, then skip
                        if (projections.getVertexRightMostPathIndexProjections().containsKey(x.getId())) {
                            continue;
                        }
                        Integer invertedX = invertedISOM.get(x.getId());
                        if (invertedX != null && c.onRightMostPath(invertedX) && c.notPreOfRM(invertedX)
                                && !c.containEdge(rightMost, invertedX)) {
                            // rightmost and invertedX both have correspondings in g, so label of vertices
                            // and edge all
                            // can be found by correspondings
                            ExtendedEdge ee = new ExtendedEdge(rightMost, invertedX, mappedRMlabel, x.getLabel(),
                                    g.getEdgeLabel(mappedRM, x.getId()));

                            ProjectedCompact extensionProjected;
                            extensionProjected = extensionsLocal.get(ee);
                            if (extensionProjected == null) {
                                DFSCode newC = c.copy();
                                newC.add(ee);
                                extensionProjected = new ProjectedCompact(newC, graphDatabase);
                                extensionProjected.setGraphIds(new HashSet<>());
                                extensionsLocal.put(ee, extensionProjected);
                                extensionPDFSCounterLocal.put(ee, 0);
                            }


                            extensionPDFSCounterLocal.put(ee, extensionPDFSCounterLocal.get(ee) + projections.getVertexRightMostPathIndexProjectionsCount().get(mappedRM).get(rightMost));

                            Edge e = g.getEdge(mappedRM, x.getId());
                            EdgeEnumeration edgeEnumeration = g.getEdgeEnumeration(e);
                            boolean isReversed = e.v1 != mappedRM;
                            ProjectedEdge projectedEdge = ProjectedEdge.get(edgeEnumeration, isReversed);
                            extensionProjected.addProjection(projections.getVertexRightMostPathIndexProjections().get(mappedRM).get(rightMost), projections.getVertexRightMostPathIndexProjectionsCount().get(mappedRM).get(rightMost), projectedEdge);
                        }
                    } else {
                        // 'to' vertex of the backward edge belongs to isomorphic subgraph on the projection
                        for (int invertedX : projections.getRightMostVertexVertexRightMostPathIndexProjections().get(mappedRM).get(x.getId()).keySet()) {
                            if (c.onRightMostPath(invertedX) && c.notPreOfRM(invertedX)
                                    && !c.containEdge(rightMost, invertedX)) {
                                // rightmost and invertedX both have correspondings in g, so label of vertices
                                // and edge all
                                // can be found by correspondings
                                ExtendedEdge ee = new ExtendedEdge(rightMost, invertedX, mappedRMlabel, x.getLabel(),
                                        g.getEdgeLabel(mappedRM, x.getId()));

                                ProjectedCompact extensionProjected;
                                extensionProjected = extensionsLocal.get(ee);
                                if (extensionProjected == null) {
                                    DFSCode newC = c.copy();
                                    newC.add(ee);
                                    extensionProjected = new ProjectedCompact(newC, graphDatabase);
                                    extensionProjected.setGraphIds(new HashSet<>());
                                    extensionsLocal.put(ee, extensionProjected);
                                    extensionPDFSCounterLocal.put(ee, 0);
                                }


                                extensionPDFSCounterLocal.put(ee, extensionPDFSCounterLocal.get(ee) + projections.getRightMostVertexVertexRightMostPathIndexProjectionsCount().get(mappedRM).get(x.getId()).get(invertedX));

                                Edge e = g.getEdge(mappedRM, x.getId());
                                EdgeEnumeration edgeEnumeration = g.getEdgeEnumeration(e);
                                boolean isReversed = e.v1 != mappedRM;
                                ProjectedEdge projectedEdge = ProjectedEdge.get(edgeEnumeration, isReversed);
                                extensionProjected.addProjection(projections.getRightMostVertexVertexRightMostPathIndexProjections().get(mappedRM).get(x.getId()).get(invertedX), projections.getRightMostVertexVertexRightMostPathIndexProjectionsCount().get(mappedRM).get(x.getId()).get(invertedX), projectedEdge);
                            }
                        }
                    }
                }
            }
        }
    }

    protected void rightMostPathForwardExtensions(DFSCode c, int rightMost, List<DatabaseGraph> graphDatabase, DatabaseGraph g, PDFSCompact pdfs, Map<Integer, Integer> isom) throws IOException, ClassNotFoundException {

        Collection<Integer> mappedVertices = isom.values();
        for (int v : c.getRightMostPath()) {
            Set<ExtendedEdge> countedExtensions = new HashSet<ExtendedEdge>();

            int mappedV = isom.get(v);
            int mappedVlabel = g.getVLabel(mappedV);
            for (Edge edge : g.vMap.get(mappedV).getEdgeList()) {
                int x = edge.v1 == mappedV ? edge.v2 : edge.v1;
                int xLabel = g.vMap.get(x).getLabel();
                if (!mappedVertices.contains(x)) {
                    addForwardExtensionProjection(c, v, rightMost, graphDatabase, g, pdfs, mappedV, mappedVlabel, xLabel, edge, countedExtensions);
                }
            }
        }
    }

    private void addForwardExtensionProjection(DFSCode c, int v, int rightMost, List<DatabaseGraph> graphDatabase, DatabaseGraph g, PDFSCompact pdfs, int mappedV, int mappedVlabel, int xLabel, Edge e, Set<ExtendedEdge> countedExtensions) throws IOException, ClassNotFoundException {
        ExtendedEdge ee = new ExtendedEdge(v, rightMost + 1, mappedVlabel, xLabel,
                e.getEdgeLabel());


        ProjectedCompact extensionProjected;
        extensionProjected = extensionsLocal.get(ee);
        if (extensionProjected == null) {
            DFSCode newC = c.copy();
            newC.add(ee);
            extensionProjected = new ProjectedCompact(newC, graphDatabase);
            extensionProjected.setGraphIds(new HashSet<>());
            extensionsLocal.put(ee, extensionProjected);

            extensionPDFSCounterLocal.put(ee, 0);
        }

        EdgeEnumeration edgeEnumeration = g.getEdgeEnumeration(e);
        boolean isReversed = e.v1 != mappedV;
        ProjectedEdge projectedEdge = ProjectedEdge.get(edgeEnumeration, isReversed);
        if (!countedExtensions.contains(ee)) {
            extensionProjected.addProjection(pdfs, projectedEdge);
            extensionPDFSCounterLocal.put(ee, extensionPDFSCounterLocal.get(ee) + 1);
            countedExtensions.add(ee);
        } else {
            extensionProjected.addProjection(projectedEdge, pdfs.getProjectedEdges().size());
        }
    }

    protected void rightMostPathForwardExtensionsAutomorphism(DFSCode c, int rightMost, List<DatabaseGraph> graphDatabase, DatabaseGraph g, PDFSCompact pdfs, Map<Integer, Integer> isom) throws IOException, ClassNotFoundException {

        IsomorphicSubgraphProjections projections = (IsomorphicSubgraphProjections) pdfs;

        Collection<Integer> mappedVertices = isom.values();

        // rightmost vertex does not belong to isomorphic subgraph on the projection
        for (int v : c.getRightMostPath()) {
            Set<ExtendedEdge> countedExtensions = new HashSet<ExtendedEdge>();

            int mappedV = isom.get(v);
            if (projections.getVertexRightMostPathIndexProjections().containsKey(mappedV)) {
                continue;
            }
            int mappedVlabel = g.getVLabel(mappedV);
            for (Edge edge : g.vMap.get(mappedV).getEdgeList()) {
                int x = edge.v1 == mappedV ? edge.v2 : edge.v1;
                int xLabel = g.vMap.get(x).getLabel();
                if (!mappedVertices.contains(x)) {
                    addForwardExtensionProjectionAutomorphism(c, v, rightMost, graphDatabase, g, projections.getProjections(), projections.getNumProjections(), mappedV, mappedVlabel, xLabel, edge, countedExtensions);
                }
            }
        }

        // rightmost vertex belongs to isomorphic subgraph on the projection
        for (int mappedV : projections.getVertexRightMostPathIndexProjections().keySet()) {
            Map<Integer, Set<ExtendedEdge>> countedExtensions = new HashMap<Integer, Set<ExtendedEdge>>();
            int mappedVlabel = g.getVLabel(mappedV);
            for (Edge edge : g.vMap.get(mappedV).getEdgeList()) {
                int x = edge.v1 == mappedV ? edge.v2 : edge.v1;
                int xLabel = g.vMap.get(x).getLabel();
                if (!mappedVertices.contains(x)) {
                    for (int v : projections.getVertexRightMostPathIndexProjections().get(mappedV).keySet()) {
                        if (!countedExtensions.keySet().contains(v)) {
                            countedExtensions.put(v, new HashSet<ExtendedEdge>());
                        }
                        addForwardExtensionProjectionAutomorphism(c, v, rightMost, graphDatabase, g, projections.getVertexRightMostPathIndexProjections().get(mappedV).get(v), projections.getVertexRightMostPathIndexProjectionsCount().get(mappedV).get(v), mappedV, mappedVlabel, xLabel, edge, countedExtensions.get(v));
                    }
                }
            }
        }
    }

    private void addForwardExtensionProjectionAutomorphism(DFSCode c, int v, int rightMost, List<DatabaseGraph> graphDatabase, DatabaseGraph g, List<Set<ProjectedEdge>> projections, int numProjections, int mappedV, int mappedVlabel, int xLabel, Edge e, Set<ExtendedEdge> countedExtensions) throws IOException, ClassNotFoundException {
        ExtendedEdge ee = new ExtendedEdge(v, rightMost + 1, mappedVlabel, xLabel,
                e.getEdgeLabel());


        ProjectedCompact extensionProjected;
        extensionProjected = extensionsLocal.get(ee);
        if (extensionProjected == null) {
            DFSCode newC = c.copy();
            newC.add(ee);
            extensionProjected = new ProjectedCompact(newC, graphDatabase);
            extensionProjected.setGraphIds(new HashSet<>());
            extensionsLocal.put(ee, extensionProjected);

            extensionPDFSCounterLocal.put(ee, 0);
        }

        EdgeEnumeration edgeEnumeration = g.getEdgeEnumeration(e);
        boolean isReversed = e.v1 != mappedV;
        ProjectedEdge projectedEdge = ProjectedEdge.get(edgeEnumeration, isReversed);
        if (!countedExtensions.contains(ee)) {
            extensionProjected.addProjection(projections, numProjections, projectedEdge);
            extensionPDFSCounterLocal.put(ee, extensionPDFSCounterLocal.get(ee) + numProjections);
            countedExtensions.add(ee);
        } else {
            extensionProjected.addProjection(projectedEdge, projections.size(), numProjections);
        }
    }

}
