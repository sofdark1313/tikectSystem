package com.tikectsystem.jwt;

import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.exception.TikectsystemFrameException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: token工具
 * @author: 阿星不是程序员
 **/
@Slf4j
public class TokenUtil {

    /**
     * 指定签名的时候使用的签名算法，也就是header那部分。
     *
     */
    private static final SignatureAlgorithm SIGNATURE_ALGORITHM = SignatureAlgorithm.HS256;
    /**
     * 用户登录成功后生成Jwt
     * 使用Hs256算法
     *
     * @param id        标识
     * @param info      登录成功的user对象
     * @param ttlMillis jwt过期时间
     * @param tokenSecret 私钥
     * @return
     */
    public static String createToken(String id, String info, long ttlMillis, String tokenSecret) {
        //生成JWT的时间
        long nowMillis = System.currentTimeMillis();

        //创建一个JwtBuilder，设置jwt的body
        JwtBuilder builder = Jwts.builder()
                //如果有私有声明，一定要先设置这个自己创建的私有的声明，这个是给builder的claim赋值，一旦写在标准的声明赋值之后，就是覆盖了那些标准的声明的
//                .setClaims(claims)
                //设置jti(JWT ID)：是JWT的唯一标识，根据业务需要，这个可以设置为一个不重复的值，主要用来作为一次性token,从而回避重放攻击。
                .setId(id)
                //iat: jwt的签发时间
                .setIssuedAt(new Date(nowMillis))
                //代表这个JWT的主体，即它的所有人，这个是一个json格式的字符串。
                .setSubject(info)
                //设置签名使用的签名算法和签名使用的秘钥
                .signWith(SIGNATURE_ALGORITHM, tokenSecret);
        if (ttlMillis >= 0) {
            //设置过期时间
            builder.setExpiration(new Date(nowMillis + ttlMillis));
        }
        return builder.compact();
    }


    /**
     * Token的解密
     *
     * @param token 加密后的token
     * @param tokenSecret 私钥
     * @return
     */
    public static String parseToken(String token, String tokenSecret) {
        try {
            return Jwts.parser()
                    //设置签名的秘钥
                    .setSigningKey(tokenSecret)
                    //设置需要解析的jwt
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        }catch (ExpiredJwtException jwtException) {
            log.error("parseToken error",jwtException);
            throw new TikectsystemFrameException(BaseCode.TOKEN_EXPIRE);
        } catch (JwtException | IllegalArgumentException e) {
            log.error("parseToken invalid token",e);
            throw new TikectsystemFrameException(BaseCode.LOGIN_USER_NOT_EXIST);
        }

    }
}
