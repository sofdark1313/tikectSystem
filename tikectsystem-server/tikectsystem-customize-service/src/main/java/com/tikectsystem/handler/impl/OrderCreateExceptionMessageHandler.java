package com.tikectsystem.handler.impl;

import com.tikectsystem.enums.MessageType;
import org.springframework.stereotype.Component;

@Component
public class OrderCreateExceptionMessageHandler extends AbstractKafkaExceptionMessageHandler {

    @Override
    public MessageType getMessageType() {
        return MessageType.ORDER_CREATE;
    }
}
