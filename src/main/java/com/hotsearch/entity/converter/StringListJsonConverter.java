package com.hotsearch.entity.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

@Converter
public class StringListJsonConverter extends AbstractJsonConverter<List<String>> {

    public StringListJsonConverter() {
        super(ArrayList::new, "[]");
    }

    @Override
    protected List<String> parse(String json) throws Exception {
        return MAPPER.readValue(json, new TypeReference<List<String>>() {});
    }
}
