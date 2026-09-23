package com.srm.sourcing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 选品标记。FAVORITE 每人每物料一条；HISTORY 用 seenAt 记下最近浏览。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_shelf_mark")
public class ShelfMark extends BaseEntity {
    private String ownerName;
    private String materialCode;
    private String kind;
    private LocalDateTime seenAt;
}
