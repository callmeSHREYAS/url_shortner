package com.shreyas.url_shortner.url.config;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZooKeeperConfig {

    @Value("${zookeeper.connect-string}")
    private String connectString;

    @Bean
    public CuratorFramework curatorFramework() {

        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .retryPolicy(
                        new ExponentialBackoffRetry(1000, 3))
                .build();

        client.start();
        System.out.println(connectString);
        return client;
    }
}