package com.shreyas.url_shortner.url;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy; 
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ZooKeeperService {

    private static final String ZOOKEEPER_HOST = "zookeeper:2181";

    private static final String SERVERS_PATH = "/url-shortener/servers";

    private ZooKeeper zooKeeper;

    @Value("${INSTANCE_NAME:UNKNOWN}")
    private String instanceName;

    @PostConstruct
    public void registerServer() throws Exception {

        System.out.println("Connecting to ZooKeeper...");

        zooKeeper = new ZooKeeper(
                ZOOKEEPER_HOST,
                5000,
                event -> {
                    System.out.println("ZooKeeper event: " + event);
                }
        );

        createParentNodes();

        registerInstance();

        System.out.println(
                "Registered server in ZooKeeper: " + instanceName
        );
    }

    private void createParentNodes() throws Exception {

        // /url-shortener
        if (zooKeeper.exists("/url-shortener", false) == null) {

            zooKeeper.create(
                    "/url-shortener",
                    new byte[0],
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.PERSISTENT
            );
        }

        // /url-shortener/servers
        if (zooKeeper.exists(SERVERS_PATH, false) == null) {

            zooKeeper.create(
                    SERVERS_PATH,
                    new byte[0],
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.PERSISTENT
            );
        }
    }

    private void registerInstance() throws Exception {

        String serverPath = SERVERS_PATH + "/" + instanceName;

        if (zooKeeper.exists(serverPath, false) == null) {

            zooKeeper.create(
                    serverPath,
                    instanceName.getBytes(),
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL
            );
        }
    }

    @PreDestroy
    public void shutdown() throws Exception {

        System.out.println(
                "Closing ZooKeeper connection for " + instanceName
        );

        if (zooKeeper != null) {
            zooKeeper.close();
        }
    }
}