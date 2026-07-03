package com.tikectsystem.util;

import cn.hutool.crypto.asymmetric.SignAlgorithm;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.exception.TikectsystemFrameException;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RSA 签名工具。
 * 仅负责参数签名与验签，业务密钥必须由调用方从受控配置读取后传入。
 */
@Slf4j
public class RsaSignTool {

    private static final String SIGN_TYPE = "RSA";

    private static final String CHARSET = "utf-8";

    /**
     * 对参数集合生成 RSA-SHA256 签名。
     *
     * @param params 待签名参数
     * @param privateKey PKCS8 格式私钥
     * @return Base64 编码后的签名
     */
    public static String rsaSign256(Map<String, String> params, String privateKey) {
        String content = buildParam(params);
        return rsaSign256(content, privateKey);
    }

    /**
     * 对字符串内容生成 RSA-SHA256 签名。
     *
     * @param content 待签名内容
     * @param privateKey PKCS8 格式私钥
     * @return Base64 编码后的签名
     */
    public static String rsaSign256(String content, String privateKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKey);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(SIGN_TYPE);
            Signature signature = Signature.getInstance(SignAlgorithm.SHA256withRSA.getValue());
            signature.initSign(keyFactory.generatePrivate(keySpec));
            signature.update(content.getBytes(CHARSET));
            byte[] sign = signature.sign();
            return Base64.getEncoder().encodeToString(sign);
        } catch (Exception e) {
            log.error("sign256 error", e);
            throw new TikectsystemFrameException(BaseCode.GENERATE_RSA_SIGN_ERROR);
        }
    }

    /**
     * 构建用于签名的参数字符串。
     *
     * @param params 待签名参数
     * @return 按键排序后的 key=value 字符串
     */
    private static String buildParam(Map<String, String> params) {
        if (params.isEmpty()) {
            return "";
        }
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size() - 1; i++) {
            String key = keys.get(i);
            String value = params.get(key);
            sb.append(buildKeyValue(key, value, false));
            sb.append("&");
        }

        String tailKey = keys.get(keys.size() - 1);
        String tailValue = params.get(tailKey);
        sb.append(buildKeyValue(tailKey, tailValue, false));

        return sb.toString();
    }

    /**
     * 校验参数集合中的 RSA-SHA256 签名。
     *
     * @param params 包含 sign 字段的参数
     * @param publicKey X509 格式公钥
     * @return 签名是否通过
     */
    public static boolean verifyRsaSign256(Map<String, String> params, String publicKey) {
        try {
            String sign = params.get("sign");
            String content = getSignCheckContent(params);
            return verifyRsaSign256(content.getBytes(CHARSET), sign, publicKey);
        } catch (Exception e) {
            log.error("verifyRsaSign256 error", e);
            throw new TikectsystemFrameException(BaseCode.RSA_SIGN_ERROR);
        }
    }

    /**
     * 校验指定字节内容的 RSA-SHA256 签名。
     *
     * @param dataBytes 原始待验签字节
     * @param sign Base64 编码签名
     * @param publicKey X509 格式公钥
     * @return 签名是否通过
     */
    public static boolean verifyRsaSign256(byte[] dataBytes, String sign, String publicKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        byte[] signByte = Base64.getDecoder().decode(sign);
        byte[] encodedKey = Base64.getDecoder().decode(publicKey);
        Signature signature = Signature.getInstance(SignAlgorithm.SHA256withRSA.getValue());
        KeyFactory keyFactory = KeyFactory.getInstance(SIGN_TYPE);
        PublicKey parsedPublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(encodedKey));
        signature.initVerify(parsedPublicKey);
        signature.update(dataBytes);
        return signature.verify(signByte);
    }

    /**
     * 拼接签名单个键值对。
     *
     * @param key 参数键
     * @param value 参数值
     * @param isEncode 是否 URL 编码
     * @return key=value 字符串
     */
    private static String buildKeyValue(String key, String value, boolean isEncode) {
        StringBuilder sb = new StringBuilder();
        sb.append(key);
        sb.append("=");
        if (isEncode) {
            try {
                sb.append(URLEncoder.encode(value, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                sb.append(value);
            }
        } else {
            sb.append(value);
        }
        return sb.toString();
    }

    /**
     * 获取验签内容。
     * sign 与 files 字段不参与签名，保持与外部协议约定一致。
     *
     * @param params 原始请求参数
     * @return 待验签字符串
     */
    private static String getSignCheckContent(Map<String, String> params) {
        if (params == null) {
            return "";
        }
        Map<String, String> checkParams = new HashMap<>(params);
        checkParams.remove("sign");
        checkParams.remove("files");

        return buildParam(checkParams);
    }
}
