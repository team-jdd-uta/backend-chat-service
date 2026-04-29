package com.example.chat.service;

public interface MessageBrokerService {
    void publish(String topic, Object message);
}

