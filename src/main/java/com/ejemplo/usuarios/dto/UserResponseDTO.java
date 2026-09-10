package com.ejemplo.usuarios.dto;

import com.ejemplo.usuarios.entity.Usuario;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserResponseDTO {

    private Long id;
    private String nombre;
    private String email;
    private Boolean estado;

    public static UserResponseDTO fromEntity(Usuario usuario) {
        return new UserResponseDTO(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getEstado());
    }
}