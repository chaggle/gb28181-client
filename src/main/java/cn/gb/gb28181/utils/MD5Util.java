package cn.gb.gb28181.utils;

import cn.hutool.crypto.digest.DigestUtil;
import org.apache.logging.log4j.util.Strings;

public class MD5Util {
    /**
     * MD5方法
     *
     * @param text 明文
     * @return 密文
     * @throws Exception
     */
    public static String MD5(String text) {
        //加密后的字符串
        String encodeStr = DigestUtil.md5Hex(text);
        return encodeStr;
    }

    /**
     * sip MD5 加密方法
     *
     * @param username 用户名
     * @param realm 服务器端传回
     * @param nonce 服务器
     * @param cnonce 客户端生成
     * @param requestMethod 请求方式
     * @param url url 链接
     *
     * @return 密文
     */
    public static String sipResponse(String username, String realm, String password, String nonce, String cnonce,
                                     String requestMethod, String url) {
        String text1 = username+ ":" + realm + ":" + password;
        String text2 = requestMethod + ":" + url;

        String hashText1 = MD5((text1));
        String hashText2 = MD5(text2);
        if (Strings.isBlank(cnonce)) {
            return MD5(hashText1 + ":" + nonce + ":" + hashText2);
        }
        return MD5(hashText1 + ":" + nonce + ":" + cnonce + ":" + hashText2);
    }

}