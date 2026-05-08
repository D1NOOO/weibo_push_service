package com.hotsearch.matcher;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Subscription;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SubscriptionMatcher {

    public record MatchResult(Subscription subscription, HotSearchItem item) {}

    public List<MatchResult> match(List<HotSearchItem> items, List<Subscription> subscriptions) {
        List<MatchResult> results = new ArrayList<>();
        for (Subscription sub : subscriptions) {
            if (!Boolean.TRUE.equals(sub.getEnabled())) continue;
            for (HotSearchItem item : items) {
                if (hit(sub, item)) {
                    results.add(new MatchResult(sub, item));
                }
            }
        }
        return results;
    }

    private boolean hit(Subscription sub, HotSearchItem item) {
        List<String> keywords = sub.getKeywords();
        if (keywords == null || keywords.isEmpty()) return false;
        
        // Support regex matching: if keyword starts with "regex:", treat as pattern
        boolean keywordMatch = keywords.stream().anyMatch(k -> matchKeyword(k, item.keyword()));
        if (!keywordMatch) return false;

        List<String> excludeKeywords = sub.getExcludeKeywords();
        if (excludeKeywords != null && excludeKeywords.stream().anyMatch(k -> matchKeyword(k, item.keyword()))) {
            return false;
        }

        List<String> labels = sub.getLabels();
        if (labels != null && !labels.isEmpty() && item.label() != null && !labels.contains(item.label())) {
            return false;
        }

        if (sub.getMinHotValue() != null && item.hotValue() != null && item.hotValue() < sub.getMinHotValue()) {
            return false;
        }

        return true;
    }

    /**
     * Match a keyword pattern against text.
     * Supports:
     * - Plain text: contains match (default, backward compatible)
     * - regex:PATTERN: full regex match
     * - prefix:XXX: startsWith match
     */
    private boolean matchKeyword(String pattern, String text) {
        if (pattern == null || pattern.isBlank() || text == null) return false;
        
        if (pattern.startsWith("regex:")) {
            try {
                return java.util.regex.Pattern.compile(pattern.substring(6)).matcher(text).find();
            } catch (Exception e) {
                // Invalid regex, fallback to contains
                return text.contains(pattern.substring(6));
            }
        } else if (pattern.startsWith("prefix:")) {
            return text.startsWith(pattern.substring(7));
        } else {
            return text.contains(pattern);
        }
    }
}
