package com.hotsearch.entity.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * JSON TEXT 列与 Java 对象的通用转换基类。
 * 解析失败时记录告警并回退到空值（与历史行为一致，避免单行脏数据拖垮整表查询）。
 */
abstract class AbstractJsonConverter<T> implements AttributeConverter<T, String> {

    private static final Logger log = LoggerFactory.getLogger(AbstractJsonConverter.class);

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    private final Supplier<T> emptyValue;
    private final String emptyJson;

    protected AbstractJsonConverter(Supplier<T> emptyValue, String emptyJson) {
        this.emptyValue = emptyValue;
        this.emptyJson = emptyJson;
    }

    protected abstract T parse(String json) throws Exception;

    @Override
    public String convertToDatabaseColumn(T attribute) {
        if (attribute == null) return emptyJson;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            log.warn("JSON 序列化失败，写入空值: {}", e.getMessage());
            return emptyJson;
        }
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return emptyValue.get();
        try {
            return parse(dbData);
        } catch (Exception e) {
            log.warn("JSON 解析失败，回退为空值: {}", e.getMessage());
            return emptyValue.get();
        }
    }
}
