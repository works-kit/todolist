package mohammadnuridin.todolist.common.exeception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ErrorPageController implements ErrorController {

    @GetMapping(path = "/error")
    public ResponseEntity<String> error(HttpServletRequest request) {
        Integer status = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String message = (String) request.getAttribute(RequestDispatcher.ERROR_MESSAGE);

        String html = """
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Error</title>
                    <style>
                        * {
                            box-sizing: border-box;
                            font-family: 'Segoe UI', Arial, sans-serif;
                        }

                        body {
                            margin: 0;
                            height: 100vh;
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            background: linear-gradient(135deg, #667eea, #764ba2);
                        }

                        .card {
                            background: #ffffff;
                            padding: 40px;
                            border-radius: 12px;
                            box-shadow: 0 10px 30px rgba(0,0,0,0.2);
                            text-align: center;
                            width: 400px;
                            max-width: 90%;
                            animation: fadeIn 0.5s ease-in-out;
                        }

                        .status {
                            font-size: 48px;
                            font-weight: bold;
                            color: #e74c3c;
                            margin-bottom: 10px;
                        }

                        .message {
                            font-size: 18px;
                            color: #555;
                            margin-bottom: 20px;
                        }

                        .footer {
                            font-size: 13px;
                            color: #999;
                        }

                        @keyframes fadeIn {
                            from { opacity: 0; transform: translateY(15px); }
                            to { opacity: 1; transform: translateY(0); }
                        }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <div class="status">$status</div>
                        <div class="message">$message</div>
                        <div class="footer">Todolist API Service</div>
                    </div>
                </body>
                </html>
                """
                .replace("$status", status.toString())
                .replace("$message", message);

        return ResponseEntity.status(status).body(html);
    }
}