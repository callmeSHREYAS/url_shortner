package com.shreyas.url_shortner.url.Service;


import com.shreyas.url_shortner.util.Base62;

import io.netty.util.internal.ThreadLocalRandom;

import org.springframework.stereotype.Service;

@Service
public class UrlService {

   

    public String generateShortCode(String url) {

      
        long id = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

        // Convert number into Base62 string
        return Base62.encode(id);
    }
}