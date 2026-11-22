package com.bizpilot.support;

import com.bizpilot.common.exception.GlobalExceptionHandler;
import com.bizpilot.config.CorsConfig;
import com.bizpilot.security.JwtAuthenticationFilter;
import com.bizpilot.security.JwtService;
import com.bizpilot.security.SecurityConfig;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for {@code @WebMvcTest} controller tests. Requests pass through the real security filter
 * chain, JWT filter, bean validation and global exception handler.
 */
@Import({CorsConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "bizpilot.jwt.secret=unit-test-signing-key-0123456789-abcdefghij",
        "bizpilot.jwt.expiration-ms=600000",
        "bizpilot.cors.allowed-origins=http://localhost:4200"
})
public abstract class ControllerTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtService jwtService;

    protected final UUID ownerId = UUID.randomUUID();

    protected String bearer() {
        return bearer(ownerId);
    }

    protected String bearer(UUID userId) {
        return "Bearer " + jwtService.generateToken(userId);
    }
}
