package com.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finance.entity.Holding;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HoldingMapper extends BaseMapper<Holding> {
}
