package com.ejemplo.usuarios.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integracion de la cadena de seguridad con el contexto completo
 * ({@link SpringBootTest} + base de datos H2 en memoria).
 *
 * <p>Es la unica prueba que verifica de verdad las reglas de
 * {@code SecurityConfig}, porque aqui si se aplica la cadena de filtros.</p>
 */
@SpringBootTest(properties = "spring.security.user.password=clave-de-pruebas")
@AutoConfigureMockMvc
@DisplayName("Cadena de seguridad (API stateless)")
class SecurityIntegrationTest {

    private static final String USUARIO = "admin";
    private static final String CLAVE = "clave-de-pruebas";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("las rutas de /api/v1/auth/** son publicas: registran sin credenciales")
    void authEsPublico() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Usuario Publico",
                                  "email": "publico@correo.com",
                                  "password": "secreto123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.password").doesNotExist());
    }

    @Test
    @DisplayName("las rutas protegidas devuelven 401 con cuerpo ApiResponse si falta autenticacion")
    void rutasProtegidasRequierenAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exito").value(false))
                .andExpect(jsonPath("$.mensaje").value("No autenticado: credenciales ausentes o invalidas"))
                .andExpect(jsonPath("$.datos").isEmpty());
    }

    @Test
    @DisplayName("credenciales invalidas tambien devuelven 401 con cuerpo ApiResponse")
    void credencialesInvalidasDevuelven401() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(httpBasic(USUARIO, "clave-equivocada")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.exito").value(false))
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("una ruta inexistente devuelve 404 (no 500) tambien en las rutas protegidas")
    void rutaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/v1/ruta-que-no-existe").with(httpBasic(USUARIO, CLAVE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.exito").value(false));
    }

    @Test
    @DisplayName("las rutas protegidas responden 200 con credenciales validas, sin exponer passwords")
    void rutasProtegidasConCredenciales() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(httpBasic(USUARIO, CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.datos[*].password").doesNotExist());
    }

    @Test
    @DisplayName("un numero donde se espera texto devuelve 400 (no se coacciona a String)")
    void noSeCoaccionanEscalaresATexto() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": 123,
                                  "email": "numerico@correo.com",
                                  "password": "secreto123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));
    }

    @Test
    @DisplayName("la API es stateless: no devuelve cookie de sesion")
    void apiEsStateless() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Usuario Sin Cookie",
                                  "email": "sin-cookie@correo.com",
                                  "password": "secreto123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull());
    }
}