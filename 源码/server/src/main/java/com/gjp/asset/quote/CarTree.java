package com.gjp.asset.quote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 车型三级：品牌 / 车系 / 年款。内置常见车型，不依赖被墙的二手网站。
 */
@Component
public class CarTree {

    private volatile List<Map<String, Object>> tree;

    public List<Map<String, Object>> tree() {
        if (tree == null) {
            synchronized (this) {
                if (tree == null) {
                    tree = load();
                }
            }
        }
        return tree;
    }

    private List<Map<String, Object>> load() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ClassPathResource res = new ClassPathResource("asset/car-tree.json");
            try (InputStream in = res.getInputStream()) {
                return mapper.readValue(in, new TypeReference<List<Map<String, Object>>>() { });
            }
        } catch (Exception e) {
            throw new IllegalStateException("无法加载车型目录", e);
        }
    }
}
