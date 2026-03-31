package com.kaipai.module.server.membership.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.module.model.membership.entity.MembershipChangeLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MembershipChangeLogMapper extends BaseMapper<MembershipChangeLog> {
}
