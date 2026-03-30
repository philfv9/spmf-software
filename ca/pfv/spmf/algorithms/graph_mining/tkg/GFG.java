package ca.pfv.spmf.algorithms.graph_mining.tkg;

import java.util.HashSet;
import java.util.Set;

// Java implementation of the approach
class GFG
{

    static int MAX = 100;

    // Stores the vertices
    static int []store = new int[MAX];
    static int n;

    // Graph
    static int [][]graph = new int [MAX][MAX];

    // Degree of the vertices
    static int []d = new int[MAX];

    // Function to check if the given set of vertices
// in store array is a clique or not
    static boolean is_clique(int b)
    {
        // Run a loop for all the set of edges
        // for the select vertex
        for (int i = 1; i < b; i++)
        {
            for (int j = i + 1; j < b; j++)

                // If any edge is missing
                if (graph[store[i]][store[j]] == 0)
                    return false;
        }
        return true;
    }

    // Function to print the clique
    static void print(int n)
    {
        for (int i = 1; i < n; i++)
            System.out.print(store[i] + " ");
        System.out.print(", ");
    }

    static Set<Integer> createClique(int n)
    {
        Set<Integer> clique = new HashSet<Integer>();
        for (int i = 1; i < n; i++)
            clique.add(store[i]);
        return clique;
    }

    // Function to find all the cliques of size s
    static void findCliques(int i, int l, int s, Set<Set<Integer>> cliques)
    {
        // Check if any vertices from i+1 can be inserted
        for (int j = i + 1; j <= n - (s - l); j++)

            // If the degree of the graph is sufficient
            if (d[j] >= s - 1)
            {

                // Add the vertex to store
                store[l] = j;

                // If the graph is not a clique of size k
                // then it cannot be a clique
                // by adding another edge
                if (is_clique(l + 1))

                    // If the length of the clique is
                    // still less than the desired size
                    if (l < s)

                        // Recursion to add vertices
                        findCliques(j, l + 1, s, cliques);

                        // Size is met
                    else {
                        print(l + 1);
                        Set<Integer> clique = createClique(l + 1);
                        cliques.add(clique);
                    }
            }
    }

    public static Set<Set<Integer>> labeledGraphCliques(DatabaseGraph g, int edgeLabel, int vertexLabel, int cliqueSize) {
        Set<Set<Integer>> cliques = new HashSet<Set<Integer>>();
        graph = new int[g.vertices.length][g.vertices.length];
        d = new int[g.vertices.length];
        for (Vertex v: g.getAllVertices()) {
            if (v.getLabel() != vertexLabel) {
                continue;
            }

            for (Edge e: v.getEdgeList()) {
                if (e.getEdgeLabel() != edgeLabel) {
                    continue;
                }

                if (e.v1 == v.getId() && g.vertices[e.v2].getLabel() != vertexLabel) {
                    continue;
                }

                if (e.v2 == v.getId() && g.vertices[e.v1].getLabel() != vertexLabel) {
                    continue;
                }

                if (e.v1 < e.v2) {
                    graph[e.v1][e.v2] = 1;
                    graph[e.v2][e.v1] = 1;
                    d[e.v1]++;
                    d[e.v2]++;
                }
            }
        }

        return cliques;
    }

    // Driver code
    public static void main(String[] args)
    {
        int edges[][] = { { 1, 2 },
                { 2, 3 },
                { 3, 1 },
                { 4, 3 },
                { 4, 5 },
                { 5, 3 } },
                k = 3;
        int size = edges.length;
        n = 5;

        for (int i = 0; i < size; i++)
        {
            graph[edges[i][0]][edges[i][1]] = 1;
            graph[edges[i][1]][edges[i][0]] = 1;
            d[edges[i][0]]++;
            d[edges[i][1]]++;
        }

        Set<Set<Integer>> cliques = new HashSet<Set<Integer>>();
        findCliques(0, 1, k, cliques);
    }
}

// This code is contributed by 29AjayKumar
