package com.shreyas.url_shortner.url.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shreyas.url_shortner.url.Service.RedisService;

@RestController
@RequestMapping("/redis")
public class RedisController {

    @Autowired
    private RedisService redisService;
    
    @PostMapping("/{key}/{value}")
    public String save(
            @PathVariable String key,
            @PathVariable String value) {

        redisService.save(key, value);

        return "Saved";
    }

    @GetMapping("/{key}")
    public String get(@PathVariable String key) {

        return redisService.get(key);
    }

    @DeleteMapping("/{key}")
    public String delete(@PathVariable String key) {

        redisService.delete(key);

        return "Deleted";
    }
}