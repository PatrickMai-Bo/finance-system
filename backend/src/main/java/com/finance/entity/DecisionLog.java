package com.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 决策日志:每次用户提问 + AI 五阶分析,持久化以便后续检索相似问答、
 * 在重复问题时由 AiService prepend 红色警示提醒用户避免重复犯错。
 */
@Data
@TableName("decision_log")
public class DecisionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 决策场景(投资理财/职业发展/大额消费/人际关系/健康生活/学习成长) */
    private String scene;
    /** 用户原问题 */
    private String question;
    /** AI 五阶完整分析文本 */
    private String answer;
    /** 解析出的最终建议(做 / 不做 / 再等等) */
    private String verdict;
    /** 实际调用的模型名称 */
    private String model;
    /** 提问时间 */
    private Date createdAt;
}