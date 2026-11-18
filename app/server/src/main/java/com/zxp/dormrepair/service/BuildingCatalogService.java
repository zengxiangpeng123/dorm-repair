package com.zxp.dormrepair.service;

import com.zxp.dormrepair.store.entity.BuildingFloorRow;
import com.zxp.dormrepair.store.entity.BuildingRow;
import com.zxp.dormrepair.store.mapper.BuildingFloorMapper;
import com.zxp.dormrepair.store.mapper.BuildingMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 楼栋与楼层目录：MySQL 读表，内存/异常时用同一份内置目录。 */
@Service
public class BuildingCatalogService {
    private static final Logger log = LoggerFactory.getLogger(BuildingCatalogService.class);
    private final boolean useDb;
    private final BuildingMapper buildings;
    private final BuildingFloorMapper floors;

    public BuildingCatalogService(@Value("${app.store:mysql}") String store,
                                  BuildingMapper buildings, BuildingFloorMapper floors) {
        this.useDb = "mysql".equalsIgnoreCase(store);
        this.buildings = buildings;
        this.floors = floors;
    }

    public List<Map<String, Object>> all() {
        if (useDb) {
            try {
                return fromDb();
            } catch (RuntimeException e) {
                log.warn("读楼栋楼层失败，使用内置目录：{}", e.getMessage());
            }
        }
        return fallback();
    }

    private List<Map<String, Object>> fromDb() {
        List<BuildingRow> bs = buildings.selectList(null);
        Map<String, List<Integer>> byBuilding = new TreeMap<>();
        for (BuildingFloorRow f : floors.selectList(null)) {
            byBuilding.computeIfAbsent(f.getBuildingId(), k -> new ArrayList<>()).add(f.getFloor());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (BuildingRow b : bs) {
            List<Integer> fs = byBuilding.getOrDefault(b.getId(), List.of()).stream()
                    .sorted((a, c) -> Integer.compare(c, a)).toList();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("name", b.getName());
            m.put("floors", fs);
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> fallback() {
        return List.of(
                Map.of("id", "B7", "name", "东区7号楼", "floors", List.of(5,4,3,2)),
                Map.of("id", "B8", "name", "东区8号楼", "floors", List.of(3,2,1)));
    }
}