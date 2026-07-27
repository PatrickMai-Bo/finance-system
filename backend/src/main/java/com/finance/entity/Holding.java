package com.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 存量资产负债表条目。
 * verdict(判定)由判定引擎在读取时现算,不落库。
 */
@Data
@TableName("holding")
public class Holding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bigType;        // 资产 / 负债
    private String name;
    private BigDecimal amount;     // 现值(负债填欠款余额,正数)
    private BigDecimal monthlyCashflow; // 每月净现金流(+入 / -出)
    private String note;
}
