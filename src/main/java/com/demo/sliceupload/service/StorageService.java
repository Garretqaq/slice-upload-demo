package com.demo.sliceupload.service;


import com.demo.sliceupload.param.MultipartFileParam;

import java.io.IOException;

/**
 * 存储操作的service
 * Created by 超文 on 2017/5/2.
 */
public interface StorageService {

    /**
     * 删除全部数据
     */
    void deleteAll();

    /**
     * 初始化方法
     */
    void init();

    /**
     * 上传文件方法1
     *
     * @param param
     * @throws IOException
     */
    void uploadFileRandomAccessFile(MultipartFileParam param) throws IOException;

    /**
     * 上传文件方法2
     * 处理文件分块，基于MappedByteBuffer来实现文件的保存
     *
     * @param param
     * @throws IOException
     */
    void uploadFileByMappedByteBuffer(MultipartFileParam param) throws IOException;

    /**
     * 根据md5存储临时文件
     * @param fileParam 文件参数
     */
    void tempStorageByMd5(MultipartFileParam fileParam) throws IOException;

    /**
     * 根据md5合并
     *
     * @param md5      md5
     * @param fileName 文件名称
     */
    void merge(String md5, String fileName) throws IOException;
}
