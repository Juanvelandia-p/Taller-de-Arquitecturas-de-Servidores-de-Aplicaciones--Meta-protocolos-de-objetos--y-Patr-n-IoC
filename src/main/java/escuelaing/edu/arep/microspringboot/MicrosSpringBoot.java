package escuelaing.edu.arep.microspringboot;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MicrosSpringBoot {

    private static final int PORT = 8080;
    private static final String WEB_ROOT = "src/main/resources/webroot";
    private static Map<String, ControllerMethod> routes = new ConcurrentHashMap<>();
    private static Map<String, Object> controllerInstances = new ConcurrentHashMap<>();

    static class ControllerMethod {
        Object instance;
        Method method;

        ControllerMethod(Object instance, Method method) {
            this.instance = instance;
            this.method = method;
        }
    }

    public static void main(String[] args) throws IOException {
        // Si se pasa un controlador por línea de comandos, cargarlo
        if (args.length > 0) {
            try {
                loadController(args[0]);
            } catch (ClassNotFoundException e) {
                System.err.println("No se pudo cargar el controlador: " + args[0]);
                e.printStackTrace();
            }
        } else {
            // Escanear el classpath buscando controladores
            scanControllers();
        }

        System.out.println("========================================");
        System.out.println("MicroSpringBoot Server Started");
        System.out.println("Port: " + PORT);
        System.out.println("========================================");
        System.out.println("Registered routes:");
        routes.forEach((path, cm) -> System.out.println("  GET " + path + " -> " + cm.method.getName()));
        System.out.println("========================================");

        startServer();
    }

    private static void scanControllers() {
        // Buscar controladores en el paquete actual
        String basePackage = "escuelaing.edu.arep.microspringboot";
        try {
            loadController(basePackage + ".HelloController");
        } catch (ClassNotFoundException e) {
            // Ignorar si no existe
        }
        try {
            loadController(basePackage + ".GreetingController");
        } catch (ClassNotFoundException e) {
            // Ignorar si no existe
        }
    }

    private static void loadController(String className) throws ClassNotFoundException {
        System.out.println("Loading controller: " + className);
        Class<?> c = Class.forName(className);

        if (c.isAnnotationPresent(RestController.class)) {
            try {
                Object instance = c.getDeclaredConstructor().newInstance();
                controllerInstances.put(className, instance);

                for (Method m : c.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(GetMapping.class)) {
                        GetMapping getMapping = m.getAnnotation(GetMapping.class);
                        String path = getMapping.value();
                        routes.put(path, new ControllerMethod(instance, m));
                        System.out.println("  Mapped: GET " + path + " -> " + m.getName());
                    }
                }
            } catch (Exception e) {
                System.err.println("Error instantiating controller: " + className);
                e.printStackTrace();
            }
        }
    }

    private static void startServer() throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);

        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                handleRequest(clientSocket);
            } catch (IOException e) {
                System.err.println("Error handling request: " + e.getMessage());
            }
        }
    }

    private static void handleRequest(Socket clientSocket) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
             BufferedOutputStream dataOut = new BufferedOutputStream(clientSocket.getOutputStream())) {

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            System.out.println("Request: " + requestLine);

            String[] tokens = requestLine.split(" ");
            String method = tokens[0];
            String fileRequested = tokens[1];

            // Leer headers
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // Leer headers pero no los usamos por ahora
            }

            if (method.equals("GET")) {
                handleGetRequest(fileRequested, out, dataOut);
            }

        } catch (Exception e) {
            System.err.println("Error processing request: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleGetRequest(String fileRequested, PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        // Separar path y query string
        String path = fileRequested;
        Map<String, String> queryParams = new HashMap<>();

        if (fileRequested.contains("?")) {
            String[] parts = fileRequested.split("\\?", 2);
            path = parts[0];
            parseQueryString(parts[1], queryParams);
        }

        // Intentar rutear a un controlador
        if (routes.containsKey(path)) {
            handleControllerRequest(path, queryParams, out, dataOut);
        } else {
            // Intentar servir archivo estático
            handleStaticFile(path, out, dataOut);
        }
    }

    private static void parseQueryString(String queryString, Map<String, String> queryParams) {
        if (queryString == null || queryString.isEmpty()) {
            return;
        }

        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                try {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                    queryParams.put(key, value);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void handleControllerRequest(String path, Map<String, String> queryParams, 
                                               PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        try {
            ControllerMethod cm = routes.get(path);
            Method method = cm.method;
            Object instance = cm.instance;

            // Preparar argumentos del método
            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];

            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];
                if (param.isAnnotationPresent(RequestParam.class)) {
                    RequestParam requestParam = param.getAnnotation(RequestParam.class);
                    String paramName = requestParam.value();
                    String defaultValue = requestParam.defaultValue();

                    String value = queryParams.getOrDefault(paramName, defaultValue);
                    if (value.isEmpty() && !defaultValue.isEmpty()) {
                        value = defaultValue;
                    }
                    args[i] = value;
                }
            }

            // Invocar el método
            Object result = method.invoke(instance, args);
            String response = result != null ? result.toString() : "";

            // Enviar respuesta HTTP
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: text/html; charset=UTF-8");
            out.println("Content-Length: " + response.getBytes(StandardCharsets.UTF_8).length);
            out.println();
            out.println(response);
            out.flush();

        } catch (IllegalAccessException | InvocationTargetException e) {
            System.err.println("Error invoking controller method: " + e.getMessage());
            e.printStackTrace();
            sendErrorResponse(out, 500, "Internal Server Error");
        }
    }

    private static void handleStaticFile(String fileRequested, PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        File file = new File(WEB_ROOT, fileRequested);

        if (file.exists() && !file.isDirectory()) {
            byte[] fileData = Files.readAllBytes(file.toPath());
            String contentType = getContentType(fileRequested);

            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: " + contentType);
            out.println("Content-Length: " + fileData.length);
            out.println();
            out.flush();

            dataOut.write(fileData, 0, fileData.length);
            dataOut.flush();
        } else {
            sendErrorResponse(out, 404, "File Not Found");
        }
    }

    private static String getContentType(String fileRequested) {
        if (fileRequested.endsWith(".html")) {
            return "text/html";
        } else if (fileRequested.endsWith(".css")) {
            return "text/css";
        } else if (fileRequested.endsWith(".js")) {
            return "application/javascript";
        } else if (fileRequested.endsWith(".png")) {
            return "image/png";
        } else if (fileRequested.endsWith(".jpg") || fileRequested.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileRequested.endsWith(".gif")) {
            return "image/gif";
        }
        return "text/plain";
    }

    private static void sendErrorResponse(PrintWriter out, int statusCode, String message) {
        out.println("HTTP/1.1 " + statusCode + " " + message);
        out.println("Content-Type: text/html");
        out.println();
        out.println("<html><body><h1>" + statusCode + " - " + message + "</h1></body></html>");
        out.flush();
    }
}
