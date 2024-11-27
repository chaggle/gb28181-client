package cn.gb.gb28181.stream;

import lombok.extern.slf4j.Slf4j;

import com.alibaba.fastjson2.JSON;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


@Slf4j
public class FfmpegStream {

    public static void push(String url, String stream) {
        // FFmpeg命令
        String[] command = {
                "ffmpeg",
                "-re",
                "-i", url,
                "-c:v", "libx264",
                "-f", "rtp_mpegts",
                stream
        };
        log.info("command : " + JSON.toJSONString(command));
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        log.info("processBuilder : " + processBuilder.toString());
        processBuilder.redirectErrorStream(true); // 合并错误流
        try {
            // 启动 ffmpeg 进程
            Process process = processBuilder.start();
            // 打印 ffmpeg 的标准输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            // 等待 ffmpeg 进程结束
            int exitCode = process.waitFor();
            System.out.println("FFmpeg exited with code " + exitCode);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

    }


    public static void main(String[] args) {

        String rtspUrl = "/Users/chaggle/Downloads/photo/2024-11-27.mp4";
        String myStream = "rtp://ip:port/mylive/outTest111110001.mp4";

        push(rtspUrl, myStream);


    }

}