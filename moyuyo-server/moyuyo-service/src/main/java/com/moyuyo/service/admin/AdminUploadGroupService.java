package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 图片空间分组服务。
 * <p>
 * 图片文件本身存储在磁盘，本服务只维护「图片 URL -> 分组」映射与分组定义。
 */
public interface AdminUploadGroupService {

    /** 查询全部分组（含各分组图片数量），按 sortOrder 升序 */
    List<Map<String, Object>> listGroups();

    /** 创建分组，返回分组 ID */
    Long createGroup(String name);

    /** 重命名分组 */
    void renameGroup(Long id, String name);

    /** 删除分组（图片保留并回到未分组） */
    void deleteGroup(Long id);

    /** 将图片移动到指定分组；groupId 为 null/0 表示移出分组 */
    void assignGroup(List<String> urls, Long groupId);

    /** 查询全部「图片 URL -> 分组ID」映射 */
    Map<String, Long> groupIdByUrl();

    /** 上传成功后绑定分组（groupId 为空/0 时忽略） */
    void bindOnUpload(String url, Long groupId);

    /** 删除图片后清理分组映射 */
    void unbindOnDelete(String url);
}
