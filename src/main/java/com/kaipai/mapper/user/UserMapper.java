package com.kaipai.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("""
            SELECT *
            FROM `user`
            WHERE user_id = #{userId}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    User selectActiveByIdForUpdate(@Param("userId") Long userId);
}
