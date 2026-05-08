package com.hotsearch.dto;

public record TokenResponse(String token, String username, boolean mustChangePassword) {}
