package com.ejemplo.usuarios.config;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ajustes de la serializacion/deserializacion JSON.
 *
 * <p>Por defecto Jackson convierte cualquier escalar al tipo del campo: un
 * {@code {"nombre": 123}} se aceptaba como {@code "123"}. Eso oculta errores del
 * cliente, asi que se prohibe la coercion de numeros y booleanos hacia campos de
 * texto: la peticion se rechaza con 400 en lugar de guardarse un dato falseado.</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictTypeCoercionCustomizer() {
        return builder -> builder.postConfigurer(mapper ->
                mapper.coercionConfigFor(LogicalType.Textual)
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}