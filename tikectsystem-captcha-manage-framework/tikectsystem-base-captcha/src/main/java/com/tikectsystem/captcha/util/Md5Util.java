package com.tikectsystem.captcha.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;


/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: MD5工具类
 * @author: 阿星不是程序员
 **/
public class Md5Util {
    private static final Logger logger = LoggerFactory.getLogger(Md5Util.class);

    /**
     * 获取指定字符串的md5值
     * @param dataStr 明文
     * @return String
     */
    public static String md5(String dataStr) {
        if (dataStr == null) {
            return "";
        }
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.update(dataStr.getBytes(StandardCharsets.UTF_8));
            byte[] s = m.digest();
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < s.length; i++) {
                result.append(Integer.toHexString((0x000000FF & s[i]) | 0xFFFFFF00).substring(6));
            }
            return result.toString();
        } catch (Exception e) {
            logger.error("md5 encrypt error", e);
        }
        return "";
    }

    /**
     * 获取指定字符串的md5值, md5(str+salt)
     * @param dataStr 明文
     * @return String
     */
    public static String md5WithSalt(String dataStr,String salt) {
        return md5(dataStr + salt);
    }

}
