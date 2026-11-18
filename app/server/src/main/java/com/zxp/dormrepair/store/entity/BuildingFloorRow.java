package com.zxp.dormrepair.store.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("t_building_floor")
public class BuildingFloorRow {
    @TableId("building_id")
    private String buildingId;
    private Integer floor;
}