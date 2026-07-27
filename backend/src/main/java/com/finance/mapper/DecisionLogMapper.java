package com.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finance.entity.DecisionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DecisionLogMapper extends BaseMapper<DecisionLog> {

    /**
     * 相似问答检索:keywords 由 Service 提取(可控、可注入由关键词列表长度与字符集保证),
     * 直接拼接 OR LIKE 串。scene 为空时查全部。
     */
    @Select("SELECT * FROM decision_log WHERE (" +
            " '' = #{scene} OR scene = #{scene} " +
            ") AND ( ${sql} ) ORDER BY created_at DESC LIMIT #{limit}")
    List<DecisionLog> findSimilar(@Param("scene") String scene,
                                   @Param("sql") String sql,
                                   @Param("limit") int limit);
}