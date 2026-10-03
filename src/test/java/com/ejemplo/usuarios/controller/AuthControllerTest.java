package com.ejemplo.usuarios.controller;

import com.ejemplo.usuarios.config.JacksonConfig;
import com.ejemplo.usuarios.dto.UsuarioResponse;
import com.ejemplo.usuarios.service.UsuarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integracion de la capa web: solo se carga el contexto MVC de
 * {@link AuthController}, sin base de datos ni seguridad (addFilters = false).
 *
 * <p>La asercion clave es de seguridad: la respuesta del registro NO puede
 * contener el campo {@code password} en ningun nivel.</p>
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
// Las clases @Configuration normales no se cargan en una slice test: hay que importarlas.
@Import(JacksonConfig.class)
@DisplayName("API de autenticacion")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UsuarioService usuarioService;

    @Test
    @DisplayName("POST /api/v1/auth/register responde 201 y NO expone la contrasena")
    void register_noDebeDevolverPassword() throws Exception {
        when(usuarioService.register(any())).thenReturn(
                new UsuarioResponse(1L, "Ana Lopez", "ana@correo.com", Boolean.TRUE));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Ana Lopez",
                                  "email": "ana@correo.com",
                                  "password": "secreto123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos.id").value(1))
                .andExpect(jsonPath("$.datos.nombre").value("Ana Lopez"))
                .andExpect(jsonPath("$.datos.email").value("ana@correo.com"))
                .andExpect(jsonPath("$.datos.password").doesNotExist())
                .andExpect(jsonPath("$.datos", not(hasKey("password"))));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login responde 200 sin contrasena")
    void login_noDebeDevolverPassword() throws Exception {
        when(usuarioService.login(any())).thenReturn(
                new UsuarioResponse(1L, "Ana Lopez", "ana@correo.com", Boolean.TRUE));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "ana@correo.com",
                                  "password": "secreto123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos.email").value("ana@correo.com"))
                .andExpect(jsonPath("$.datos.password").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register con datos invalidos responde 400 con errores por campo")
    void register_validacionDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "",
                                  "email": "no-es-correo",
                                  "password": "123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false))
                .andExpect(jsonPath("$.datos.nombre").exists())
                .andExpect(jsonPath("$.datos.email").exists())
                .andExpect(jsonPath("$.datos.password").exists());
    }

    @Test
    @DisplayName("JSON mal formado responde 400 y no 500")
    void jsonMalFormadoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"roto\",,}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false))
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("un numero donde se espera texto responde 400 (no se coacciona a String)")
    void numeroDondeSeEsperaTextoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": 123,
                                  "email": "ana@correo.com",
                                  "password": "secreto123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false))
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("una contrasena numerica se rechaza en vez de convertirse a texto")
    void passwordNumericoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Ana",
                                  "email": "ana@correo.com",
                                  "password": 12345678
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un boolean donde se espera texto se rechaza")
    void booleanDondeSeEsperaTextoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": true,
                                  "email": "ana@correo.com",
                                  "password": "secreto123"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("una ruta inexistente responde 404 y no 500")
    void rutaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/v1/ruta-que-no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.exito").value(false))
                .andExpect(jsonPath("$.mensaje").exists());
    }
}