package com.example.aimailbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class  aimailboxApplication {

	public static void main(String[] args) {
		SpringApplication.run(aimailboxApplication.class, args);
	}

}
