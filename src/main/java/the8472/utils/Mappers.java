package the8472.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public abstract class Mappers {

    public static final ObjectMapper jsonMapper;

    static {
        jsonMapper = new ObjectMapper();
    }

    public static <T> T parseJson(String json, TypeReference<T> type) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static JsonNode parseJson(String json) {
        if (isBlank(json))
            return MissingNode.getInstance();
        try {
            return jsonMapper.readTree(json);
        } catch (IOException e) {
            log.warn("parse json failure: {}", json, e);
            return MissingNode.getInstance();
        }
    }

    private static boolean isBlank(String json) {
        return json == null || json.isEmpty();
    }

    public static String json(Object obj) {
        return json(obj, jsonMapper);
    }

    private static String json(Object obj, ObjectMapper mapper) {
        if (obj == null)
            return null;
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
