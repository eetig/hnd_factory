package org.example;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient // 开启Nacos服务注册
@EnableFeignClients    // 开启Feign扫描，非常关键！不加Feign接口不会生效
@MapperScan("org.example.mapper") // 扫描MyBatis的Mapper接口
@EnableScheduling      // 开启定时任务（导入缓存过期清理）
public class HndFactoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(HndFactoryApplication.class, args);
    }
}
