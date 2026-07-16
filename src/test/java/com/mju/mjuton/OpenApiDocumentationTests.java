package com.mju.mjuton;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTests {
	@Autowired MockMvc mvc;

	@Test
	void openApiDocumentsEveryImplementedEndpointAndSessionAuthentication() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.info.title").value("CampusLink API"))
				.andExpect(jsonPath("$.paths['/api/health'].get").exists())
				.andExpect(jsonPath("$.paths['/api/auth/email-verifications'].post").exists())
				.andExpect(jsonPath("$.paths['/api/auth/signup'].post").exists())
				.andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
				.andExpect(jsonPath("$.paths['/api/auth/login'].post.security").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/auth/logout'].post").exists())
				.andExpect(jsonPath("$.paths['/api/auth/session'].get.security[0].sessionCookie").isArray())
				.andExpect(jsonPath("$.paths['/api/profile'].get.security[0].sessionCookie").isArray())
				.andExpect(jsonPath("$.paths['/api/profile'].put.security[0].sessionCookie").isArray())
				.andExpect(jsonPath("$.components.securitySchemes.sessionCookie.type").value("apiKey"))
				.andExpect(jsonPath("$.components.securitySchemes.sessionCookie.in").value("cookie"))
				.andExpect(jsonPath("$.components.securitySchemes.sessionCookie.name").value("JSESSIONID"))
				.andExpect(jsonPath("$.components.schemas.ProfileUpdateRequest.required",
						hasItems("name", "schoolName", "departmentName", "residenceArea", "interests", "purposes", "roles")))
				.andExpect(jsonPath("$.components.schemas.ProfileUpdateRequest.properties.interests.maxItems").value(20))
				.andExpect(jsonPath("$.components.schemas.ProfileUpdateRequest.properties.interests.uniqueItems").value(true));
	}

	@Test
	void swaggerUiIsAvailable() throws Exception {
		mvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection());
		mvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
	}
}
