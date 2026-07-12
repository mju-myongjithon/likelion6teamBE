package com.mju.mjuton.global;

import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorResponse> handle(ApiException exception) {
		return ResponseEntity.status(exception.getStatus())
				.body(new ErrorResponse(exception.getCode(), exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handle(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getDefaultMessage()).collect(Collectors.joining(", "));
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST", message));
	}

	public record ErrorResponse(String code, String message) {}
}
