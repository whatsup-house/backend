package com.whatsuphouse.backend.global.storage.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.Map;

public interface StorageService {
    String upload(MultipartFile file, String folder);
    /** 비공개 버킷 업로드. prefix 아래 랜덤 파일명으로 저장하고 버킷 내 경로를 돌려준다. 공개 URL 없음. */
    String uploadPrivate(MultipartFile file, String bucket, String prefix);
    /** 비공개 버킷 서명 URL 일괄 발급. 실패한 경로는 결과에서 빠진다. */
    Map<String, String> createSignedUrls(String bucket, Collection<String> paths, int expiresInSeconds);
    String move(String tempPath, String targetFolder);
    String getPublicUrl(String path);
    void delete(String path);
}
