package com.revinate.jaxwsspringsamplefromjava;

import org.springframework.stereotype.Service;

@Service
public class NumberService {

    public int fibonacci(int index) {
        if (index == 0) {
            return 0;
        }
        if (index == 1) {
            return 1;
        }
        return fibonacci(index - 1) + fibonacci(index - 2);
    }
}
