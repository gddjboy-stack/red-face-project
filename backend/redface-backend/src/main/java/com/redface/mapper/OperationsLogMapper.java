package com.redface.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * operations_log 操作审计日志 Mapper。
 */
@Mapper
public interface OperationsLogMapper {

    /**
     * 插入一条操作审计日志。
     *
     * @param operatorId 操作人 ID
     * @param actionType 操作类型
     * @param target     操作目标
     * @param detail     JSON 字符串形式的操作详情
     * @param reason     操作原因
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO operations_log (operator_id, action_type, target, detail, reason)
            VALUES (#{operatorId}, #{actionType}, #{target}, #{detail}, #{reason})
            """)
    int insert(@Param("operatorId") String operatorId,
               @Param("actionType") String actionType,
               @Param("target") String target,
               @Param("detail") String detail,
               @Param("reason") String reason);

    /**
     * 统计指定操作类型的日志条数，用于 JUnit 验证。
     *
     * @param actionType 操作类型
     * @return 日志条数
     */
    @Select("SELECT COUNT(*) FROM operations_log WHERE action_type = #{actionType}")
    long countByActionType(@Param("actionType") String actionType);
}
