package com.hotsearch.entity.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hotsearch.dto.HotSearchItem;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

@Converter
public class HotSearchItemListJsonConverter extends AbstractJsonConverter<List<HotSearchItem>> {

    public HotSearchItemListJsonConverter() {
        super(ArrayList::new, "[]");
    }

    @Override
    protected List<HotSearchItem> parse(String json) throws Exception {
        return MAPPER.readValue(json, new TypeReference<List<HotSearchItem>>() {});
    }
}
