package com.distributed.search.cluster;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.util.Collections;
import java.util.List;

public class LeaderElection implements Watcher {
    private static final String ELECTION_NAMESPACE = "/election";
    private final ZooKeeper zooKeeper;
    private final OnElectionCallback onElectionCallback;
    private String currentZnodeName;

    public LeaderElection(ZooKeeper zooKeeper, OnElectionCallback onElectionCallback) {
        this.zooKeeper = zooKeeper;
        this.onElectionCallback = onElectionCallback;
    }

    /**
     * Registers this node in the election process by creating an Ephemeral Sequential Znode.
     */
    public void volunteerForLeadership() throws KeeperException, InterruptedException {
        String znodePrefix = ELECTION_NAMESPACE + "/c_";

        // Ensure the parent /election path exists before creating children
        if (zooKeeper.exists(ELECTION_NAMESPACE, false) == null) {
            try {
                zooKeeper.create(ELECTION_NAMESPACE, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } catch (KeeperException.NodeExistsException e) {
                // Race condition: another node created it just now, which is fine
            }
        }

        // Create the Znode with EPHEMERAL_SEQUENTIAL mode
        // EPHEMERAL: Node gets deleted if this program crashes
        // SEQUENTIAL: Zookeeper appends a unique number (used for sorting)
        String znodeFullPath = zooKeeper.create(znodePrefix, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);

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
                // We have the smallest number, so we are the Leader
                System.out.println("I am the leader");
                onElectionCallback.onElectedToBeLeader();
                return;
            } else {
                // We are not the leader. Find our position in the list.
                System.out.println("I am not the leader");
                int index = children.indexOf(currentZnodeName);

                // Edge case: If our node was deleted (e.g., session lost), re-volunteer
                if (index == -1) {
                    System.out.println("Node lost, re-volunteering...");
                    volunteerForLeadership();
                    reelectLeader();
                    return;
                }

                // Optimization: Watch ONLY the node immediately before us.
                // This prevents the "Herd Effect" where all nodes wake up when the leader dies.
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
        switch (event.getType()) {
            case NodeDeleted:
                try {
                    // The node we were watching died. Run election again.
                    reelectLeader();
                } catch (InterruptedException | KeeperException e) {
                    e.printStackTrace();
                }
        }
    }
}