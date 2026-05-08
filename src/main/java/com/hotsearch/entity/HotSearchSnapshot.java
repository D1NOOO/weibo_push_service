package com.hotsearch.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "hot_search_snapshots")
public class HotSearchSnapshot {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Column(columnDefinition = "TEXT")
    private String items;

    public HotSearchSnapshot() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }

    public String getItems() { return items; }
    public void setItems(String items) { this.items = items; }

    public void setItemsObject(Object itemsObj) {
        try {
            this.items = MAPPER.writeValueAsString(itemsObj);
        } catch (JsonProcessingException e) {
            this.items = "[]";
        }
    }
}
