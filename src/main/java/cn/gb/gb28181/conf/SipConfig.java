package cn.gb.gb28181.conf;

import lombok.Data;

@Data
public class SipConfig {

    private String domain;
    private String displayName;
    private String username;
    private String password;
    private String proxy;

}
