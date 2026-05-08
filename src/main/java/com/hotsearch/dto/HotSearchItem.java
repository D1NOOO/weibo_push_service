package com.hotsearch.dto;

public record HotSearchItem(
    int rank, String keyword, String label, Long hotValue, boolean isAd, String url
) {}
