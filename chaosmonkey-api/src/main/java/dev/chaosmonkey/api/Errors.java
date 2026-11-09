package dev.chaosmonkey.api;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
@RestControllerAdvice
public class Errors {
    @ExceptionHandler(SecurityException.class) ResponseEntity<ProblemDetail> forbidden(SecurityException e) {return ResponseEntity.status(403).body(ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,e.getMessage()));}
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class,jakarta.validation.ConstraintViolationException.class}) ResponseEntity<ProblemDetail> invalid(Exception e) {return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Invalid request: check required fields, bounds and target scope"));}
}
