package com.kaipai.service.ai; public interface ProfileImportRateLimiter { boolean allow(Long userId,int dailyLimit); }
