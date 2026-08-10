package com.aivle.team08.backendspring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "legacy-db"})
class BackendSpringApplicationTests {

	@Test
	void contextLoads() {
	}

}
