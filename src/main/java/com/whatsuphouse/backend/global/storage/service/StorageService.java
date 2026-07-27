package com.whatsuphouse.backend.global.storage.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String upload(MultipartFile file, String folder);
    String move(String tempPath, String targetFolder);
    String getPublicUrl(String path);
    void delete(String path);
}
