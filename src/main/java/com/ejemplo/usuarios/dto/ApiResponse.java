package com.ejemplo.usuarios.dto;

/**
 * Envoltorio estandar de todas las respuestas de la API.
 *
 * <pre>
 * {
 *   "exito": true,
 *   "mensaje": "Usuario registrado correctamente",
 *   "datos": { ... }
 * }
 * </pre>
 *
 * @param <T> tipo de la carga util de la respuesta
 */
public record ApiResponse<T>(
        boolean exito,
        String mensaje,
        T datos
) {

    public static <T> ApiResponse<T> ok(T datos) {
        return new ApiResponse<>(true, null, datos);
    }

    public static <T> ApiResponse<T> ok(String mensaje, T datos) {
        return new ApiResponse<>(true, mensaje, datos);
    }

    public static <T> ApiResponse<T> error(String mensaje) {
        return new ApiResponse<>(false, mensaje, null);
    }

    public static <T> ApiResponse<T> error(String mensaje, T datos) {
        return new ApiResponse<>(false, mensaje, datos);
    }
}