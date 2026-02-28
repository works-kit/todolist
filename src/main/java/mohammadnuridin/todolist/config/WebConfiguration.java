package mohammadnuridin.todolist.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfiguration implements WebMvcConfigurer {
    // Saat ini belum ada konfigurasi khusus, tapi kelas ini sudah siap untuk
    // digunakan jika nanti dibutuhkan.
    // Contoh penggunaan: menambahkan interceptor, formatter, dll.
}