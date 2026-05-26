package com.tbm.careerpathlearning.exception;

import com.tbm.careerpathlearning.service.ValidationService;
import com.tbm.careerpathlearning.service.impl.ValidationServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ValidationService validationService;

    private static final String BAD_REQUEST_EXP_ERR_TITLE_CODE = "bad.request.err.title";

    private static final String FORBIDDEN_REQUEST_EXP_ERR_TITLE_CODE = "forbidden.request.err.title";

    private static final String FORBIDDEN_REQUEST_EXP_ERR_MSG_CODE = "forbidden.request.err.msg";

    private static final String INTERNAL_SERVER_ERROR_EXP_ERR_TITLE_CODE = "internal.server.err.title";

    private static final String DATA_ACCESS_EXP_ERR_TITLE_CODE = "database.err.title";

    private static final String ACCESS_DENIED_ERR_TITLE_CODE = "access.denied.err.title";

    private static final String ACCESS_DENIED_ERR_MSG_CODE = "access.denied.err.msg";

    @ExceptionHandler(InternalServerException.class)
    public ResponseEntity<?> handleInternalServerException(InternalServerException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("title", ex.getTitle() == null || ex.getTitle().trim().isEmpty() ?
                                messageSource.getMessage(INTERNAL_SERVER_ERROR_EXP_ERR_TITLE_CODE, null, Locale.getDefault()) : ex.getTitle(),
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<?> handleBadRequestException(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("title", ex.getTitle() == null || ex.getTitle().trim().isEmpty() ?
                                messageSource.getMessage(BAD_REQUEST_EXP_ERR_TITLE_CODE, null, Locale.getDefault()) : ex.getTitle(),
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(ForbiddenRequestException.class)
    public ResponseEntity<?> handleForbiddenRequestException(ForbiddenRequestException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("title", ex.getTitle() == null || ex.getTitle().trim().isEmpty() ?
                                messageSource.getMessage(FORBIDDEN_REQUEST_EXP_ERR_TITLE_CODE, null, Locale.getDefault()) : ex.getTitle(),
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<?> handleAuthorizationDeniedExceptionException(AuthorizationDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("title", messageSource.getMessage(FORBIDDEN_REQUEST_EXP_ERR_TITLE_CODE, null, Locale.getDefault()),
                        "message", messageSource.getMessage(FORBIDDEN_REQUEST_EXP_ERR_MSG_CODE, null, Locale.getDefault())
                ));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> handleDataAccessException(DataAccessException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("title", ex.getTitle() == null || ex.getTitle().trim().isEmpty() ?
                                messageSource.getMessage(DATA_ACCESS_EXP_ERR_TITLE_CODE, null, Locale.getDefault()) : ex.getTitle(),
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex) {
        String errorTitle = messageSource.getMessage(ACCESS_DENIED_ERR_TITLE_CODE, null, Locale.getDefault());
        String errorMessage = messageSource.getMessage(ACCESS_DENIED_ERR_MSG_CODE, null, Locale.getDefault());

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("title", errorTitle, "message", errorMessage));
    }
}

