package com.demo.sliceupload.service.impl;

import cn.hutool.cache.Cache;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.demo.sliceupload.param.MultipartFileParam;
import com.demo.sliceupload.service.StorageService;
import com.demo.sliceupload.utils.Constants;
import com.demo.sliceupload.utils.FileMD5Util;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by 超文 on 2017/5/2.
 */
@Service
public class StorageServiceImpl implements StorageService {

    private final Logger logger = LoggerFactory.getLogger(StorageServiceImpl.class);
    // 保存文件的根目录
    private Path rootPaht;

    //这个必须与前端设定的值一致
    @Value("${breakpoint.upload.chunkSize}")
    private long CHUNK_SIZE;

    @Value("${breakpoint.upload.dir}")
    private String finalDirPath;

    @Autowired
    private Cache<String,String> cache;
    @Autowired
    public StorageServiceImpl(@Value("${breakpoint.upload.dir}") String location) {
        this.rootPaht = Paths.get(location);
    }

    @Override
    public void deleteAll() {
        logger.info("开发初始化清理数据，start");
        FileSystemUtils.deleteRecursively(rootPaht.toFile());
        cache.remove(Constants.FILE_UPLOAD_STATUS);
        cache.remove(Constants.FILE_MD5_KEY);
        logger.info("开发初始化清理数据，end");
    }

    @Override
    public void init() {
        try {
            Files.createDirectory(rootPaht);
        } catch (FileAlreadyExistsException e) {
            logger.error("文件夹已经存在了，不用再创建。");
        } catch (IOException e) {
            logger.error("初始化root文件夹失败。", e);
        }
    }

    @Override
    public void uploadFileRandomAccessFile(MultipartFileParam param) throws IOException {
        String fileName = param.getName();
        String tempDirPath = finalDirPath + param.getMd5();
        String tempFileName = fileName + "_tmp";
        File tmpDir = new File(tempDirPath);
        File tmpFile = new File(tempDirPath, tempFileName);
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }

        RandomAccessFile accessTmpFile = new RandomAccessFile(tmpFile, "rw");
        long offset = CHUNK_SIZE * param.getChunk();
        //定位到该分片的偏移量
        accessTmpFile.seek(offset);
        //写入该分片数据
        accessTmpFile.write(param.getFile().getBytes());
        // 释放
        accessTmpFile.close();

        boolean isOk = checkAndSetUploadProgress(param, tempDirPath);
        if (isOk) {
            boolean flag = renameFile(tmpFile, fileName);
            System.out.println("upload complete !!" + flag + " name=" + fileName);
        }
    }

    @Override
    public void uploadFileByMappedByteBuffer(MultipartFileParam param) throws IOException {
        String fileName = param.getName();
        String uploadDirPath = finalDirPath + param.getMd5();
        String tempFileName = fileName + "_tmp";
        File tmpDir = new File(uploadDirPath);
        File tmpFile = new File(uploadDirPath, tempFileName);
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }

        RandomAccessFile tempRaf = new RandomAccessFile(tmpFile, "rw");
        FileChannel fileChannel = tempRaf.getChannel();

        //写入该分片数据
        long offset = CHUNK_SIZE * param.getChunk();
        byte[] fileData = param.getFile().getBytes();
        MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, offset, fileData.length);
        mappedByteBuffer.put(fileData);
        // 释放
        FileMD5Util.freedMappedByteBuffer(mappedByteBuffer);
        fileChannel.close();

        boolean isOk = checkAndSetUploadProgress(param, uploadDirPath);
        if (isOk) {
            boolean flag = renameFile(tmpFile, fileName);
            System.out.println("upload complete !!" + flag + " name=" + fileName);
        }
    }

    @Override
    public void tempStorageByMd5(MultipartFileParam fileParam) throws IOException {
        String md5Dir = getMd5Dir(fileParam.getMd5());

        // 新建文件名
        String tmpFileName = fileParam.getChunk()  + ".tmp";
        File tmpFile = new File(md5Dir + File.separator + tmpFileName);
        if (tmpFile.exists()){
            return;
        }

        try (BufferedOutputStream outputStream = FileUtil.getOutputStream(tmpFile)) {
            IoUtil.copy(fileParam.getFile().getInputStream(), outputStream);
        }
    }

    @Override
    public void merge(String md5, String fileName) throws IOException {
        String md5Dir = getMd5Dir(md5);
        if (!FileUtil.exist(md5Dir)){
            return;
        }

        // 获取md5下面的所有文件
        List<String> pathList = FileUtil.listFileNames(md5Dir)
                .stream()
                .sorted((o1, o2) -> {
                    Integer num1 = Integer.parseInt(StrUtil.removeSuffix(o1, ".tmp"));
                    Integer num2 = Integer.parseInt(StrUtil.removeSuffix(o2, ".tmp"));
                    return num1.compareTo(num2);
                })
                .map(f -> md5Dir + File.separator + f)
                .collect(Collectors.toList());

        // 合并文件
        File mergeFile = new File(md5Dir + File.separator + fileName);
        try (BufferedOutputStream outputStream = FileUtil.getOutputStream(mergeFile)) {
            for (String path : pathList) {
                File file = new File(path);
                BufferedInputStream inputStream = FileUtil.getInputStream(file);
                IoUtil.copy(inputStream, outputStream);
                // 拷贝完关闭输入流
                inputStream.close();
            }
        }
    }

    private String getMd5Dir(String md5) {
        File tmpDir = FileUtil.mkdir("/data/tmp");

        String md5Dir = tmpDir.getPath() + File.separator + md5;
        File file = FileUtil.mkdir(md5Dir);
        return file.getPath();
    }
    /**
     * 检查并修改文件上传进度
     *
     * @param param
     * @param uploadDirPath
     * @return
     * @throws IOException
     */
    private boolean checkAndSetUploadProgress(MultipartFileParam param, String uploadDirPath) throws IOException {
        String fileName = param.getName();
        File confFile = new File(uploadDirPath, fileName + ".conf");
        RandomAccessFile accessConfFile = new RandomAccessFile(confFile, "rw");
        //把该分段标记为 true 表示完成
        System.out.println("set part " + param.getChunk() + " complete");
        accessConfFile.setLength(param.getChunks());
        accessConfFile.seek(param.getChunk());
        accessConfFile.write(Byte.MAX_VALUE);

        //completeList 检查是否全部完成,如果数组里是否全部都是(全部分片都成功上传)
        byte[] completeList = FileUtils.readFileToByteArray(confFile);
        byte isComplete = Byte.MAX_VALUE;
        for (int i = 0; i < completeList.length && isComplete == Byte.MAX_VALUE; i++) {
            //与运算, 如果有部分没有完成则 isComplete 不是 Byte.MAX_VALUE
            isComplete = (byte) (isComplete & completeList[i]);
            System.out.println("check part " + i + " complete?:" + completeList[i]);
        }

        accessConfFile.close();
        if (isComplete == Byte.MAX_VALUE) {
            cache.put(param.getMd5(), "true");
            cache.put(Constants.FILE_MD5_KEY + param.getMd5(), uploadDirPath + "/" + fileName);
            return true;
        } else {
            if (!cache.containsKey(param.getMd5())) {
                cache.put(param.getMd5(), "false");
            }
            if (cache.containsKey(Constants.FILE_MD5_KEY + param.getMd5())) {
                cache.put(Constants.FILE_MD5_KEY + param.getMd5(), uploadDirPath + "/" + fileName + ".conf");
            }
            return false;
        }
    }

    /**
     * 文件重命名
     *
     * @param toBeRenamed   将要修改名字的文件
     * @param toFileNewName 新的名字
     * @return
     */
    public boolean renameFile(File toBeRenamed, String toFileNewName) {
        //检查要重命名的文件是否存在，是否是文件
        if (!toBeRenamed.exists() || toBeRenamed.isDirectory()) {
            logger.info("File does not exist: " + toBeRenamed.getName());
            return false;
        }
        String p = toBeRenamed.getParent();
        File newFile = new File(p + File.separatorChar + toFileNewName);
        //修改文件名
        return toBeRenamed.renameTo(newFile);
    }

}
