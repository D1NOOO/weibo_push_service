package com.hotsearch.service;

import com.hotsearch.entity.Channel;
import com.hotsearch.repository.ChannelRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
public class ApplicationConfigService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationConfigService.class);
    private final ChannelRepository channelRepository;
    private final Path configPath;
    private final Path legacyConfigPath;
    private volatile int dedupeWindowHours;
    private volatile int fetchIntervalMinutes;
    private volatile int snapshotRetentionDays;
    private volatile String sinkBaseUrl = "";
    private volatile String sinkToken = "";

    @Autowired
    public ApplicationConfigService(ChannelRepository channelRepository,
                                    @Value("${app.dedupe.window-hours:6}") int defaultDedupeWindowHours,
                                    @Value("${app.schedule.interval-minutes:10}") int defaultFetchIntervalMinutes,
                                    @Value("${app.snapshot.retention-days:30}") int defaultSnapshotRetentionDays) {
        this(channelRepository, defaultDedupeWindowHours, defaultFetchIntervalMinutes, defaultSnapshotRetentionDays,
                Paths.get("data/app-config.properties"), Paths.get("data/dedupe-config.properties"));
    }

    ApplicationConfigService(ChannelRepository channelRepository, int defaultDedupeWindowHours,
                             int defaultFetchIntervalMinutes, int defaultSnapshotRetentionDays,
                             Path configPath, Path legacyConfigPath) {
        this.channelRepository = channelRepository;
        this.configPath = configPath;
        this.legacyConfigPath = legacyConfigPath;
        this.dedupeWindowHours = defaultDedupeWindowHours;
        this.fetchIntervalMinutes = defaultFetchIntervalMinutes;
        this.snapshotRetentionDays = defaultSnapshotRetentionDays;
    }

    @PostConstruct
    void initialize() {
        loadPersistedConfig();
        migrateLegacyChannelSinkConfig();
    }

    public int getDedupeWindowHours() {
        return dedupeWindowHours;
    }

    public synchronized void setDedupeWindowHours(int hours) {
        dedupeWindowHours = hours;
        persist();
    }

    public int getFetchIntervalMinutes() {
        return fetchIntervalMinutes;
    }

    public long getFetchIntervalMillis() {
        return fetchIntervalMinutes * 60_000L;
    }

    public synchronized void setFetchIntervalMinutes(int minutes) {
        fetchIntervalMinutes = minutes;
        persist();
    }

    public int getSnapshotRetentionDays() {
        return snapshotRetentionDays;
    }

    public synchronized void setSnapshotRetentionDays(int days) {
        snapshotRetentionDays = days;
        persist();
    }

    public SinkConfig getSinkConfig() {
        return new SinkConfig(sinkBaseUrl, sinkToken);
    }

    public synchronized void setSinkConfig(String baseUrl, String token) {
        sinkBaseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        sinkToken = token == null ? "" : token.trim();
        persist();
    }

    private void loadPersistedConfig() {
        Properties properties = new Properties();
        Path source = Files.exists(configPath) ? configPath : legacyConfigPath;
        if (!Files.exists(source)) return;

        try (InputStream input = Files.newInputStream(source)) {
            properties.load(input);
            dedupeWindowHours = boundedInt(properties.getProperty("dedupe.window-hours"),
                    dedupeWindowHours, 1, 168);
            fetchIntervalMinutes = boundedInt(properties.getProperty("schedule.interval-minutes"),
                    fetchIntervalMinutes, 1, 1_440);
            snapshotRetentionDays = boundedInt(properties.getProperty("snapshot.retention-days"),
                    snapshotRetentionDays, 1, 3_650);
            sinkBaseUrl = properties.getProperty("sink.base-url", "").trim().replaceAll("/+$", "");
            sinkToken = properties.getProperty("sink.token", "").trim();
            if (source.equals(legacyConfigPath)) persist();
        } catch (Exception e) {
            log.warn("Failed to load persisted application configuration; defaults will be used", e);
        }
    }

    private void migrateLegacyChannelSinkConfig() {
        List<Channel> changedChannels = new ArrayList<>();
        String migratedBaseUrl = sinkBaseUrl;
        String migratedToken = sinkToken;

        for (Channel channel : channelRepository.findAll()) {
            Map<String, Object> config = new LinkedHashMap<>(channel.getConfigMap());
            String channelBaseUrl = stringValue(config.get("sinkBaseUrl"));
            String channelToken = stringValue(config.get("sinkToken"));
            if ((migratedBaseUrl.isBlank() || migratedToken.isBlank())
                    && !channelBaseUrl.isBlank() && !channelToken.isBlank()) {
                migratedBaseUrl = channelBaseUrl;
                migratedToken = channelToken;
            }
        }

        boolean globalSinkConfigured = !migratedBaseUrl.isBlank() && !migratedToken.isBlank();
        if (!globalSinkConfigured) return;

        for (Channel channel : channelRepository.findAll()) {
            Map<String, Object> config = new LinkedHashMap<>(channel.getConfigMap());
            boolean changed = config.remove("sinkBaseUrl") != null;
            changed |= config.remove("sinkToken") != null;
            if (changed) {
                channel.setConfigMap(config);
                changedChannels.add(channel);
            }
        }

        boolean migratedGlobalConfig = !migratedBaseUrl.equals(sinkBaseUrl) || !migratedToken.equals(sinkToken);
        sinkBaseUrl = migratedBaseUrl.trim().replaceAll("/+$", "");
        sinkToken = migratedToken.trim();
        if (migratedGlobalConfig) persist();
        if (!changedChannels.isEmpty()) channelRepository.saveAll(changedChannels);
        if (migratedGlobalConfig || !changedChannels.isEmpty()) {
            log.info("Migrated Sink configuration from channels to global application configuration");
        }
    }

    private synchronized void persist() {
        try {
            Files.createDirectories(configPath.getParent());
            Properties properties = new Properties();
            properties.setProperty("dedupe.window-hours", String.valueOf(dedupeWindowHours));
            properties.setProperty("schedule.interval-minutes", String.valueOf(fetchIntervalMinutes));
            properties.setProperty("snapshot.retention-days", String.valueOf(snapshotRetentionDays));
            properties.setProperty("sink.base-url", sinkBaseUrl);
            properties.setProperty("sink.token", sinkToken);

            Path temporary = configPath.resolveSibling(configPath.getFileName() + ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Application configuration - persists across restarts");
            }
            try {
                Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist application configuration", e);
        }
    }

    private int boundedInt(String value, int fallback, int minimum, int maximum) {
        if (value == null || value.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= minimum && parsed <= maximum ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record SinkConfig(String baseUrl, String token) {
        public boolean isConfigured() {
            return baseUrl != null && !baseUrl.isBlank() && token != null && !token.isBlank();
        }
    }
}
