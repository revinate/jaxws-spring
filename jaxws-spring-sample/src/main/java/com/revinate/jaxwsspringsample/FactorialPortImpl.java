package com.revinate.jaxwsspringsample;

import com.revinate.sample.service.FactorialFault;
import com.revinate.sample.service.FactorialPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.jws.WebService;

@WebService(endpointInterface = "com.revinate.sample.service.FactorialPort")
@Component
public class FactorialPortImpl implements FactorialPort {

    @Autowired
    private NumberService numberService;

    public int factorial(int number) throws FactorialFault {
        if (number < 0) {
            String message = "Number cannot be negative.";
            com.revinate.sample.datatype.FactorialFault fault
                    = new com.revinate.sample.datatype.FactorialFault();
            fault.setMessage(message);
            fault.setFaultInfo("Number: " + number);
            throw new FactorialFault(message, fault);
        }

        return numberService.factorial(number);
    }
}
