package com.demo.sliceupload.cache;

import cn.hutool.cache.impl.LRUCache;

/**
 * @author sgz
 * @since 2024/6/3
 */
public class CacheUtil {

    public LRUCache<String, String> cache = new LRUCache<>(100);
}
