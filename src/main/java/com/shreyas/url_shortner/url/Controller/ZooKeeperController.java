package com.shreyas.url_shortner.url.Controller;
import org.apache.curator.framework.CuratorFramework;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ZooKeeperController {

    private final CuratorFramework curatorFramework;

    public ZooKeeperController(CuratorFramework curatorFramework) {
        this.curatorFramework = curatorFramework;
    }

    @GetMapping("/zookeeper")
    public String testZooKeeper() {

        try {
            return "ZooKeeper connected: "
                    + curatorFramework.checkExists()
                    .forPath("/");
        } catch (Exception e) {
            return "ZooKeeper connection failed: " + e.getMessage();
        }
    }
}