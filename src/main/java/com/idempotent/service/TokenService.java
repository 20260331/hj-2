package com.idempotent.service;

public interface TokenService {

    String generateToken();

    String generateToken(String businessKey);

    boolean checkToken(String token);

    boolean checkToken(String token, String businessKey);

    boolean deleteToken(String token);

    boolean deleteToken(String token, String businessKey);
}
