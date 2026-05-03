package com.battleiq.battleiq.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaController {
    @RequestMapping(value = {
            "/", "/home", "/create", "/lobby/**",
            "/game/**", "/result/**"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
