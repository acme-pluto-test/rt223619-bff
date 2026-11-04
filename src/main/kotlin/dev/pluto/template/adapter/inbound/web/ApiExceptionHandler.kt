package dev.pluto.template.adapter.inbound.web

import dev.pluto.template.adapter.inbound.web.generated.model.ApiError
import jakarta.validation.ConstraintViolationException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException

class ApiException(val status: Int, val code: String) : RuntimeException(code)

/** Every error leaves the BFF as `{"code": "..."}` with the status the contract documents. */
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun api(e: ApiException): ResponseEntity<ApiError> = ResponseEntity.status(e.status).body(ApiError(e.code))

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun invalidParameter(e: HandlerMethodValidationException): ResponseEntity<ApiError> {
        val parameter = e.parameterValidationResults.firstOrNull()?.methodParameter?.parameterName ?: "request"
        return ResponseEntity.badRequest().body(ApiError("invalid_$parameter"))
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun invalidConstraint(e: ConstraintViolationException): ResponseEntity<ApiError> {
        val parameter = e.constraintViolations.firstOrNull()?.propertyPath?.last()?.name ?: "request"
        return ResponseEntity.badRequest().body(ApiError("invalid_$parameter"))
    }
}
