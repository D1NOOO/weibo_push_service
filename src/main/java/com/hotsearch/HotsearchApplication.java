package com.hotsearch;

import com.hotsearch.service.AuthService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HotsearchApplication {

    private final AuthService authService;

    public HotsearchApplication(AuthService authService) {
        this.authService = authService;
    }

    public static void main(String[] args) {
        SpringApplication.run(HotsearchApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        authService.initDefaultAdmin();
    }
}
