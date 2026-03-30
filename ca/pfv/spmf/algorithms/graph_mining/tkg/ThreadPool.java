package ca.pfv.spmf.algorithms.graph_mining.tkg;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
 * This is implementation CGspan thread pools.
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
 * @author Shaul Zevin
 */
public final class ThreadPool {

    // rightmost path extender threads pool singleton
    private static ThreadPool RIGHT_MOST_EXTENDER_INSTANCE;
    // number rightmost path extender threads
    public static int RIGHT_MOST_EXTENDER_THREAD_COUNT = 4;
    // projections producer threads pool singleton
    private static ThreadPool PROJECTED_ITERATOR_PRODUCERS_INSTANCE;
    // number of extensions producer threads
    public static int PROJECTED_ITERATOR_PRODUCERS_THREAD_COUNT = 4;
    private ExecutorService executorService;
    private int threadCount;

    private ThreadPool(int threadCount) {
        this.threadCount = threadCount;
        executorService = Executors.newFixedThreadPool(threadCount);
    }

    /**
     *
     * @return rightmost path extender threads pool singleton
     */
    public static synchronized ThreadPool getRightMostExtenderInstance() {
        if(RIGHT_MOST_EXTENDER_INSTANCE == null) {
            RIGHT_MOST_EXTENDER_INSTANCE = new ThreadPool(RIGHT_MOST_EXTENDER_THREAD_COUNT);
        }

        return RIGHT_MOST_EXTENDER_INSTANCE;
    }

    /**
     *
     * @return projections producer threads pool singleton
     */
    public static synchronized  ThreadPool getProjectedIteratorProducersInstance() {
        if(PROJECTED_ITERATOR_PRODUCERS_INSTANCE == null) {
            PROJECTED_ITERATOR_PRODUCERS_INSTANCE = new ThreadPool(PROJECTED_ITERATOR_PRODUCERS_THREAD_COUNT);
        }

        return PROJECTED_ITERATOR_PRODUCERS_INSTANCE;
    }

    // getters and setters

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    /**
     * thread pools shutdown
     */
    public static synchronized void shutdown() {
        if (RIGHT_MOST_EXTENDER_INSTANCE != null) {
            RIGHT_MOST_EXTENDER_INSTANCE.getExecutorService().shutdown();
            RIGHT_MOST_EXTENDER_INSTANCE = null;
        }

        if (PROJECTED_ITERATOR_PRODUCERS_INSTANCE != null) {
            PROJECTED_ITERATOR_PRODUCERS_INSTANCE.getExecutorService().shutdown();
            PROJECTED_ITERATOR_PRODUCERS_INSTANCE = null;
        }
    }
}
