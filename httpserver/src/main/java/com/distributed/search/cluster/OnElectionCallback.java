package com.distributed.search.cluster;

/**
 * callback interface to define the behavior of the node based on the election result.
 */
public interface OnElectionCallback {

    /**
     * Called when this node wins the election and becomes the Leader.
     * The node should start coordinator tasks (like Service Discovery).
     */
    void onElectedToBeLeader();

    /**
     * Called when this node becomes a Worker (does not win the election).
     * The node should register itself to the cluster and wait for tasks.
     */
    void onWorker();
}