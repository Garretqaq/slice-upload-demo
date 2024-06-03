package com.demo.sliceupload.config;

import cn.hutool.cache.Cache;
import cn.hutool.cache.impl.LRUCache;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * @author sgz
 * @since 2024/6/3
 */
@Component
public class CacheConfig {

    @Bean
    public Cache<String, String> cache(){
        return new LRUCache<>(100);
    }
}
