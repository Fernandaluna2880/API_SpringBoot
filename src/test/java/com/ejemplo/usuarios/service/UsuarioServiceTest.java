package com.ejemplo.usuarios.service;

import com.ejemplo.usuarios.dto.LoginRequest;
import com.ejemplo.usuarios.dto.RegisterRequest;
import com.ejemplo.usuarios.dto.UsuarioResponse;
import com.ejemplo.usuarios.entity.Usuario;
import com.ejemplo.usuarios.exception.InvalidCredentialsException;
import com.ejemplo.usuarios.exception.RecursoNoEncontradoException;
import com.ejemplo.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Servicio de usuarios")
class UsuarioServiceTest {

    private static final String PASSWORD_PLANA = "secreto123";
    private static final String PASSWORD_HASH = "$2a$10$hashFakeDeLaContrasena";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    @Test
    @DisplayName("registro: hashea la contrasena, guarda el usuario y devuelve el DTO sin password")
    void register_debeHasharGuardarYMapear() {
        RegisterRequest request = new RegisterRequest("Ana Lopez", "Ana@Correo.com", PASSWORD_PLANA);

        when(usuarioRepository.existsByEmailIgnoreCase("ana@correo.com")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD_PLANA)).thenReturn(PASSWORD_HASH);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario guardado = invocation.getArgument(0);
            guardado.setId(1L);
            return guardado;
        });

        UsuarioResponse response = usuarioService.register(request);

        verify(passwordEncoder, times(1)).encode(PASSWORD_PLANA);
        verify(usuarioRepository, times(1)).save(any(Usuario.class));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario entidadGuardada = captor.getValue();

        assertThat(entidadGuardada.getPassword())
                .as("la entidad debe persistir el hash, nunca la contrasena en claro")
                .isEqualTo(PASSWORD_HASH)
                .isNotEqualTo(PASSWORD_PLANA);
        assertThat(entidadGuardada.getNombre()).isEqualTo("Ana Lopez");
        assertThat(entidadGuardada.getEmail()).isEqualTo("ana@correo.com");
        assertThat(entidadGuardada.getEstado()).isTrue();

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.nombre()).isEqualTo("Ana Lopez");
        assertThat(response.email()).isEqualTo("ana@correo.com");
        assertThat(response.estado()).isTrue();
    }

    @Test
    @DisplayName("registro: no guarda nada si el email ya existe")
    void register_noDebeGuardarSiElEmailYaExiste() {
        RegisterRequest request = new RegisterRequest("Ana Lopez", "ana@correo.com", PASSWORD_PLANA);

        when(usuarioRepository.existsByEmailIgnoreCase("ana@correo.com")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.register(request))
                .isInstanceOf(com.ejemplo.usuarios.exception.EmailAlreadyExistsException.class);

        verify(passwordEncoder, never()).encode(anyString());
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("login: valida la contrasena con matches y devuelve el DTO")
    void login_exitoso() {
        Usuario usuario = Usuario.nuevo("Ana Lopez", "ana@correo.com", PASSWORD_HASH);
        usuario.setId(7L);

        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD_PLANA, PASSWORD_HASH)).thenReturn(true);

        UsuarioResponse response = usuarioService.login(new LoginRequest("ana@correo.com", PASSWORD_PLANA));

        verify(passwordEncoder).matches(PASSWORD_PLANA, PASSWORD_HASH);
        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.email()).isEqualTo("ana@correo.com");
    }

    @Test
    @DisplayName("login: credenciales invalidas no devuelven datos del usuario")
    void login_credencialesInvalidas() {
        Usuario usuario = Usuario.nuevo("Ana Lopez", "ana@correo.com", PASSWORD_HASH);
        usuario.setId(7L);

        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("otraClave", PASSWORD_HASH)).thenReturn(false);

        assertThatThrownBy(() -> usuarioService.login(new LoginRequest("ana@correo.com", "otraClave")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("findById: lanza RecursoNoEncontradoException si el id no existe")
    void findById_usuarioInexistente() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.findById(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("deactivate: marca el usuario como inactivo")
    void deactivate_marcaInactivo() {
        Usuario usuario = Usuario.nuevo("Ana Lopez", "ana@correo.com", PASSWORD_HASH);
        usuario.setId(1L);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        usuarioService.deactivate(1L);

        verify(usuarioRepository).save(usuario);
        assertThat(usuario.getEstado()).isFalse();
    }
}