package escuelaing.edu.arep.microspringboot;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MicrosSpringBoot {

    private static final int PORT = 8080;
    private static final String WEB_ROOT = "src/main/resources/webroot";
    private static final int THREAD_POOL_SIZE = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
    private static Map<String, ControllerMethod> routes = new ConcurrentHashMap<>();
    private static Map<String, Object> controllerInstances = new ConcurrentHashMap<>();
    private static final AtomicBoolean isRunning = new AtomicBoolean(true);
    private static ServerSocket serverSocket;
    private static ExecutorService requestExecutor;

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
        serverSocket = new ServerSocket(PORT);
        requestExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        registerShutdownHook();

        while (isRunning.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                requestExecutor.submit(() -> handleRequest(clientSocket));
            } catch (SocketException e) {
                // Expected when closing the server socket during graceful shutdown.
                if (isRunning.get()) {
                    System.err.println("Socket error handling request: " + e.getMessage());
                }
            } catch (IOException e) {
                if (isRunning.get()) {
                    System.err.println("Error handling request: " + e.getMessage());
                }
            }
        }

        shutdownExecutorGracefully();
    }

    private static void handleRequest(Socket clientSocket) {
        try (Socket socket = clientSocket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedOutputStream dataOut = new BufferedOutputStream(socket.getOutputStream())) {

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

        // Prefer static index for root path when available.
        if ("/".equals(path)) {
            File indexFile = new File(WEB_ROOT, "index.html");
            if (indexFile.exists() && !indexFile.isDirectory()) {
                handleStaticFile(path, out, dataOut);
                return;
            }
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
            out.print("HTTP/1.1 200 OK\r\n");
            out.print("Content-Type: text/html; charset=UTF-8\r\n");
            out.print("Content-Length: " + response.getBytes(StandardCharsets.UTF_8).length + "\r\n");
            out.print("\r\n");
            out.print(response);
            out.flush();

        } catch (IllegalAccessException | InvocationTargetException e) {
            System.err.println("Error invoking controller method: " + e.getMessage());
            e.printStackTrace();
            sendErrorResponse(out, 500, "Internal Server Error");
        }
    }

    private static void handleStaticFile(String fileRequested, PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        String relativePath = fileRequested;
        if ("/".equals(relativePath)) {
            relativePath = "index.html";
        } else if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        // Prevent basic path traversal when serving static files.
        if (relativePath.contains("..")) {
            sendErrorResponse(out, 403, "Forbidden");
            return;
        }

        File file = new File(WEB_ROOT, relativePath);

        if (file.exists() && !file.isDirectory()) {
            byte[] fileData = Files.readAllBytes(file.toPath());
            String contentType = getContentType(relativePath);

            out.print("HTTP/1.1 200 OK\r\n");
            out.print("Content-Type: " + contentType + "\r\n");
            out.print("Content-Length: " + fileData.length + "\r\n");
            out.print("\r\n");
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
        String response = "<html><body><h1>" + statusCode + " - " + message + "</h1></body></html>";
        out.print("HTTP/1.1 " + statusCode + " " + message + "\r\n");
        out.print("Content-Type: text/html; charset=UTF-8\r\n");
        out.print("Content-Length: " + response.getBytes(StandardCharsets.UTF_8).length + "\r\n");
        out.print("\r\n");
        out.print(response);
        out.flush();
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown signal received. Stopping server gracefully...");
            stopServer();
        }));
    }

    public static void stopServer() {
        if (!isRunning.compareAndSet(true, false)) {
            return;
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }

        shutdownExecutorGracefully();
    }

    private static void shutdownExecutorGracefully() {
        if (requestExecutor == null || requestExecutor.isShutdown()) {
            return;
        }

        requestExecutor.shutdown();
        try {
            if (!requestExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                requestExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            requestExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
