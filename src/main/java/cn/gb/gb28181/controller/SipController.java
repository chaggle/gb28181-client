package cn.gb.gb28181.controller;

import cn.gb.gb28181.service.SipgateSipListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gb")
@Validated
@Slf4j
public class SipController {

    private static SipgateSipListener sipgateSipListener;

    static {
        try {
            sipgateSipListener = new SipgateSipListener();
            sipgateSipListener.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PostMapping("/init")
    public void register() throws Exception {
        sipgateSipListener.register();
    }

}
