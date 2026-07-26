package com.hotsearch.entity.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

import java.util.LinkedHashMap;
import java.util.Map;

@Converter
public class JsonMapConverter extends AbstractJsonConverter<Map<String, Object>> {

    public JsonMapConverter() {
        super(LinkedHashMap::new, "{}");
    }

    @Override
    protected Map<String, Object> parse(String json) throws Exception {
        return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    }
}
