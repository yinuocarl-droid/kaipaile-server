package com.kaipai.module.server.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.module.model.system.entity.AdminOperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminOperationLogMapper extends BaseMapper<AdminOperationLog> {
}
