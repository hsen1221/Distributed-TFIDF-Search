package com.distributed.search.cluster;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.util.Collections;
import java.util.List;

/**
 * Handles the Leader Election process using Zookeeper.
 * Implements the "Leader Election" pattern where the node with the smallest Znode sequence number becomes the leader.
 */
public class LeaderElection implements Watcher {
    private static final String ELECTION_NAMESPACE = "/election";
    private final ZooKeeper zooKeeper;
    private final OnElectionCallback onElectionCallback;

    private String currentZnodeName;
    private String currentAddress; // Store address for re-volunteering if needed

    public LeaderElection(ZooKeeper zooKeeper, OnElectionCallback onElectionCallback) {
        this.zooKeeper = zooKeeper;
        this.onElectionCallback = onElectionCallback;
    }

    /**
     * Registers this node in the election process.
     * UPDATED: Now saves the node's HTTP address in the Znode data so the Frontend can find the Leader.
     * * @param address The HTTP address (e.g., "localhost:8081") of this node.
     */
    public void volunteerForLeadership(String address) throws KeeperException, InterruptedException {
        this.currentAddress = address;
        String znodePrefix = ELECTION_NAMESPACE + "/c_";

        // Ensure parent /election Znode exists
        if (zooKeeper.exists(ELECTION_NAMESPACE, false) == null) {
            try {
                zooKeeper.create(ELECTION_NAMESPACE, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } catch (KeeperException.NodeExistsException e) {
                // Ignore race condition if another node created it first
            }
        }

        // Create the Znode with EPHEMERAL_SEQUENTIAL mode
        // Data = address (byte array)
        String znodeFullPath = zooKeeper.create(znodePrefix, address.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);

        System.out.println("znode name: " + znodeFullPath);
        this.currentZnodeName = znodeFullPath.replace(ELECTION_NAMESPACE + "/", "");
    }

    /**
     * Determines if this node is the leader or if it should watch a predecessor.
     */
    public void reelectLeader() throws KeeperException, InterruptedException {
        Stat predecessorStat = null;
        String predecessorZnodeName = "";

        while (predecessorStat == null) {
            List<String> children = zooKeeper.getChildren(ELECTION_NAMESPACE, false);
            Collections.sort(children); // Sort to find the smallest number

            String smallestChild = children.get(0);

            if (smallestChild.equals(currentZnodeName)) {
                // We have the smallest number -> We are the Leader
                System.out.println("I am the leader");
                onElectionCallback.onElectedToBeLeader();
                return;
            } else {
                // We are not the leader
                System.out.println("I am not the leader");
                int index = children.indexOf(currentZnodeName);

                // Edge case: If our node was deleted (e.g., session lost), re-volunteer
                if (index == -1) {
                    System.out.println("Node lost, re-volunteering...");
                    volunteerForLeadership(this.currentAddress); // Re-submit with stored address
                    reelectLeader();
                    return;
                }

                // Optimization: Watch ONLY the node immediately before us
                // This prevents the "Herd Effect"
                int predecessorIndex = index - 1;
                predecessorZnodeName = children.get(predecessorIndex);
                predecessorStat = zooKeeper.exists(ELECTION_NAMESPACE + "/" + predecessorZnodeName, this);
            }
        }

        // If we are here, we are a Worker watching a predecessor
        onElectionCallback.onWorker();
        System.out.println("Watching node: " + predecessorZnodeName);
    }

    @Override
    public void process(WatchedEvent event) {
        if (event.getType() == Event.EventType.NodeDeleted) {
            try {
                // The node we were watching died. Run election again.
                reelectLeader();
            } catch (InterruptedException | KeeperException e) {
                e.printStackTrace();
            }
        }
    }
}