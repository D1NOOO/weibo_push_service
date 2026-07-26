package com.hotsearch.provider;

/** 上游限频，投递执行器据此进行退避重试。 */
public class RateLimitedException extends ProviderException {

    public RateLimitedException(String message) {
        super(message);
    }
}
