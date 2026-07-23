package com.kaipai.service.ai.profileimport; public interface ProfileImportHttpTransport { String post(String endpoint,String apiKey,String body); class Timeout extends RuntimeException {} }
