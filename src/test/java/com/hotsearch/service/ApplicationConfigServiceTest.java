package com.hotsearch.service;

import com.hotsearch.entity.Channel;
import com.hotsearch.repository.ChannelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationConfigServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsGlobalSettingsAcrossServiceRestarts() {
        ChannelRepository repository = emptyRepository();
        Path configPath = temporaryDirectory.resolve("app-config.properties");
        Path legacyPath = temporaryDirectory.resolve("legacy.properties");
        ApplicationConfigService first = service(repository, configPath, legacyPath);
        first.initialize();

        first.setDedupeWindowHours(12);
        first.setFetchIntervalMinutes(25);
        first.setSnapshotRetentionDays(45);
        first.setSinkConfig("https://s.example.com/", "site-token");

        ApplicationConfigService restarted = service(repository, configPath, legacyPath);
        restarted.initialize();
        assertThat(restarted.getDedupeWindowHours()).isEqualTo(12);
        assertThat(restarted.getFetchIntervalMinutes()).isEqualTo(25);
        assertThat(restarted.getSnapshotRetentionDays()).isEqualTo(45);
        assertThat(restarted.getSinkConfig())
                .isEqualTo(new ApplicationConfigService.SinkConfig("https://s.example.com", "site-token"));
    }

    @Test
    void migratesSinkCredentialsOutOfLegacyChannelConfig() {
        ChannelRepository repository = mock(ChannelRepository.class);
        Channel channel = new Channel();
        channel.setConfigMap(Map.of(
                "shortLinkEnabled", true,
                "sinkBaseUrl", "https://s.example.com",
                "sinkToken", "site-token"
        ));
        when(repository.findAll()).thenReturn(List.of(channel));
        ApplicationConfigService service = service(repository,
                temporaryDirectory.resolve("app-config.properties"),
                temporaryDirectory.resolve("legacy.properties"));

        service.initialize();

        assertThat(service.getSinkConfig().isConfigured()).isTrue();
        assertThat(channel.getConfigMap())
                .containsEntry("shortLinkEnabled", true)
                .doesNotContainKeys("sinkBaseUrl", "sinkToken");
        verify(repository).saveAll(anyList());
    }

    private ApplicationConfigService service(ChannelRepository repository, Path configPath, Path legacyPath) {
        return new ApplicationConfigService(repository, 6, 10, 30, configPath, legacyPath);
    }

    private ChannelRepository emptyRepository() {
        ChannelRepository repository = mock(ChannelRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        return repository;
    }
}
