package com.hotsearch.fetcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotsearch.dto.HotSearchItem;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WeiboFetcher {

    private static final Logger log = LoggerFactory.getLogger(WeiboFetcher.class);
    private static final String WEIBU_HOT_URL = "https://weibo.com/hot/search";
    private static final String AJAX_URL = "https://weibo.com/ajax/side/hotSearch";

    @Value("${app.fetcher.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36}")
    private String userAgent;

    @Value("${app.fetcher.timeout-seconds:15}")
    private int timeoutSeconds;

    @Value("${app.fetcher.cookie:}")
    private String cookie;

    private final ObjectMapper objectMapper;

    public WeiboFetcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<HotSearchItem> fetch() {
        // Try AJAX API first
        try {
            List<HotSearchItem> items = fetchViaAjax();
            if (!items.isEmpty()) return items;
        } catch (Exception e) {
            log.warn("AJAX方式获取失败，尝试页面抓取: {}", e.getMessage());
        }

        // Try HTML scraping
        try {
            List<HotSearchItem> items = fetchViaHtml();
            if (!items.isEmpty()) return items;
        } catch (Exception e) {
            log.warn("HTML页面抓取失败: {}", e.getMessage());
        }

        // Fallback to mock
        log.info("使用模拟数据");
        return mockData();
    }

    /**
     * Build a Jsoup connection with anti-detection headers to mimic a real browser.
     */
    private Connection buildConnection(String url) {
        Connection conn = Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(timeoutSeconds * 1000)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Referer", "https://weibo.com/")
                .header("Connection", "keep-alive")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Cache-Control", "max-age=0");
        if (cookie != null && !cookie.isBlank()) {
            conn.header("Cookie", cookie);
        }
        return conn;
    }

    private List<HotSearchItem> fetchViaAjax() throws Exception {
        String json = buildConnection(AJAX_URL)
                .ignoreContentType(true)
                .execute().body();

        JsonNode root = objectMapper.readTree(json);
        JsonNode realtime = root.has("data") && root.get("data").has("realtime")
                ? root.get("data").get("realtime")
                : root.has("realtime") ? root.get("realtime") : null;

        if (realtime == null || !realtime.isArray() || realtime.isEmpty()) return List.of();

        List<HotSearchItem> items = new ArrayList<>();
        for (int i = 0; i < realtime.size(); i++) {
            JsonNode x = realtime.get(i);
            String word = x.has("word") ? x.get("word").asText() : "";
            String url = buildSearchUrl(word);
            items.add(new HotSearchItem(
                    x.has("realpos") ? x.get("realpos").asInt() : i + 1,
                    x.has("note") ? x.get("note").asText() : word,
                    x.has("label_name") ? x.get("label_name").asText()
                            : x.has("icon_desc") ? x.get("icon_desc").asText()
                            : x.has("small_icon_desc") ? x.get("small_icon_desc").asText() : null,
                    x.has("num") ? x.get("num").asLong() : null,
                    x.has("is_ad") && x.get("is_ad").asBoolean(),
                    url
            ));
        }
        return items;
    }

    private List<HotSearchItem> fetchViaHtml() throws Exception {
        Document doc = buildConnection(WEIBU_HOT_URL)
                .get();

        // Try to extract from script tags or HTML elements
        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String html = script.html();
            if (html.contains("realtime") || html.contains("hotSearch")) {
                // Try to find JSON data embedded in script
                int start = html.indexOf("{");
                int end = html.lastIndexOf("}");
                if (start >= 0 && end > start) {
                    try {
                        JsonNode node = objectMapper.readTree(html.substring(start, end + 1));
                        JsonNode realtime = node.has("data") ? node.get("data").findPath("realtime") : node.findPath("realtime");
                        if (realtime.isArray() && !realtime.isEmpty()) {
                            List<HotSearchItem> items = new ArrayList<>();
                            for (int i = 0; i < realtime.size(); i++) {
                                JsonNode x = realtime.get(i);
                                String word = x.path("word").asText("");
                                items.add(new HotSearchItem(
                                        i + 1,
                                        x.path("note").asText(word),
                                        x.path("label_name").asText(null),
                                        x.path("num").asLong(0),
                                        x.path("is_ad").asBoolean(false),
                                        buildSearchUrl(word)
                                ));
                            }
                            if (!items.isEmpty()) return items;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return List.of();
    }

    private List<HotSearchItem> mockData() {
        return List.of(
                new HotSearchItem(1, "示例热搜1", "爆", 300000L, false, "https://s.weibo.com/weibo?q=示例热搜1"),
                new HotSearchItem(2, "示例热搜2", "热", 200000L, false, "https://s.weibo.com/weibo?q=示例热搜2"),
                new HotSearchItem(3, "示例热搜3", "新", 150000L, false, "https://s.weibo.com/weibo?q=示例热搜3")
        );
    }

    /**
     * Build a Weibo search URL from a keyword for clickable links.
     */
    private String buildSearchUrl(String keyword) {
        if (keyword == null || keyword.isBlank()) return "";
        return "https://s.weibo.com/weibo?q=" + java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8);
    }
}
