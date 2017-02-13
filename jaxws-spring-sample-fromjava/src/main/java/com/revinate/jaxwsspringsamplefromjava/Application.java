package com.revinate.jaxwsspringsamplefromjava;

import com.revinate.ws.spring.SpringService;
import com.sun.xml.ws.transport.http.servlet.SpringBinding;
import com.sun.xml.ws.transport.http.servlet.WSSpringServlet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

import javax.servlet.Servlet;
import javax.xml.namespace.QName;
import java.io.IOException;

@SpringBootApplication
public class Application {

    @Autowired
    private FibonacciPortImpl fibonacciPortImpl;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public Servlet jaxwsServlet() {
        return new WSSpringServlet();
    }

    @Bean
    public ServletRegistrationBean jaxwsServletRegistration() {
        return new ServletRegistrationBean(jaxwsServlet(), "/service/*");
    }

    @Bean
    public SpringService fibonacciService() throws IOException {
        SpringService service = new SpringService();
        service.setBean(fibonacciPortImpl);
        service.setServiceName(new QName("http://www.revinate.com/sample", "SampleService"));
        service.setPortName(new QName("http://www.revinate.com/sample", "FibonacciPort"));
        return service;
    }

    @Bean
    public SpringBinding fibonacciBinding() throws Exception {
        SpringBinding binding = new SpringBinding();
        binding.setUrl("/service/fibonacci");
        binding.setService(fibonacciService().getObject());
        return binding;
    }
}
