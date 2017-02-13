package com.revinate.jaxwsspringsamplefromjava;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.jws.WebService;

@WebService
@Component
public class FibonacciPortImpl {

    @Autowired
    private NumberService numberService;
    
    public int fibonacci(int index) throws FibonacciException {
        if (index < 0) {
            throw new FibonacciException("Index cannot be negative.", "Index: " + index);
        }

        return numberService.fibonacci(index);
    }
}
