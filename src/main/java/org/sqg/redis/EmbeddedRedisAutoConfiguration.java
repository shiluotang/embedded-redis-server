package org.sqg.redis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.sqg.redis.EmbeddedRedisAutoConfiguration.RedisConnectionFactoryDependsOnBeanFactoryPostProcessor;

import redis.embedded.RedisExecProvider;
import redis.embedded.RedisServer;
import redis.embedded.RedisServerBuilder;
import redis.embedded.util.OS;
import redis.embedded.util.OsArchitecture;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({EmbeddedRedisProperties.class})
@ConditionalOnClass({RedisServer.class})
@Import({RedisConnectionFactoryDependsOnBeanFactoryPostProcessor.class})
public class EmbeddedRedisAutoConfiguration {

    private static final Logger log =
        LoggerFactory.getLogger(EmbeddedRedisAutoConfiguration.class);

    private boolean overrideExecWithPATH(
            String path,
            RedisExecProvider provider) {
        List<Path> candidates = new ArrayList<Path>();
        OsArchitecture osarch = OsArchitecture.detect();
        String splittor = osarch.os().equals(OS.WINDOWS) ? ";" : ":";
        String[] filenames = osarch.os().equals(OS.WINDOWS)
            ? new String[] {"redis-server.exe", "redis-server.bat"}
            : new String[] {"redis-server"};
        for (Object s : Collections.list(new StringTokenizer(path, splittor))) {
            for (String filename : filenames) {
                Path p = Paths.get((String) s, filename);
                if (Files.exists(p))
                    candidates.add(p.toAbsolutePath());
            }
        }
        if (candidates.size() > 0) {
            Path p = candidates.get(0);
            log.info("redis exec override {} {} => {}",
                    osarch.os(), osarch.arch(),
                    p);
            provider.override(
                    osarch.os(), osarch.arch(),
                    candidates.get(0).toString());
            return true;
        }
        return false;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public RedisServer redisServer(
            EmbeddedRedisProperties props) {
        RedisServerBuilder builder = new RedisServerBuilder();
        builder.port(props.getPort());
        String password = props.getPassword();
        if (password != null && !password.isEmpty())
            builder.setting(String.format("requirepass %s", password));
        builder.setting(String.format("databases %d", props.getDatabases()));
        builder.setting(String.format("timeout %d", props.getTimeout()));
        RedisExecProvider provider = RedisExecProvider.defaultProvider();
        overrideExecWithPATH(System.getenv("PATH"), provider);
        builder.redisExecProvider(provider);
        return builder.build();
    }

    public static class RedisConnectionFactoryDependsOnBeanFactoryPostProcessor
            extends AbstractDependsOnBeanFactoryPostProcessor  {
        public RedisConnectionFactoryDependsOnBeanFactoryPostProcessor() {
            super(RedisConnectionFactory.class, RedisServer.class);
        }
    }
}

