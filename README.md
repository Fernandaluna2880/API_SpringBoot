# Gestión de Usuarios y Autenticación Básica — Spring Boot

API REST construida con **Java 17+**, **Spring Boot 3.3.x** y **Maven** que implementa el registro, autenticación y gestión de usuarios siguiendo una **arquitectura en capas** y buenas prácticas REST.

El proyecto incluye:

- Base de datos **H2 en memoria** con consola web habilitada en `/h2-console`.
- **BCrypt** para el hashing de contraseñas (nunca se guarda texto plano).
- Manejo global de errores con `@RestControllerAdvice`.
- Inyección de dependencias por **constructor** (Lombok `@RequiredArgsConstructor`).
- DTOs que jamás exponen la contraseña.

---

## Tabla de contenidos

1. [Investigación Teórica](#1-investigacion-teorica)
   - [Arquitectura en Capas](#11-arquitectura-en-capas)
   - [Manejo de Contraseñas](#12-manejo-de-contrasenas)
   - [Lógica de Autenticación Básica](#13-logica-de-autenticacion-basica)
   - [Buenas Prácticas REST](#14-buenas-practicas-rest)
2. [Estructura del Proyecto](#2-estructura-del-proyecto)
3. [Guía de Ejecución](#3-guia-de-ejecucion)
4. [Colección de Pruebas (cURL)](#4-coleccion-de-pruebas-curl)

---

## 1. Investigación Teórica

### 1.1 Arquitectura en Capas

La aplicación separa las responsabilidades en capas que se comunican siempre en **sentido descendente**: una capa solo conoce a la capa inmediatamente inferior.

| Capa | Paquete | Responsabilidad en el flujo de usuarios |
|------|---------|------------------------------------------|
| **Controller** | `com.ejemplo.usuarios.controller` | Capa de presentación/API. Recibe la petición HTTP (`POST /api/v1/auth/register`, `GET /api/v1/users/{id}`, etc.), delega en el servicio y empaqueta la respuesta en `ResponseEntity<?>`. No contiene lógica de negocio ni de acceso a datos. |
| **Service** | `com.ejemplo.usuarios.service` | Capa de **lógica de negocio**. Orquesta las reglas: verifica si el email ya existe, hashea la contraseña con BCrypt, valida credenciales en el login, desactiva la cuenta en el delete y lanza excepciones de negocio (`EmailAlreadyExistsException`, `InvalidCredentialsException`, `UsuarioNotFoundException`). Está anotada con `@Transactional`. |
| **Repository** | `com.ejemplo.usuarios.repository` | Capa de **acceso a datos**. Extiende `JpaRepository<Usuario, Long>` y expone consultas derivadas como `findByEmail`, `existsByEmailIgnoreCase` y `findByEmailIgnoreCase`. Encapsula toda la interacción con la base de datos. |
| **Entity** | `com.ejemplo.usuarios.entity` | Clase persistente `Usuario` mapeada con JPA (`@Entity`, `@Table`). Representa la tabla `usuarios` con `id`, `nombre`, `email` (único), `password` y `estado` (activo por defecto). |
| **DTO** | `com.ejemplo.usuarios.dto` | Objetos de transferencia de datos. Los DTOs de entrada (`RegisterRequestDTO`, `LoginRequestDTO`, `UserUpdateRequestDTO`) definen el contrato JSON de las peticiones y sus validaciones con Bean Validation. El DTO de salida `UserResponseDTO` **nunca incluye la contraseña**, garantizando que esta no se exponga en ninguna respuesta. |
| **Exception** | `com.ejemplo.usuarios.exception` | Excepciones de negocio personalizadas y el `GlobalExceptionHandler` (`@RestControllerAdvice`) que traduce las excepciones en respuestas HTTP con código y cuerpo estructurado. |

**Flujo de una petición de registro:**

```
Cliente HTTP
   │  POST /api/v1/auth/register {nombre, email, password}
   ▼
Controller (AuthController) ── valida cuerpo con @Valid
   │
   ▼
Service (UsuarioService.register)
   │  1. ¿Existe el email? ── Sí ──► EmailAlreadyExistsException ──► 400 Bad Request
   │  2. passwordEncoder.encode(password)  (hashing BCrypt)
   │  3. Guarda Usuario con estado = activo
   ▼
Repository (UsuarioRepository.save)
   │
   ▼
Entity (Usuario) ── taba `usuarios` en H2
   │
   ▼
Servicio devuelve UserResponseDTO (sin password) → Controller → 201 Created
```

### 1.2 Manejo de Contraseñas

**¿Por qué no guardar texto plano?**

- Si la base de datos se filtra o es robada, un atacante obtiene todas las contraseñas de inmediato.
- Los usuarios reutilizan contraseñas entre servicios; una fuga compromete también cuentas de terceros.
- Es mala práctica incluso a nivel legal/regulatorio: una contraseña almacenada sin proteger se considera un dato sensible mal custodiado.

**¿Cómo funciona el hashing con BCrypt?**

`BCryptPasswordEncoder` (de `spring-security-crypto`) aplica a la contraseña un **hash unidireccional** basado en el cifrado Blowfish, con estas propiedades clave:

1. **Unidireccional**: dado el hash es computacionalmente inviable recuperar la contraseña original. No es reversible (a diferencia de cifrados AES).
2. **Sal automática**: en cada ejecución, BCrypt genera un *salt* aleatorio que se incorpora al hash. Por eso **el mismo texto plano produce siempre un hash distinto**. Esto impide el uso de tablas *rainbow* precomputadas.
3. **Factor de costo ajustable**: el parámetro de fuerza (`strength`, por defecto 10) hace que cada hash sea deliberadamente lento (~100 ms), ralentizando ataques de fuerza bruta.
4. **Formato autocontenido**: el hash resultante incluye la versión, el costo y el salt, por ejemplo:
   `$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy`

**En este proyecto:**

- **Registro**: `passwordEncoder.encode(password)` produce el hash que se persiste en `usuarios.password`.
- **Login**: `passwordEncoder.matches(passwordIngresada, hashPersistido)` re-hashea la entrada con el mismo *salt* y compara los resultados sin necesidad de descifrar nada.

**Verificación empírica**: si registras dos usuarios con la misma contraseña, sus hashes (`users.password`) serán distintos, pero `matches()` devolverá `true` en ambos.

### 1.3 Lógica de Autenticación Básica

| Aspecto | Registro (`POST /auth/register`) | Login (`POST /auth/login`) |
|---------|----------------------------------|----------------------------|
| **Objetivo** | Crear una nueva cuenta en el sistema | Comprobar que quien intenta entrar es quien dice ser |
| **Condición previa** | El email **no debe** existir | La cuenta **debe** existir |
| **Acción sobre la contraseña** | Se hashea con `encode()` y se persiste | Se compara con `matches()` contra el hash guardado |
| **Efecto en datos** | Inserta un nuevo registro con estado `activo` | Ninguno (lectura) |
| **Resultado** | `201 Created` con el `UserResponseDTO` | `200 OK` con el `UserResponseDTO`, o `401 Unauthorized` si el email no existe o la clave no coincide |

**Concepto clave**: el registro **crea** credenciales, el login **verifica** credenciales. El login nunca crea ni modifica usuarios: solo busca por email y valida el hash. Un fallo en cualquier paso produce exactamente la misma respuesta `401 Credenciales incorrectas`, para no filtrar si lo que falló fue el email o la contraseña (evita enumeración de cuentas).

### 1.4 Buenas Prácticas REST

| Verbo HTTP | Uso semántico | Endpoint | Código de éxito | Códigos de error en este proyecto |
|------------|---------------|----------|-----------------|-----------------------------------|
| **POST** | Crear un recurso (no idempotente) | `POST /api/v1/auth/register` | **201 Created** | 400 si falta un campo o el email ya existe |
| **POST** | Operación de verificación (login) | `POST /api/v1/auth/login` | **200 OK** | 401 si las credenciales son incorrectas; 400 si el cuerpo es inválido |
| **GET** | Leer/consultar (idempotente, no modifica) | `GET /api/v1/users` | **200 OK** | 401/403 si hubiera protección (fuera de alcance) |
| **GET** | Leer un recurso por identificador | `GET /api/v1/users/{id}` | **200 OK** | **404 Not Found** si el id no existe |
| **PUT** | Actualizar/reemplazar un recurso (idempotente) | `PUT /api/v1/users/{id}` | **200 OK** | **404** si el id no existe; **400** si el email ya está en uso o el cuerpo es inválido |
| **DELETE** | Borrar o desactivar (idempotente) | `DELETE /api/v1/users/{id}` | **200 OK** (desactiva → `estado: false`) | **404** si el id no existe |

**Resumen de códigos de estado implementados:**

| Código | Significado | Cuándo se devuelve |
|--------|-------------|--------------------|
| **200 OK** | Éxito genérico | Login correcto, listar usuarios, obtener por id, actualizar, desactivar |
| **201 Created** | Recurso creado | Registro exitoso (`/auth/register`) |
| **400 Bad Request** | Petición mal formada / conflicto de validación | Faltan campos, email inválido, email ya registrado |
| **401 Unauthorized** | Credenciales inválidas | Login con email inexistente o contraseña incorrecta |
| **404 Not Found** | Recurso inexistente | Consultar/actualizar/eliminar un `id` que no existe |

Otras buenas prácticas aplicadas:

- **Rutas en plural y versionadas**: `/api/v1/users`, `/api/v1/auth`.
- **Recurso identificado por path** (`{id}`), nunca por query string para búsquedas directas.
- **Stateless y desacoplado**: cada petición es independiente; el servicio no mantiene estado de sesión.
- **JSON como formato de intercambio**, con `Content-Type: application/json`.
- **Respuestas de error con cuerpo estructurado** (`timestamp`, `status`, `error`, `message`) en lugar de devolver solo el código.
- **DTOs de salida sin datos sensibles**: la contraseña jamás se serializa.

---

## 2. Estructura del Proyecto

```
Api_springboot/
├── .mvn/wrapper/maven-wrapper.properties   # Config del Maven Wrapper
├── mvnw / mvnw.cmd                         # Scripts del Maven Wrapper (Windows/Unix)
├── pom.xml                                 # Dependencias y build (Spring Boot 3.3.x)
├── README.md
└── src/main/
    ├── java/com/ejemplo/usuarios/
    │   ├── UsuariosApplication.java        # Clase principal + bean BCryptPasswordEncoder
    │   ├── controller/
    │   │   ├── AuthController.java         # POST /api/v1/auth/register y /login
    │   │   └── UsuarioController.java      # GET/PUT/DELETE /api/v1/users
    │   ├── dto/
    │   │   ├── RegisterRequestDTO.java
    │   │   ├── LoginRequestDTO.java
    │   │   ├── UserResponseDTO.java        # Nunca incluye password
    │   │   └── UserUpdateRequestDTO.java
    │   ├── entity/
    │   │   └── Usuario.java                # @Entity -> tabla `usuarios`
    │   ├── exception/
    │   │   ├── EmailAlreadyExistsException.java
    │   │   ├── InvalidCredentialsException.java
    │   │   ├── UsuarioNotFoundException.java
    │   │   └── GlobalExceptionHandler.java # @RestControllerAdvice (400/401/404/500)
    │   ├── repository/
    │   │   └── UsuarioRepository.java      # JpaRepository + consultas derivadas
    │   └── service/
    │       └── UsuarioService.java         # Lógica de negocio + BCrypt + @Transactional
    └── resources/
        └── application.properties          # H2 en memoria + consola /h2-console
```

---

## 3. Guía de Ejecución

**Requisitos previos**

- **JDK 17 o superior** instalado y `JAVA_HOME` configurado.
- Maven 3.9+ (opcional: el proyecto incluye el **Maven Wrapper** `mvnw`, que descarga Maven automáticamente).
- Conexión a internet en la **primera** ejecución (Maven descarga las dependencias).

**Paso 1 — Compilar**

```bash
./mvnw clean compile
```

Si en lugar del wrapper quieres usar tu Maven local: `mvn clean compile`.

**Paso 2 — Ejecutar la aplicación**

```bash
./mvnw spring-boot:run
```

Equivalente con Maven local: `mvn spring-boot:run`.

También puedes ejecutar el JAR ya empaquetado:

```bash
./mvnw package
./mvnw clean package
java -jar target/gestion-usuarios-1.0.0.jar
```

**Paso 3 — Verificar**

Al arrancar, en la consola verás algo similar a:

```
Tomcat started on port 8080 (http) with context path ''
Started UsuariosApplication in X seconds
```

La API queda disponible en **http://localhost:8080**.

**Consola H2 (opcional)**

Con la app corriendo, abre http://localhost:8080/h2-console y conecta con:

| Campo | Valor |
|-------|-------|
| JDBC URL | `jdbc:h2:mem:usuariosdb` |
| User Name | `sa` |
| Password | *(vacío)* |

Podrás ejecutar consultas SQL contra la tabla `usuarios` (por ejemplo, ver que los `password` son hashes BCrypt y no texto plano).

---

## 4. Colección de Pruebas (cURL)

> **Nota para Windows (cmd/PowerShell):** las comillas dobles afectan cómo se interpreta el JSON. En PowerShell usa `curl.exe` (no el alias `curl` de PS) o escapa las comillas como `\"`. En bash/git-bash los comandos funcionan tal cual.

### 4.1 Registro de usuario

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Ana Lopez","email":"ana@correo.com","password":"secreto123"}'
```

**Respuesta esperada (201 Created):**

```json
{
  "id": 1,
  "nombre": "Ana Lopez",
  "email": "ana@correo.com",
  "estado": true
}
```

### 4.2 Login correcto

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@correo.com","password":"secreto123"}'
```

**Respuesta esperada (200 OK):**

```json
{
  "id": 1,
  "nombre": "Ana Lopez",
  "email": "ana@correo.com",
  "estado": true
}
```

### 4.3 Login con credenciales incorrectas

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@correo.com","password":"claveIncorrecta"}'
```

**Respuesta esperada (401 Unauthorized):**

```json
{
  "timestamp": "2026-09-08T19:02:21",
  "status": 401,
  "error": "Unauthorized",
  "message": "Credenciales incorrectas"
}
```

### 4.4 Registro con email duplicado

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Copia","email":"ana@correo.com","password":"secreto123"}'
```

**Respuesta esperada (400 Bad Request):**

```json
{
  "timestamp": "2026-09-08T19:02:17",
  "status": 400,
  "error": "Bad Request",
  "message": "El email ya se encuentra registrado"
}
```

### 4.5 Registro con datos inválidos (campos faltantes / email mal formado)

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"nombre":"","email":"no-es-correo","password":"123"}'
```

**Respuesta esperada (400 Bad Request):**

```json
{
  "timestamp": "2026-09-08T19:02:17",
  "status": 400,
  "error": "Bad Request",
  "message": "nombre: El nombre es obligatorio; email: El email debe tener un formato valido; password: La contrasena debe tener entre 6 y 64 caracteres"
}
```

### 4.6 Obtener todos los usuarios

```bash
curl http://localhost:8080/api/v1/users
```

**Respuesta esperada (200 OK):**

```json
[
  {
    "id": 1,
    "nombre": "Ana Lopez",
    "email": "ana@correo.com",
    "estado": true
  }
]
```

### 4.7 Obtener usuario por ID

```bash
curl http://localhost:8080/api/v1/users/1
```

**Respuesta esperada (200 OK):**

```json
{
  "id": 1,
  "nombre": "Ana Lopez",
  "email": "ana@correo.com",
  "estado": true
}
```

### 4.8 Obtener usuario inexistente

```bash
curl http://localhost:8080/api/v1/users/999
```

**Respuesta esperada (404 Not Found):**

```json
{
  "timestamp": "2026-09-08T19:02:25",
  "status": 404,
  "error": "Not Found",
  "message": "Usuario no encontrado con id: 999"
}
```

### 4.9 Actualizar usuario (nombre y email)

```bash
curl -X PUT http://localhost:8080/api/v1/users/1 \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Ana M. Lopez","email":"ana-nueva@correo.com"}'
```

**Respuesta esperada (200 OK):**

```json
{
  "id": 1,
  "nombre": "Ana M. Lopez",
  "email": "ana-nueva@correo.com",
  "estado": true
}
```

### 4.10 Actualizar usuario inexistente

```bash
curl -X PUT http://localhost:8080/api/v1/users/999 \
  -H "Content-Type: application/json" \
  -d '{"nombre":"X","email":"x@correo.com"}'
```

**Respuesta esperada (404 Not Found):**

```json
{
  "timestamp": "2026-09-08T19:02:26",
  "status": 404,
  "error": "Not Found",
  "message": "Usuario no encontrado con id: 999"
}
```

### 4.11 Desactivar usuario (DELETE)

```bash
curl -X DELETE http://localhost:8080/api/v1/users/1
```

**Respuesta esperada (200 OK):**

```json
{
  "message": "Usuario desactivado correctamente"
}
```

Después de desactivar, el usuario conserva su registro pero con `estado: false`:

```bash
curl http://localhost:8080/api/v1/users/1
```

```json
{
  "id": 1,
  "nombre": "Ana M. Lopez",
  "email": "ana-nueva@correo.com",
  "estado": false
}
```

---

## Endpoints de referencia rápida

| Método | Ruta | Descripción | Códigos |
|--------|------|-------------|---------|
| POST | `/api/v1/auth/register` | Registra un usuario (hash BCrypt, estado activo) | 201, 400 |
| POST | `/api/v1/auth/login` | Autentica con BCrypt | 200, 401, 400 |
| GET | `/api/v1/users` | Lista todos los usuarios (DTO sin password) | 200 |
| GET | `/api/v1/users/{id}` | Obtiene un usuario por id | 200, 404 |
| PUT | `/api/v1/users/{id}` | Actualiza nombre/email | 200, 404, 400 |
| DELETE | `/api/v1/users/{id}` | Desactiva la cuenta (`estado: false`) | 200, 404 |