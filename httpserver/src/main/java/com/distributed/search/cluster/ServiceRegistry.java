package com.distributed.search.cluster;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ServiceRegistry implements Watcher {
    private static final String REGISTRY_NAMESPACE = "/registry";
    private final ZooKeeper zooKeeper;
    private String currentZnode = null;
    private List<String> allServiceAddresses = new ArrayList<>();

    public ServiceRegistry(ZooKeeper zooKeeper) {
        this.zooKeeper = zooKeeper;
        createServiceRegistryZnode();
    }

    /**
     * Registers the current node as a Worker in the cluster.
     * It creates an Ephemeral Sequential node containing the worker's address.
     * @param metadata The address (IP:Port) of the worker.
     */
    public void registerToCluster(String metadata) throws KeeperException, InterruptedException {
        if (this.currentZnode != null) {
            System.out.println("Already registered to service registry");
            return;
        }
        this.currentZnode = zooKeeper.create(REGISTRY_NAMESPACE + "/n_", metadata.getBytes(),
                ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        System.out.println("Registered to service registry with address: " + metadata);
    }

    /**
     * Called by the Leader to start watching for updates in the worker list.
     */
    public void registerForUpdates() {
        try {
            updateAddresses();
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the cached list of all available worker addresses.
     * If the list is empty, it forces an update from Zookeeper.
     */
    public synchronized List<String> getAllServiceAddresses() {
        if (allServiceAddresses.isEmpty()) {
            try {
                updateAddresses();
            } catch (KeeperException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        return Collections.unmodifiableList(allServiceAddresses);
    }

    /**
     * Removes the node from the cluster (used when promoting to Leader or shutting down).
     */
    public void unregisterFromCluster() {
        try {
            if (currentZnode != null && zooKeeper.exists(currentZnode, false) != null) {
                zooKeeper.delete(currentZnode, -1);
            }
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Ensures the parent /registry path exists in Zookeeper.
     */
    private void createServiceRegistryZnode() {
        try {
            if (zooKeeper.exists(REGISTRY_NAMESPACE, false) == null) {
                // Try to create the persistent parent node
                zooKeeper.create(REGISTRY_NAMESPACE, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        } catch (KeeperException.NodeExistsException e) {
            // Race condition: Node was created by another instance concurrently. This is expected and fine.
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Fetches the latest list of workers from Zookeeper and updates the local cache.
     * It also re-registers the Watcher to receive future updates.
     */
    private synchronized void updateAddresses() throws KeeperException, InterruptedException {
        // Get all child nodes (workers) and set a watch for future changes
        List<String> workerZnodes = zooKeeper.getChildren(REGISTRY_NAMESPACE, this);

        List<String> addresses = new ArrayList<>(workerZnodes.size());

        for (String workerZnode : workerZnodes) {
            String workerFullPath = REGISTRY_NAMESPACE + "/" + workerZnode;
            Stat stat = zooKeeper.exists(workerFullPath, false);
            if (stat == null) {
                continue;
            }

            // Read the worker's address stored in the Znode data
            byte[] addressBytes = zooKeeper.getData(workerFullPath, false, stat);
            String address = new String(addressBytes);
            addresses.add(address);
        }

        this.allServiceAddresses = Collections.unmodifiableList(addresses);
        System.out.println("The cluster addresses are: " + this.allServiceAddresses);
    }

    @Override
    public void process(WatchedEvent event) {
        // This callback is triggered when the list of workers changes (NodeChildrenChanged)
        try {
            updateAddresses();
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}