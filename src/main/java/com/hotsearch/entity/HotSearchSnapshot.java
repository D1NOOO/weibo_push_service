package com.hotsearch.entity;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.converter.HotSearchItemListJsonConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "hot_search_snapshots", indexes = @Index(name = "idx_hot_search_snapshots_fetched_at", columnList = "fetched_at"))
public class HotSearchSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Convert(converter = HotSearchItemListJsonConverter.class)
    @Column(name = "items", columnDefinition = "TEXT")
    private List<HotSearchItem> items = new ArrayList<>();

    public HotSearchSnapshot() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }

    public List<HotSearchItem> getItems() {
        return items == null ? List.of() : items;
    }

    public void setItems(List<HotSearchItem> items) {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }
}
