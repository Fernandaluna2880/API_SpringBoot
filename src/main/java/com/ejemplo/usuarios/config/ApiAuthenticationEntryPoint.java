package com.ejemplo.usuarios.config;

import com.ejemplo.usuarios.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Punto de entrada de autenticacion: se invoca cuando una ruta protegida se
 * solicita sin credenciales validas.
 *
 * <p>La cadena de filtros corta la peticion antes de llegar al controlador, por
 * lo que Spring Security devolveria un 401 sin cuerpo. Este componente escribe
 * el mismo formato {@link ApiResponse} que el resto de la API.</p>
 */
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ApiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error("No autenticado: credenciales ausentes o invalidas"));
    }
}