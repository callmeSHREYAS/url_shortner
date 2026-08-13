package com.shreyas.url_shortner.url.Service;

import org.apache.zookeeper.*;
import org.springframework.stereotype.Service;
import com.shreyas.url_shortner.url.Service.ZooKeeperService;
import jakarta.annotation.PostConstruct;

@Service
public class CounterService {

    private static final String COUNTER_PATH = "/url-shortener/counter";

    private final ZooKeeper zooKeeper;

    public CounterService(ZooKeeperService zooKeeperService) {
        this.zooKeeper = zooKeeperService.getZooKeeper();
    }

    public void createCounterIfNotExists() throws Exception {

        if (zooKeeper.exists(COUNTER_PATH, false) == null) {

            zooKeeper.create(
                    COUNTER_PATH,
                    "1".getBytes(),
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.PERSISTENT);

            System.out.println("Counter node created.");
        }
    }

    @PostConstruct
    public void initializeCounter() throws Exception {
        createCounterIfNotExists();
    }
}