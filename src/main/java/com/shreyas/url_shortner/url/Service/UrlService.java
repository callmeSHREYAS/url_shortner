package com.shreyas.url_shortner.url.Service;


import com.shreyas.url_shortner.util.Base62;

import org.springframework.stereotype.Service;

@Service
public class UrlService {

    private final ZooKeeperService zooKeeperService;

    public UrlService(ZooKeeperService zooKeeperService) {
        this.zooKeeperService = zooKeeperService;
    }

    public String generateShortCode(String url) {

        long id;
        try {
            id = zooKeeperService.getNextCounter();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get counter from ZooKeeper", e);
        }

        // Convert number into Base62 string
        return Base62.encode(id);
    }
}
