package com.ejemplo.usuarios.service;

import com.ejemplo.usuarios.dto.LoginRequest;
import com.ejemplo.usuarios.dto.RegisterRequest;
import com.ejemplo.usuarios.dto.UpdateRequest;
import com.ejemplo.usuarios.dto.UsuarioResponse;
import com.ejemplo.usuarios.entity.Usuario;
import com.ejemplo.usuarios.exception.EmailAlreadyExistsException;
import com.ejemplo.usuarios.exception.InvalidCredentialsException;
import com.ejemplo.usuarios.exception.RecursoNoEncontradoException;
import com.ejemplo.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Capa de negocio de usuarios. Trabaja exclusivamente con DTOs: la entidad
 * JPA no sale de esta clase, lo que impide que el hash de la contrasena
 * termine en un controlador o en la respuesta JSON.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UsuarioResponse register(RegisterRequest request) {
        String email = normalizarEmail(request.email());

        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException("El email ya se encuentra registrado");
        }

        String passwordHashed = passwordEncoder.encode(request.password());
        Usuario usuario = Usuario.nuevo(
                request.nombre().trim(),
                email,
                passwordHashed);

        return UsuarioResponse.fromEntity(usuarioRepository.save(usuario));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(normalizarEmail(request.email()))
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales incorrectas"));

        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            throw new InvalidCredentialsException("Credenciales incorrectas");
        }

        return UsuarioResponse.fromEntity(usuario);
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> findAll() {
        return usuarioRepository.findAll().stream()
                .map(UsuarioResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse findById(Long id) {
        return UsuarioResponse.fromEntity(requireUsuario(id));
    }

    @Transactional
    public UsuarioResponse update(Long id, UpdateRequest request) {
        Usuario usuario = requireUsuario(id);
        String email = normalizarEmail(request.email());

        usuarioRepository.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new EmailAlreadyExistsException("El email ya se encuentra registrado");
                });

        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(email);

        return UsuarioResponse.fromEntity(usuarioRepository.save(usuario));
    }

    @Transactional
    public void deactivate(Long id) {
        Usuario usuario = requireUsuario(id);
        usuario.setEstado(Boolean.FALSE);
        usuarioRepository.save(usuario);
    }

    private Usuario requireUsuario(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.porId("Usuario", id));
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }
}