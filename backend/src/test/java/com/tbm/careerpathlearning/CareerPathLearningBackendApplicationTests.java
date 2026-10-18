package com.tbm.careerpathlearning;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the whole application against the configured database. The training daily jobs and notification
 * emails stay off, so a test run never lapses grants, sends reminders or emails anyone.
 */
@SpringBootTest(properties = {
		"training.daily-jobs.enabled=false",
		"training.notifications.email-enabled=false"
})
class CareerPathLearningBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
