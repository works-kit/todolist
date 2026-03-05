package mohammadnuridin.todolist.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Konfigurasi AspectJ untuk mengaktifkan Aspect-Oriented Programming (AOP).
 *
 * @EnableAspectJAutoProxy : Mengaktifkan Spring untuk memproses @Aspect
 *                         annotation
 *                         dan membuat proxy untuk method yang match dengan
 *                         pointcut.
 * 
 *                         proxyTargetClass = true : Menggunakan CGLIB proxy
 *                         (dapat proxy concrete class)
 *                         instead of JDK dynamic proxy (hanya untuk interface).
 */
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AspectJConfiguration {
    // Konfigurasi AspectJ sudah cukup di sini
    // LoggingAspect akan otomatis di-scan karena @Component annotation
}