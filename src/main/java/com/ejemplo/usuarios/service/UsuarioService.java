package com.ejemplo.usuarios.service;

import com.ejemplo.usuarios.dto.LoginRequestDTO;
import com.ejemplo.usuarios.dto.RegisterRequestDTO;
import com.ejemplo.usuarios.dto.UserResponseDTO;
import com.ejemplo.usuarios.dto.UserUpdateRequestDTO;
import com.ejemplo.usuarios.entity.Usuario;
import com.ejemplo.usuarios.exception.EmailAlreadyExistsException;
import com.ejemplo.usuarios.exception.InvalidCredentialsException;
import com.ejemplo.usuarios.exception.UsuarioNotFoundException;
import com.ejemplo.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Transactional
    public UserResponseDTO register(RegisterRequestDTO request) {
        if (usuarioRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new EmailAlreadyExistsException("El email ya se encuentra registrado");
        }

        String passwordHashed = passwordEncoder.encode(request.getPassword());
        Usuario usuario = Usuario.nuevo(
                request.getNombre().trim(),
                request.getEmail().trim().toLowerCase(),
                passwordHashed);

        return UserResponseDTO.fromEntity(usuarioRepository.save(usuario));
    }

    @Transactional(readOnly = true)
    public UserResponseDTO login(LoginRequestDTO request) {
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales incorrectas"));

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new InvalidCredentialsException("Credenciales incorrectas");
        }

        return UserResponseDTO.fromEntity(usuario);
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> findAll() {
        return usuarioRepository.findAll().stream()
                .map(UserResponseDTO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponseDTO findById(Long id) {
        return UserResponseDTO.fromEntity(requireUsuario(id));
    }

    @Transactional
    public UserResponseDTO update(Long id, UserUpdateRequestDTO request) {
        Usuario usuario = requireUsuario(id);

        usuarioRepository.findByEmailIgnoreCase(request.getEmail().trim())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new EmailAlreadyExistsException("El email ya se encuentra registrado");
                });

        usuario.setNombre(request.getNombre().trim());
        usuario.setEmail(request.getEmail().trim().toLowerCase());

        return UserResponseDTO.fromEntity(usuarioRepository.save(usuario));
    }

    @Transactional
    public void deactivate(Long id) {
        Usuario usuario = requireUsuario(id);
        usuario.setEstado(Boolean.FALSE);
        usuarioRepository.save(usuario);
    }

    private Usuario requireUsuario(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado con id: " + id));
    }
}