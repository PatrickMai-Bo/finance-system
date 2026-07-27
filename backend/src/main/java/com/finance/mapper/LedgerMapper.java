package com.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finance.entity.Ledger;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LedgerMapper extends BaseMapper<Ledger> {
}
