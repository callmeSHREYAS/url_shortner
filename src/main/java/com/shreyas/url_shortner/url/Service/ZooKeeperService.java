package com.shreyas.url_shortner.url.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ZooKeeperService {

    private static final String ROOT_PATH = "/url-shortener";
    private static final String SERVERS_PATH = "/url-shortener/servers";
    private static final String COUNTER_PATH = "/url-shortener/counter";
    private static final String INIT_COUNTER_PATH = "/url-shortener/counter/initCtr";
    private static final String COUNTER_RANGE_PATH = "/url-shortener/counter/ctrRange";
    private static final int COUNTER_RANGE_SIZE = 100;

    private ZooKeeper zooKeeper;
    private long currentCounter;
    private long currentRangeEnd;

    public ZooKeeper getZooKeeper() {
        return zooKeeper;
    }

    @Value("${zookeeper.connect-string:zookeeper:2181}")
    private String zookeeperHost;

    @Value("${INSTANCE_NAME:UNKNOWN}")
    private String instanceName;

    @PostConstruct
    public void registerServer() throws Exception {

        System.out.println("Connecting to ZooKeeper...");

        zooKeeper = new ZooKeeper(
                zookeeperHost,
                5000,
                event -> {
                    System.out.println("ZooKeeper event: " + event);
                });

        createParentNodes();

        registerInstance();

        allocateCounterRange();

        System.out.println(
                "Registered server in ZooKeeper: " + instanceName);
    }

    private void createParentNodes() throws Exception {

        // /url-shortener
        createPersistentNodeIfMissing(ROOT_PATH, new byte[0]);

        // /url-shortener/servers
        createPersistentNodeIfMissing(SERVERS_PATH, new byte[0]);

        // /url-shortener/counter
        createPersistentNodeIfMissing(COUNTER_PATH, new byte[0]);

        // /url-shortener/counter/initCtr
        createPersistentNodeIfMissing(INIT_COUNTER_PATH, "1".getBytes(StandardCharsets.UTF_8));

        // /url-shortener/counter/ctrRange
        createPersistentNodeIfMissing(COUNTER_RANGE_PATH, new byte[0]);
    }

    private void createPersistentNodeIfMissing(String path, byte[] data) throws Exception {
        if (zooKeeper.exists(path, false) != null) {
            return;
        }

        try {
            zooKeeper.create(
                    path,
                    data,
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.PERSISTENT);
        } catch (KeeperException.NodeExistsException e) {
            // Another server created the znode between exists() and create().
        }
    }

    private void registerInstance() throws Exception {

        String serverPath = SERVERS_PATH + "/" + instanceName;

        if (zooKeeper.exists(serverPath, false) == null) {

            zooKeeper.create(
                    serverPath,
                    instanceName.getBytes(StandardCharsets.UTF_8),
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL);
        }
    }

    public synchronized long getNextCounter() throws Exception {
        if (currentCounter > currentRangeEnd) {
            allocateCounterRange();
        }

        return currentCounter++;
    }

    private synchronized void allocateCounterRange() throws Exception {
        while (true) {
            Stat stat = new Stat();
            // get init ctr
            byte[] initCounterBytes = zooKeeper.getData(INIT_COUNTER_PATH, false, stat);
            // start = initCtr
            long rangeStart = Long.parseLong(new String(initCounterBytes, StandardCharsets.UTF_8));
            long rangeEnd = rangeStart + COUNTER_RANGE_SIZE - 1;
            long nextRangeStart = rangeStart + COUNTER_RANGE_SIZE;

            try {
                zooKeeper.setData(
                        INIT_COUNTER_PATH,
                        Long.toString(nextRangeStart).getBytes(StandardCharsets.UTF_8),
                        stat.getVersion());

                currentCounter = rangeStart;
                currentRangeEnd = rangeEnd;
                saveCounterRange(rangeStart, rangeEnd);

                System.out.println(
                        "Assigned counter range to " + instanceName + ": " + rangeStart + "," + rangeEnd);
                return;
            } catch (KeeperException.BadVersionException e) {
                System.out.println("Counter range changed while assigning " + instanceName + ", retrying...");
            }
        }
    }

    private void saveCounterRange(long rangeStart, long rangeEnd) throws Exception {
        String serverRangePath = COUNTER_RANGE_PATH + "/" + instanceName;
        byte[] rangeBytes = (rangeStart + "," + rangeEnd).getBytes(StandardCharsets.UTF_8);

        if (zooKeeper.exists(serverRangePath, false) == null) {
            zooKeeper.create(
                    serverRangePath,
                    rangeBytes,
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL);
        } else {
            zooKeeper.setData(serverRangePath, rangeBytes, -1);
        }
    }

    @PreDestroy
    public void shutdown() throws Exception {

        System.out.println(
                "Closing ZooKeeper connection for " + instanceName);

        if (zooKeeper != null) {
            zooKeeper.close();
        }
    }

   
}
