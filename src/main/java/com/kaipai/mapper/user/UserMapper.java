package com.kaipai.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
