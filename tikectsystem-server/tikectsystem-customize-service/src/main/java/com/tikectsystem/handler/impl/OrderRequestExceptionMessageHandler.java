package com.tikectsystem.handler.impl;

import com.tikectsystem.enums.MessageType;
import org.springframework.stereotype.Component;

@Component
public class OrderRequestExceptionMessageHandler extends AbstractKafkaExceptionMessageHandler {

    @Override
    public MessageType getMessageType() {
        return MessageType.ORDER_REQUEST;
    }
}
