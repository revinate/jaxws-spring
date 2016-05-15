package com.revinate.jaxwsspringsample;

import com.revinate.sample.service.FibonacciFault;
import com.revinate.sample.service.FibonacciPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.jws.WebService;

@WebService(endpointInterface = "com.revinate.sample.service.FibonacciPort")
@Component
public class FibonacciPortImpl implements FibonacciPort {

    @Autowired
    private NumberService numberService;
    
    public int fibonacci(int index) throws FibonacciFault {
        if (index < 0) {
            String message = "Index cannot be negative.";
            com.revinate.sample.datatype.FibonacciFault fault
                    = new com.revinate.sample.datatype.FibonacciFault();
            fault.setMessage(message);
            fault.setFaultInfo("Index: " + index);
            throw new FibonacciFault(message, fault);
        }

        return numberService.fibonacci(index);
    }
}
