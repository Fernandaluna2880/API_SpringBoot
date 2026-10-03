package com.ejemplo.usuarios.dto;

import com.ejemplo.usuarios.entity.Usuario;

/**
 * Contrato JSON de salida de un usuario.
 *
 * <p><strong>IMPORTANTE:</strong> este record NO tiene componente {@code password}.
 * Al no existir el accessor, Jackson no puede serializar la contrasena y la
 * fuga de informacion sensible es imposible por construccion, no por
 * olvido de borrar un campo.</p>
 */
public record UsuarioResponse(
        Long id,
        String nombre,
        String email,
        Boolean estado
) {

    public static UsuarioResponse fromEntity(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getEstado()
        );
    }
}