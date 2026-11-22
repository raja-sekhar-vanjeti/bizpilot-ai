package com.bizpilot;

import com.bizpilot.config.DatabaseEnvironmentListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BizPilotApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(BizPilotApplication.class);
		app.addListeners(new DatabaseEnvironmentListener());
		app.run(args);
	}

}
