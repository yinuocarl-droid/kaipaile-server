package com.kaipai.module.server.adminauth.service;

import com.kaipai.module.model.adminauth.dto.AdminLoginReqDTO;
import com.kaipai.module.model.adminauth.dto.AdminLoginRespDTO;
import com.kaipai.module.model.adminauth.dto.AdminSessionInfoDTO;
import jakarta.servlet.http.HttpServletRequest;

public interface AdminAuthService {

    AdminLoginRespDTO login(AdminLoginReqDTO dto, HttpServletRequest request);

    AdminSessionInfoDTO currentSession();
}
