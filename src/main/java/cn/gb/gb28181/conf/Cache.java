package cn.gb.gb28181.conf;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.LRUCache;

public class Cache {
    public static LRUCache<Object, Object> cacheObj = CacheUtil.newLRUCache(10000);

    public LRUCache<Object, Object> getCacheObj() {
        return cacheObj;
    }

    public void setCacheObj(LRUCache<Object, Object> cacheObj) {
        this.cacheObj = cacheObj;
    }
}