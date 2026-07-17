package com.moyuyo.api.controller.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
// TODO: 静态页面预览功能，无 Service 依赖
public class AdminSpaController {

    @GetMapping(value = "/admin/**")
    public String forwardAdminSpa(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/admin/assets/")) {
            return null;
        }
        return "forward:/admin/index.html";
    }
}
