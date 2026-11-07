package com.rms.common;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        List<FieldError> errors,
        String path) {

    public record FieldError(String field, String message) {
    }
}

// record la class mang const data. Java auto tao constructor va phuong thuc doc tung thanh phan

// {
//   "timestamp": "2026-11-07T10:46:04.587545363Z",
//   "status": 404,
//   "code": "NOT_FOUND",
//   "message": "Resource not found",
//   "errors": null,
//   "path": "/api/v1/missing"
// }