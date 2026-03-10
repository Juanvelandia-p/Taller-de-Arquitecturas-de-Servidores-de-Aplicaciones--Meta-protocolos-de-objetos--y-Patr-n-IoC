# MicroSpringBoot - Servidor Web IoC en Java

## Descripción

MicroSpringBoot es un servidor web ligero implementado en Java que proporciona:
- Servidor HTTP básico capaz de servir páginas HTML e imágenes PNG
- Framework IoC (Inversión de Control) para construcción de aplicaciones web con POJOs
- Soporte para múltiples solicitudes no concurrentes
- Anotaciones personalizadas inspiradas en Spring Boot

## Características

Servidor HTTP que escucha en el puerto 8080  
Servir archivos estáticos (HTML, CSS, JS, PNG, JPG, etc.)  
Anotaciones: `@RestController`, `@GetMapping`, `@RequestParam`  
Escaneo automático de controladores en el classpath  
Soporte para parámetros de consulta con valores por defecto  
Uso de Java Reflection para carga dinámica de beans

## Estructura del Proyecto

```
MicroSpring/
├── src/
│   └── main/
│       ├── java/
│       │   └── escuelaing/edu/arep/microspringboot/
│       │       ├── MicrosSpringBoot.java      # Servidor principal
│       │       ├── RestController.java        # Anotación de controlador
│       │       ├── GetMapping.java            # Anotación de mapeo GET
│       │       ├── RequestParam.java          # Anotación de parámetros
│       │       ├── HelloController.java       # Controlador de ejemplo
│       │       └── GreetingController.java    # Controlador con parámetros
│       └── resources/
│           └── webroot/                       # Archivos estáticos
│               ├── index.html
│               └── about.html
└── pom.xml
```

## Requisitos

- Java 17 o superior
- Maven 3.6 o superior

## Instalación y Ejecución

### 1. Compilar el proyecto

```bash
mvn clean compile
```

### 2. Ejecutar el servidor

**Opción A: Escaneo automático de controladores (recomendado)**
```bash
mvn exec:java
```

**Opción B: Especificar controlador desde línea de comandos**
```bash
java -cp target/classes escuelaing.edu.arep.microspringboot.MicrosSpringBoot escuelaing.edu.arep.microspringboot.HelloController
```

### 3. Probar el servidor

Abre tu navegador en `http://localhost:8080`

## Endpoints Disponibles

### Controladores REST

- `GET /` → Mensaje de bienvenida
- `GET /hello` → Página HTML de saludo
- `GET /greeting` → Saludo con contador
- `GET /greeting?name=Juan` → Saludo personalizado
- `GET /saludo` → Saludo en español
- `GET /saludo?name=María` → Saludo personalizado en español

### Archivos Estáticos

- `GET /index.html` → Página principal
- `GET /about.html` → Página acerca de
- Cualquier archivo en `src/main/resources/webroot/`

## Crear un Nuevo Controlador

### 1. Crear una clase con la anotación @RestController

```java
package escuelaing.edu.arep.microspringboot;

@RestController
public class MiControlador {

    @GetMapping("/miendpoint")
    public String miMetodo() {
        return "<html><body><h1>Mi Respuesta</h1></body></html>";
    }
    
    @GetMapping("/parametros")
    public String conParametros(
        @RequestParam(value = "nombre", defaultValue = "Invitado") String nombre,
        @RequestParam(value = "edad", defaultValue = "0") String edad) {
        return String.format("<html><body><p>Nombre: %s, Edad: %s</p></body></html>", 
                            nombre, edad);
    }
}
```

### 2. Registrar en el escaneo

Agrega tu controlador al método `scanControllers()` en `MicrosSpringBoot.java`:

```java
try {
    loadController("escuelaing.edu.arep.microspringboot.MiControlador");
} catch (ClassNotFoundException e) {
    // Ignorar si no existe
}
```

### 3. Recompilar y ejecutar

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="escuelaing.edu.arep.microspringboot.MicrosSpringBoot"
```

## Agregar Archivos Estáticos

Simplemente coloca tus archivos HTML, CSS, JS, PNG, etc. en el directorio:
```
src/main/resources/webroot/
```

Ejemplo:
- Archivo: `src/main/resources/webroot/imagen.png`
- URL: `http://localhost:8080/imagen.png`

## Arquitectura y Diseño

### Reflexión Java

El framework utiliza Java Reflection para:
1. Cargar clases dinámicamente en tiempo de ejecución
2. Inspeccionar anotaciones (`@RestController`, `@GetMapping`, `@RequestParam`)
3. Invocar métodos de controlador con parámetros dinámicos
4. Crear instancias de beans sin conocer su tipo en tiempo de compilación

### Inversión de Control (IoC)

- El framework controla el ciclo de vida de los controladores
- Los desarrolladores solo definen POJOs con anotaciones
- El framework se encarga de instanciar, registrar y enrutar

### Servidor HTTP

- Implementado con `ServerSocket` y `Socket` de Java
- Maneja requests HTTP GET
- Parser simple de headers y query parameters
- Responde con headers HTTP apropiados

## Pruebas

### Compilar y ejecutar tests

```bash
mvn test
```

## Despliegue en AWS

### Opción 1: EC2 Instance

1. **Crear una instancia EC2:**
   - AMI: Amazon Linux 2 o Ubuntu
   - Tipo: t2.micro (free tier)
   - Security Group: Permitir puerto 8080

2. **Conectar a la instancia:**
   ```bash
   ssh -i tu-key.pem ec2-user@tu-ip-publica
   ```

3. **Instalar Java y Maven:**
   ```bash
   sudo yum install java-17-amazon-corretto maven -y
   # o en Ubuntu:
   sudo apt update
   sudo apt install openjdk-17-jdk maven -y
   ```

4. **Clonar y ejecutar:**
   ```bash
   git clone https://github.com/tu-usuario/MicroSpring.git
   cd MicroSpring
   mvn clean compile
   mvn exec:java -Dexec.mainClass="escuelaing.edu.arep.microspringboot.MicrosSpringBoot"
   ```

5. **Acceder:**
   - URL: `http://tu-ip-publica:8080`



## Tecnologías Utilizadas

- **Java 17:** Lenguaje de programación
- **Maven:** Gestión de dependencias y ciclo de vida
- **Java Reflection API:** Carga dinámica y anotaciones
- **Java Networking:** ServerSocket para servidor HTTP
- **Concurrent Collections:** Manejo thread-safe de rutas


## Limitaciones Conocidas

- Servidor de un solo hilo (no concurrente)
- Sin soporte para POST, PUT, DELETE
- Sin sesiones o cookies
- Sin plantillas dinámicas (solo String)
- Parser HTTP simple (no robusto para producción)



## Autor

Proyecto desarrollado para el curso de Tecnologías de Desarrollo de Software Empresarial (TDSE)  
Escuela Colombiana de Ingeniería Julio Garavito

