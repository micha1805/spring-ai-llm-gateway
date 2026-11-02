package io.github.micha1805.llmgateway.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class HomeController {

    @GetMapping("/")
    String swagger() {
        return "redirect:/swagger-ui.html";
    }
}
