/*
 * (C) Copyright 2005-2016, by Christian Soltenborn and Contributors.
 *
 * JGraphT : a free Java graph-theory library
 *
 * This program and the accompanying materials are dual-licensed under
 * either
 *
 * (a) the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation, or (at your option) any
 * later version.
 *
 * or (per the licensee's choosing)
 *
 * (b) the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation.
 */
package org.jgrapht.alg;

import java.util.*;

import org.jgrapht.*;
import org.jgrapht.alg.interfaces.*;
import org.jgrapht.graph.*;

/**
 * <p>
 * Complements the {@link ConnectivityInspector} class with the capability to compute the strongly
 * connected components of a directed graph. The algorithm is implemented after "Cormen et al:
 * Introduction to algorithms", Chapter 22.5. It has a running time of O(V + E).
 * </p>
 *
 * <p>
 * Unlike {@link ConnectivityInspector}, this class does not implement incremental inspection. The
 * full algorithm is executed at the first call of
 * {@link StrongConnectivityInspector#stronglyConnectedSets()} or
 * {@link StrongConnectivityInspector#isStronglyConnected()}.
 * </p>
 *
 * @author Christian Soltenborn
 * @author Christian Hammer
 * @since Feb 2, 2005
 * @deprecated Use {@link KosarajuStrongConnectivityInspector} instead.
 */
@Deprecated
public class StrongConnectivityInspector<V, E>
    implements StrongConnectivityAlgorithm<V, E>
{
    // the graph to compute the strongly connected sets for
    private final DirectedGraph<V, E> graph;

    // stores the vertices, ordered by their finishing time in first dfs
    private LinkedList<VertexData<V>> orderedVertices;

    // the result of the computation, cached for future calls
    private List<Set<V>> stronglyConnectedSets;

    // the result of the computation, cached for future calls
    private List<DirectedSubgraph<V, E>> stronglyConnectedSubgraphs;

    // maps vertices to their VertexData object
    private Map<V, VertexData<V>> vertexToVertexData;

    /**
     * The constructor of the StrongConnectivityAlgorithm class.
     *
     * @param directedGraph the graph to inspect
     *
     * @throws IllegalArgumentException
     */
    public StrongConnectivityInspector(DirectedGraph<V, E> directedGraph)
    {
        if (directedGraph == null) {
            throw new IllegalArgumentException("null not allowed for graph!");
        }

        graph = directedGraph;
        vertexToVertexData = null;
        orderedVertices = null;
        stronglyConnectedSets = null;
        stronglyConnectedSubgraphs = null;
    }

    /**
     * Returns the graph inspected by the StrongConnectivityAlgorithm.
     *
     * @return the graph inspected by this StrongConnectivityAlgorithm
     */
    public DirectedGraph<V, E> getGraph()
    {
        return graph;
    }

    /**
     * Returns true if the graph of this <code>
     * StronglyConnectivityInspector</code> instance is strongly connected.
     *
     * @return true if the graph is strongly connected, false otherwise
     */
    public boolean isStronglyConnected()
    {
        return stronglyConnectedSets().size() == 1;
    }

    /**
     * Computes a {@link List} of {@link Set}s, where each set contains vertices which together form
     * a strongly connected component within the given graph.
     *
     * @return <code>List</code> of <code>Set</code> s containing the strongly connected components
     */
    public List<Set<V>> stronglyConnectedSets()
    {
        if (stronglyConnectedSets == null) {
            orderedVertices = new LinkedList<VertexData<V>>();
            stronglyConnectedSets = new Vector<Set<V>>();

            // create VertexData objects for all vertices, store them
            createVertexData();

            // perform the first round of DFS, result is an ordering
            // of the vertices by decreasing finishing time
            for (VertexData<V> data : vertexToVertexData.values()) {
                if (!data.isDiscovered()) {
                    dfsVisit(graph, data, null);
                }
            }

            // 'create' inverse graph (i.e. every edge is reversed)
            DirectedGraph<V, E> inverseGraph = new EdgeReversedGraph<V, E>(graph);

            // get ready for next dfs round
            resetVertexData();

            // second dfs round: vertices are considered in decreasing
            // finishing time order; every tree found is a strongly
            // connected set
            for (VertexData<V> data : orderedVertices) {
                if (!data.isDiscovered()) {
                    // new strongly connected set
                    Set<V> set = new HashSet<V>();
                    stronglyConnectedSets.add(set);
                    dfsVisit(inverseGraph, data, set);
                }
            }

            // clean up for garbage collection
            orderedVertices = null;
            vertexToVertexData = null;
        }

        return stronglyConnectedSets;
    }

    /**
     * <p>
     * Computes a list of {@link DirectedSubgraph}s of the given graph. Each subgraph will represent
     * a strongly connected component and will contain all vertices of that component. The subgraph
     * will have an edge (u,v) iff u and v are contained in the strongly connected component.
     * </p>
     *
     * <p>
     * NOTE: Calling this method will first execute
     * {@link StrongConnectivityInspector#stronglyConnectedSets()}. If you don't need subgraphs, use
     * that method.
     * </p>
     *
     * @return a list of subgraphs representing the strongly connected components
     */
    public List<DirectedSubgraph<V, E>> stronglyConnectedSubgraphs()
    {
        if (stronglyConnectedSubgraphs == null) {
            List<Set<V>> sets = stronglyConnectedSets();
            stronglyConnectedSubgraphs = new Vector<DirectedSubgraph<V, E>>(sets.size());

            for (Set<V> set : sets) {
                stronglyConnectedSubgraphs.add(new DirectedSubgraph<V, E>(graph, set, null));
            }
        }

        return stronglyConnectedSubgraphs;
    }

    /*
     * Creates a VertexData object for every vertex in the graph and stores them in a HashMap.
     */
    private void createVertexData()
    {
        vertexToVertexData = new HashMap<V, VertexData<V>>(graph.vertexSet().size());

        for (V vertex : graph.vertexSet()) {
            vertexToVertexData.put(vertex, new VertexData2<V>(vertex, false, false));
        }
    }

    /*
     * The subroutine of DFS. NOTE: the set is used to distinguish between 1st and 2nd round of DFS.
     * set == null: finished vertices are stored (1st round). set != null: all vertices found will
     * be saved in the set (2nd round)
     */
    private void dfsVisit(
        DirectedGraph<V, E> visitedGraph, VertexData<V> vertexData, Set<V> vertices)
    {
        Deque<VertexData<V>> stack = new ArrayDeque<VertexData<V>>();
        stack.add(vertexData);

        while (!stack.isEmpty()) {
            VertexData<V> data = stack.removeLast();

            if (!data.isDiscovered()) {
                data.setDiscovered(true);

                if (vertices != null) {
                    vertices.add(data.getVertex());
                }

                stack.add(new VertexData1<V>(data, true, true));

                // follow all edges
                for (E edge : visitedGraph.outgoingEdgesOf(data.getVertex())) {
                    VertexData<V> targetData =
                        vertexToVertexData.get(visitedGraph.getEdgeTarget(edge));

                    if (!targetData.isDiscovered()) {
                        // the "recursion"
                        stack.add(targetData);
                    }
                }
            } else if (data.isFinished()) {
                if (vertices == null) {
                    orderedVertices.addFirst(data.getFinishedData());
                }
            }
        }
    }

    /*
     * Resets all VertexData objects.
     */
    private void resetVertexData()
    {
        for (VertexData<V> data : vertexToVertexData.values()) {
            data.setDiscovered(false);
            data.setFinished(false);
        }
    }

    /*
     * Lightweight class storing some data for every vertex.
     */
    private static abstract class VertexData<V>
    {
        private byte bitfield;

        private VertexData(boolean discovered, boolean finished)
        {
            this.bitfield = 0;
            setDiscovered(discovered);
            setFinished(finished);
        }

        private boolean isDiscovered()
        {
            return (bitfield & 1) == 1;
        }

        private boolean isFinished()
        {
            return (bitfield & 2) == 2;
        }

        private void setDiscovered(boolean discovered)
        {
            if (discovered) {
                bitfield |= 1;
            } else {
                bitfield &= ~1;
            }
        }

        private void setFinished(boolean finished)
        {
            if (finished) {
                bitfield |= 2;
            } else {
                bitfield &= ~2;
            }
        }

        abstract VertexData<V> getFinishedData();

        abstract V getVertex();
    }

    private static final class VertexData1<V>
        extends VertexData<V>
    {
        private final VertexData<V> finishedData;

        private VertexData1(VertexData<V> finishedData, boolean discovered, boolean finished)
        {
            super(discovered, finished);
            this.finishedData = finishedData;
        }

        @Override
        VertexData<V> getFinishedData()
        {
            return finishedData;
        }

        @Override
        V getVertex()
        {
            return null;
        }
    }

    private static final class VertexData2<V>
        extends VertexData<V>
    {
        private final V vertex;

        private VertexData2(V vertex, boolean discovered, boolean finished)
        {
            super(discovered, finished);
            this.vertex = vertex;
        }

        @Override
        VertexData<V> getFinishedData()
        {
            return null;
        }

        @Override
        V getVertex()
        {
            return vertex;
        }
    }
}

// End StrongConnectivityAlgorithm.java
