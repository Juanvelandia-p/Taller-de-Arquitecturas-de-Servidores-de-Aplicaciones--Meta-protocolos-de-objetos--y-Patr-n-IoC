package escuelaing.edu.arep.microspringboot;

import java.util.concurrent.atomic.AtomicLong;

@RestController
public class GreetingController {

    private final AtomicLong counter = new AtomicLong();

    @GetMapping("/greeting")
    public String greeting(@RequestParam(value = "name", defaultValue = "World") String name) {
        long count = counter.incrementAndGet();
        return String.format("<html><body><h1>Greeting #%d</h1><p>Hello, %s!</p></body></html>", count, name);
    }
    
    @GetMapping("/saludo")
    public String saludo(@RequestParam(value = "name", defaultValue = "Mundo") String name) {
        return String.format("<html><body><h1>¡Hola, %s!</h1></body></html>", name);
    }
}
