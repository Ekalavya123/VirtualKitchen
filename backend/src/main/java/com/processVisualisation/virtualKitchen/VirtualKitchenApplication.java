package com.processVisualisation.virtualKitchen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Virtual Kitchen Spring Boot application.
 * Bootstraps the Spring application context, auto-configuration, and component
 * scanning for all feature packages (ai, auth, common, kitchen, recipe, restclient, store).
 */
@SpringBootApplication
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
