package com.kaipai.service.adminauth;

import com.kaipai.model.adminauth.dto.AdminLoginReqDTO;
import com.kaipai.model.adminauth.dto.AdminLoginRespDTO;
import com.kaipai.model.adminauth.dto.AdminSessionInfoDTO;
import jakarta.servlet.http.HttpServletRequest;

public interface AdminAuthService {

    AdminLoginRespDTO login(AdminLoginReqDTO dto, HttpServletRequest request);

    AdminSessionInfoDTO currentSession();
}
