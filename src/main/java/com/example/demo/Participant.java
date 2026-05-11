package com.example.demo;

public interface Participant {

    boolean prepare(UserEntity user);

    void commit();

    void rollback();
}
