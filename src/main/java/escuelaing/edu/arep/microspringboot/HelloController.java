package escuelaing.edu.arep.microspringboot;

@RestController
public class HelloController {
    @GetMapping("/")
    public String index() {
        return "Greetings from Spring Boot!";
    }
    
    @GetMapping("/hello")
    public String hello() {
        return "<html><body><h1>Hello from MicroSpringBoot!</h1></body></html>";
    }
}