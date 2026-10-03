package com.ejemplo.usuarios.exception;

/**
 * Excepcion de dominio para "el recurso solicitado no existe".
 * Se traduce a HTTP 404 en {@link GlobalExceptionHandler}.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String message) {
        super(message);
    }

    public static RecursoNoEncontradoException porId(String recurso, Object id) {
        return new RecursoNoEncontradoException(recurso + " no encontrado con id: " + id);
    }
}