package com.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 记账流水(随手记)。
 * 仅持久化原始字段;category(资产负债分类)由分类引擎在读取时现算也可,此处落库以便归档。
 */
@Data
@TableName("ledger")
public class Ledger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String date;          // YYYY-MM-DD
    private String type;          // income / expense
    private BigDecimal amount;
    private String description;   // 描述(接口字段名为 desc)
    private String category;      // 富爸爸分类
}
