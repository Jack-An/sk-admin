package com.dxj.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.dxj.constant.CommonConstant;
import com.dxj.dao.PictureDao;
import com.dxj.domain.entity.Picture;
import com.dxj.domain.dto.PictureQuery;
import com.dxj.exception.SkException;
import com.dxj.service.PictureService;
import com.dxj.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Sinkiang
 * @date 2018-12-27
 */
@Slf4j
@Service(value = "pictureService")
@CacheConfig(cacheNames = "picture")
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true, rollbackFor = Exception.class)
public class PictureServiceImpl implements PictureService {

    @Value("${smms.token}")
    private String token;

    private final PictureDao pictureDao;

    private static final String SUCCESS = "success";

    private static final String CODE = "code";

    private static final String MSG = "message";

    public PictureServiceImpl(PictureDao pictureDao) {
        this.pictureDao = pictureDao;
    }

    @Override
    public Map<String, Object> queryAll(PictureQuery criteria, Pageable pageable) {
        return PageUtil.toPage(pictureDao.findAll((root, criteriaQuery, criteriaBuilder) -> QueryHelp.getPredicate(root, criteria, criteriaBuilder), pageable));
    }

    @Override
    public List<Picture> queryAll(PictureQuery criteria) {
        return pictureDao.findAll((root, criteriaQuery, criteriaBuilder) -> QueryHelp.getPredicate(root, criteria, criteriaBuilder));
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Picture upload(MultipartFile multipartFile, String username) {
        File file = FileUtils.toFile(multipartFile);
        // 验证是否重复上传
        Picture picture = pictureDao.findByMd5Code(FileUtils.getMd5(file));
        if (picture != null) {
            return picture;
        }
        HashMap<String, Object> paramMap = new HashMap<>(1);
        paramMap.put("smfile", file);
        // 上传文件
        String result = HttpRequest.post(CommonConstant.SM_MS_URL + "/v2/upload")
                .header("Authorization", token)
                .form(paramMap)
                .timeout(20000)
                .execute().body();
        JSONObject jsonObject = JSONUtil.parseObj(result);
        if (!jsonObject.get(CODE).toString().equals(SUCCESS)) {
            throw new SkException(TranslatorUtil.translate(jsonObject.get(MSG).toString()));
        }
        picture = JSON.parseObject(jsonObject.get("data").toString(), Picture.class);
        picture.setSize(FileUtils.getSize(Integer.parseInt(picture.getSize())));
        picture.setUsername(username);
        picture.setMd5Code(FileUtils.getMd5(file));
        picture.setFilename(FileUtils.getFileNameNoEx(multipartFile.getOriginalFilename()) + "." + FileUtils.getExtensionName(multipartFile.getOriginalFilename()));
        pictureDao.save(picture);
        //删除临时文件
        FileUtils.del(file);
        return picture;

    }

    @Override
    public Picture findById(Long id) {
        Picture picture = pictureDao.findById(id).orElseGet(Picture::new);
        ValidationUtil.isNull(picture.getId(), "Picture", "id", id);
        return picture;
    }

    @Override
    public void deleteAll(Long[] ids) {
        for (Long id : ids) {
            Picture picture = findById(id);
            try {
                HttpUtil.get(picture.getDelete());
                pictureDao.delete(picture);
            } catch (Exception e) {
                pictureDao.delete(picture);
            }
        }
    }

    @Override
    public void synchronize() {
        //链式构建请求
        String result = HttpRequest.get(CommonConstant.SM_MS_URL + "/v2/upload_history")
                //头信息，多个头信息多次调用此方法即可
                .header("Authorization", token)
                .timeout(20000)
                .execute().body();
        JSONObject jsonObject = JSONUtil.parseObj(result);
        List<Picture> pictures = JSON.parseArray(jsonObject.get("data").toString(), Picture.class);
        for (Picture picture : pictures) {
            if (!pictureDao.existsByUrl(picture.getUrl())) {
                picture.setSize(FileUtils.getSize(Integer.parseInt(picture.getSize())));
                picture.setUsername("System Sync");
                picture.setMd5Code("");
                pictureDao.save(picture);
            }
        }
    }

    @Override
    public void download(List<Picture> queryAll, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Picture picture : queryAll) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("文件名", picture.getFilename());
            map.put("图片地址", picture.getUrl());
            map.put("文件大小", picture.getSize());
            map.put("操作人", picture.getUsername());
            map.put("高度", picture.getHeight());
            map.put("宽度", picture.getWidth());
            map.put("删除地址", picture.getDelete());
            map.put("创建日期", picture.getCreateTime());
            list.add(map);
        }
        FileUtils.downloadExcel(list, response);
    }
}
