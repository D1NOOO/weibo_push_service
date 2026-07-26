package com.hotsearch.entity.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/** 兼容历史数据：数组元素既可能是数字也可能是数字字符串（如 ["1","2"]），Jackson 均可读取。 */
@Converter
public class LongListJsonConverter extends AbstractJsonConverter<List<Long>> {

    public LongListJsonConverter() {
        super(ArrayList::new, "[]");
    }

    @Override
    protected List<Long> parse(String json) throws Exception {
        return MAPPER.readValue(json, new TypeReference<List<Long>>() {});
    }
}
