package com.rms.common;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rms.modules.auth.controller.AuthController;
import com.rms.modules.auth.service.AuthService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Mọi lỗi đi ra từ controller phải có cùng một dạng ApiError (status, code, message, errors, path).
 * AuthController là controller thật (kiểm @Valid trên body); ProbeController chỉ để ném các loại lỗi khác.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new AuthController(mock(AuthService.class)), new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void invalidBodyListsEveryBrokenField() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"","email":"x","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("fullName", "email", "password")))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void brokenJsonIsABadRequestWithCode() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));
    }

    @Test
    void missingBodyIsABadRequestWithCode() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void wrongParameterTypeNamesTheParameter() throws Exception {
        mvc.perform(get("/probe/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @Test
    void invalidParameterValueNamesTheParameter() throws Exception {
        mvc.perform(get("/probe/limit").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("limit"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void constraintViolationFromValidatedControllerNamesTheField() throws Exception {
        mvc.perform(get("/probe/constraint-violation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
    }

    @Test
    void missingParameterKeepsItsBadRequestStatus() throws Exception {
        mvc.perform(get("/probe/limit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void wrongHttpMethodKeepsItsStatus() throws Exception {
        mvc.perform(post("/probe/limit"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void duplicateKeyIsAConflictAndDoesNotLeakSql() throws Exception {
        mvc.perform(get("/probe/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message", not(containsString("uk_users_email"))));
    }

    @Test
    void databaseFailureDoesNotLeakSql() throws Exception {
        mvc.perform(get("/probe/db-down"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DATA_ACCESS"))
                .andExpect(jsonPath("$.message", not(containsString("orders"))));
    }

    @Test
    void uploadTooLargeIsPayloadTooLarge() throws Exception {
        mvc.perform(get("/probe/too-large"))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void accessDeniedStaysForbidden() throws Exception {
        mvc.perform(get("/probe/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unexpectedErrorIsAGenericInternalError() throws Exception {
        mvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message", not(containsString("secret detail"))))
                .andExpect(jsonPath("$.path").value("/probe/boom"));
    }

    @RestController
    static class ProbeController {

        record Limit(@Min(1) int limit) {
        }

        @GetMapping("/probe/items/{id}")
        String item(@PathVariable Long id) {
            return "item " + id;
        }

        @GetMapping("/probe/limit")
        String limit(@RequestParam @Min(1) int limit) {
            return "limit " + limit;
        }

        /** Controller có @Validated ở class ném ConstraintViolationException (qua AOP) thay vì lỗi của MVC. */
        @GetMapping("/probe/constraint-violation")
        String constraintViolation() {
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                throw new ConstraintViolationException(factory.getValidator().validate(new Limit(0)));
            }
        }

        @GetMapping("/probe/duplicate")
        String duplicate() {
            throw new DataIntegrityViolationException(
                    "could not execute statement", new RuntimeException("Duplicate entry 'a@b.c' for key 'uk_users_email'"));
        }

        @GetMapping("/probe/db-down")
        String dbDown() {
            throw new QueryTimeoutException("timeout", new RuntimeException("select * from orders where ..."));
        }

        @GetMapping("/probe/too-large")
        String tooLarge() {
            throw new MaxUploadSizeExceededException(2 * 1024 * 1024);
        }

        @GetMapping("/probe/denied")
        String denied() {
            throw new AccessDeniedException("nope");
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("secret detail: jdbc password=...");
        }
    }
}
