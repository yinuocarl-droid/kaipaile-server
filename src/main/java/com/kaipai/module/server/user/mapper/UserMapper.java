package com.kaipai.module.server.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.module.model.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
