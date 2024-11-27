package cn.gb.gb28181;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class Gb28181Application {

    public static void main(String[] args) {
        SpringApplication.run(Gb28181Application.class, args);
    }

}
