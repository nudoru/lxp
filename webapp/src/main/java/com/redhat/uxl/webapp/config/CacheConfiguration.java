package com.redhat.uxl.webapp.config;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ehcache.InstrumentedEhcache;
import java.util.Set;
import java.util.SortedSet;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.metamodel.EntityType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

/**
 * The type Cache configuration.
 */
@Configuration
@EnableCaching
@AutoConfigureAfter(value = { MetricsConfiguration.class, DatabaseConfiguration.class })
@Profile("!" + Constants.SPRING_PROFILE_FAST)
@Slf4j
public class CacheConfiguration {

    @PersistenceContext
    private EntityManager entityManager;

    @Inject
    private Environment env;

    @Inject
    private MetricRegistry metricRegistry;

    private net.sf.ehcache.CacheManager cacheManager;

    /**
     * Destroy.
     */
    @PreDestroy
    public void destroy() {
        log.info("Remove Cache Manager metrics");
        SortedSet<String> names = metricRegistry.getNames();
        names.forEach(metricRegistry::remove);
        log.info("Closing Cache Manager");
        cacheManager.shutdown();
    }

    /**
     * Cache manager cache manager.
     *
     * @return the cache manager
     */
    @Bean
    public CacheManager cacheManager() {
        log.debug("Starting Ehcache");
        cacheManager = net.sf.ehcache.CacheManager.create();
        cacheManager.getConfiguration()
                .setMaxBytesLocalHeap(env.getProperty("cache.ehcache.maxBytesLocalHeap", String.class, "16M"));
        log.debug("Registering Ehcache Metrics gauges");
        Set<EntityType<?>> entities = entityManager.getMetamodel().getEntities();
        for (EntityType<?> entity : entities) {

            String name = entity.getName();
            if (name == null || entity.getJavaType() != null) {
                name = entity.getJavaType().getName();
            }
            Assert.notNull(name, "entity cannot exist without a identifier");

            net.sf.ehcache.Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.getCacheConfiguration()
                        .setTimeToLiveSeconds(env.getProperty("cache.timeToLiveSeconds", Long.class, 3600L));
                net.sf.ehcache.Ehcache decoratedCache = InstrumentedEhcache.instrument(metricRegistry, cache);
                cacheManager.replaceCacheWithDecoratedCache(cache, decoratedCache);
            }
        }
        EhCacheCacheManager ehCacheManager = new EhCacheCacheManager();
        ehCacheManager.setCacheManager(cacheManager);
        return ehCacheManager;
    }
}