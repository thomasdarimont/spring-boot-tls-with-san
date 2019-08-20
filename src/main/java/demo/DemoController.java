package demo;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.time.Instant;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
class DemoController {

    private final HttpServletRequest request;

    @GetMapping
    Object get(@AuthenticationPrincipal Principal principal) {
        //String clientDname = ((X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate"))[0].getSubjectDN().getName();
        return "Hello " + Instant.now() + " for client=" + principal.getName();
    }
}
