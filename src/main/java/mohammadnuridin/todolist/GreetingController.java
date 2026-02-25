package mohammadnuridin.todolist;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import mohammadnuridin.todolist.common.dto.WebResponse;

@RestController
public class GreetingController {

    @Autowired
    private MessageSource messageSource;

    @GetMapping("/")
    public WebResponse<String> hello() {
        return WebResponse.<String>builder()
                .code(HttpStatus.OK.value())
                .status("success")
                .data("Hello, welcome to the Todolist API!")
                .build();
    }

    @GetMapping("/greet")
    public WebResponse<String> greet(Locale locale) {
        String localizedMessage = messageSource.getMessage("greeting", null, locale);
        return WebResponse.<String>builder()
                .code(HttpStatus.OK.value())
                .status("success")
                .data(localizedMessage)
                .build();
    }
}
