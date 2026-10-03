package com.ejemplo.usuarios.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Configuracion de la aplicacion relacionada con la codificacion de contrasenas.
 * Se declara el bean contra la interfaz {@link PasswordEncoder} y no contra la
 * implementacion concreta para que el codigo dependa de una abstraccion
 * (facilita sustituir BCrypt por otro algoritmo en el futuro).
 */
@Configuration
public class SecurityAppConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityAppConfig.class);

    /**
     * BCrypt aplica un salt aleatorio en cada ejecucion, por lo que dos
     * contrasenas identicas producen hashes distintos.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Usuario basico de las rutas protegidas.
     *
     * <p>La contrasena se hashea con nuestro {@link PasswordEncoder}; nunca se
     * persiste ni se compara en claro.</p>
     *
     * <p>No hay contrasena en {@code application.properties} a proposito: si no se
     * configura ninguna (variable de entorno {@code SPRING_SECURITY_USER_PASSWORD}),
     * se genera una aleatoria para el arranque y se imprime en el log, igual que
     * hace Spring Boot por defecto. Asi el repositorio no contiene credenciales.
     * Nunca se crea un usuario con contrasena vacia.</p>
     *
     * <p>Solo para desarrollo: en produccion debe reemplazarse por un proveedor
     * real de identidades (JWT / OAuth2).</p>
     */
    @Bean
    public UserDetailsService userDetailsService(
            @Value("${spring.security.user.name:admin}") String nombre,
            @Value("${spring.security.user.password:}") String claveConfigurada,
            PasswordEncoder passwordEncoder) {

        String clave = claveConfigurada;
        if (!StringUtils.hasText(clave)) {
            clave = UUID.randomUUID().toString();
            log.warn("""

                    No hay SPRING_SECURITY_USER_PASSWORD configurada: se genera una contrasena
                    aleatoria para este arranque.
                      Usuario:    {}
                      Contrasena: {}
                    (solo desarrollo; en produccion usa JWT / OAuth2)""", nombre, clave);
        }

        UserDetails usuario = User.withUsername(nombre)
                .password(passwordEncoder.encode(clave))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(usuario);
    }
}