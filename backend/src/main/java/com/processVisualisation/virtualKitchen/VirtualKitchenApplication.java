package com.processVisualisation.virtualKitchen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Virtual Kitchen Spring Boot application.
 * Bootstraps the Spring application context, auto-configuration, and component
 * scanning for all feature packages (ai, auth, common, kitchen, recipe, restclient, store).
 * {@code @EnableScheduling} activates {@code @Scheduled} jobs such as
 * {@code ai.credit.CreditCycleResetJob}.
 */
@SpringBootApplication
@EnableScheduling
public class VirtualKitchenApplication {

	/**
	 * Starts the Spring Boot application.
	 *
	 * @param args command-line arguments passed through to {@link SpringApplication#run(Class, String...)}
	 */
	public static void main(String[] args) {
		SpringApplication.run(VirtualKitchenApplication.class, args);
	}

}
