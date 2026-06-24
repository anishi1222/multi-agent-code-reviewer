package dev.logicojp.reviewer.agent;

interface RubberDuckSession extends AutoCloseable {

    String send(String prompt) throws Exception;

    @Override
    void close() throws Exception;
}
