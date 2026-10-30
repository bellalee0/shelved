package com.shelved;

import com.shelved.common.config.ClockConfig;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShelvedApplication {

    public static void main(String[] args) {
        // 서버 기준 시간대 (Asia/Seoul)
        TimeZone.setDefault(TimeZone.getTimeZone(ClockConfig.SERVER_ZONE_ID));
        SpringApplication.run(ShelvedApplication.class, args);
    }

}
