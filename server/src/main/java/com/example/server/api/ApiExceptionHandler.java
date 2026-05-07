package com.example.server.api;

import java.time.Instant;

import com.example.server.dto.ErrorResponse;
import com.example.server.service.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
		return ResponseEntity
				.status(exception.getStatus())
				.body(new ErrorResponse(exception.getMessage(), Instant.now()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
		return ResponseEntity
				.badRequest()
				.body(new ErrorResponse("Invalid JSON request body", Instant.now()));
	}
}
