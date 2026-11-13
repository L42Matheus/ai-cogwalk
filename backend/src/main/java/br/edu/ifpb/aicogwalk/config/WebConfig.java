package br.edu.ifpb.aicogwalk.config;

import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties props;

    public WebConfig(AppProperties props) {
        this.props = props;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (props.demoPortal() != null && props.demoPortal().enabled()) {
            String path = props.demoPortal().path();
            String location = path.startsWith("classpath:")
                    ? (path.endsWith("/") ? path : path + "/")
                    : Path.of(path).toAbsolutePath().normalize().toUri().toString();
            registry.addResourceHandler("/portal-exemplo/**").addResourceLocations(location);
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("http://localhost:3000").allowedMethods("*");
    }
}
