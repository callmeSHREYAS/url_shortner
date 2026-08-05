package com.shreyas.url_shortner.url.Controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InstanceController {

    @Value("${INSTANCE_NAME:UNKNOWN}")
    private String instanceName;

    @GetMapping("/instance")
    public String instance() {
        return instanceName;
    }
}
