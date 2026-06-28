package com.redface.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IdempotencyMapper {
    @Insert("INSERT INTO idempotency_ledger (idempotency_key, action_type, result_data) VALUES (#{key}, #{actionType}, #{resultData})")
    int insert(@Param("key") String key, @Param("actionType") String actionType, @Param("resultData") String resultData);

    @Select("SELECT result_data FROM idempotency_ledger WHERE idempotency_key = #{key}")
    String findResult(@Param("key") String key);
    @org.apache.ibatis.annotations.Update("UPDATE idempotency_ledger SET result_data = #{resultData} WHERE idempotency_key = #{key}")
    int updateResult(@Param("key") String key, @Param("resultData") String resultData);
}
