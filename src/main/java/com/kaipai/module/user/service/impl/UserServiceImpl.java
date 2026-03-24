package com.kaipai.module.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.user.entity.User;
import com.kaipai.module.user.mapper.UserMapper;
import com.kaipai.module.user.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
