package com.finiq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FiniqApplication {

    public static void main(String[] args) {

        SpringApplication.run(FiniqApplication.class, args);

        System.out.println();
        System.out.println("==================================");
        System.out.println("       FINIQ IS RUNNING!");
        System.out.println("==================================");
        System.out.println("Website: http://localhost:8080");
        System.out.println("==================================");
    }
}