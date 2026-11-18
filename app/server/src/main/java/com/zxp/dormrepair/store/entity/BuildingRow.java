package com.zxp.dormrepair.store.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("t_building")
public class BuildingRow {
    @TableId
    private String id;
    private String name;
}