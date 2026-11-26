package com.memcyco.scheduler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.memcyco.scheduler.entity.timing.ScheduleTiming;
import com.memcyco.scheduler.scheduling.TimingStrategies;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Identity hashes used to detect "the same thing":
 * <ul>
 *     <li><b>fingerprint</b> = task + parameters + timing. Two active schedulings may not share it
 *         (duplicate prevention).</li>
 *     <li><b>run key</b> = task + parameters. One scheduling may not have two RUNNING executions with the same
 *         run key (runtime lock).</li>
 * </ul>
 * Parameters must already be normalized by {@code ParameterValidator} (defaults applied, values normalized);
 * here they are serialized with sorted keys. Timings are reduced to their semantic canonical form.
 */
@Service
public class FingerprintService {

    private static final ObjectMapper CANONICAL_JSON = JsonMapper.builder()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .build();

    private final TimingStrategies timingStrategies;

    public FingerprintService(TimingStrategies timingStrategies) {
        this.timingStrategies = timingStrategies;
    }

    public String fingerprint(String taskKey, Map<String, Object> normalizedParameters, ScheduleTiming timing) {
        return sha256(taskKey + "\n" + canonicalJson(normalizedParameters) + "\n"
                + timingStrategies.canonicalForm(timing));
    }

    public String runKey(String taskKey, Map<String, Object> normalizedParameters) {
        return sha256(taskKey + "\n" + canonicalJson(normalizedParameters));
    }

    private static String canonicalJson(Map<String, Object> parameters) {
        try {
            // Copy into a naturally ordered map: Jackson does not re-sort maps that are already a SortedMap.
            return CANONICAL_JSON.writeValueAsString(new TreeMap<>(parameters));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Parameters are not serializable", e);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
