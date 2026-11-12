package com.rms.modules.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rms.modules.auth.controller.AuthController;
import com.rms.modules.auth.dto.AuthResponse;
import com.rms.modules.auth.dto.LoginRequest;
import com.rms.modules.auth.dto.RegisterRequest;
import com.rms.modules.auth.dto.UserResponse;
import com.rms.modules.auth.service.AuthService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private AuthService auth;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        auth = mock(AuthService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth)).build();
    }

    @Test
    void registerReturnsCreatedUser() throws Exception {
        when(auth.register(any(RegisterRequest.class))).thenReturn(
                new UserResponse(1L, "An", "an@example.com", null, true, List.of("CUSTOMER")));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "An",
                                  "email": "an@example.com",
                                  "password": "password1",
                                  "phone": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("an@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
    }

    @Test
    void loginReturnsTokens() throws Exception {
        var user = new UserResponse(1L, "An", "an@example.com", null, true, List.of("CUSTOMER"));
        when(auth.login(any(LoginRequest.class))).thenReturn(
                new AuthResponse("access-x", "refresh-y", 1800, user));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"an@example.com","password":"password1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-x"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-y"))
                .andExpect(jsonPath("$.user.email").value("an@example.com"));
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        when(auth.me(7L)).thenReturn(
                new UserResponse(7L, "An", "an@example.com", null, true, List.of("CUSTOMER")));

        var authentication = new UsernamePasswordAuthenticationToken(7L, null, List.of());

        mvc.perform(get("/api/v1/auth/me").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }
}
