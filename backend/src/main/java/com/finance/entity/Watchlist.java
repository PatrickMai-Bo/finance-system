package com.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 自选清单(自选股 / 自选基)。
 * type: stock(自选股) / fund(自选基)。
 */
@Data
@TableName("watchlist")
public class Watchlist {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;        // stock / fund
    private String name;
    private String code;
    private String category;
    private BigDecimal cost;        // 成本价
    private BigDecimal amount;      // 持仓金额
    private BigDecimal targetPrice; // 目标/心理价位
    private String note;
}
