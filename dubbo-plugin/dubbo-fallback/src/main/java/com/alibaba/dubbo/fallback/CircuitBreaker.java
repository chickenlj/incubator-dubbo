package com.alibaba.dubbo.fallback;

/**
 * @author ken.lj
 * @date 2018/2/27
 */
public interface CircuitBreaker {

    boolean isAllowed();

    boolean isOpen();

    void markSuccess();

}
