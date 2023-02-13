package io.github.legionivo.plugin.api;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static okhttp3.Credentials.basic;

public class BasicAuthInterceptor implements Interceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private final String credentials;

    BasicAuthInterceptor(String user, String password) {
        this.credentials = basic(user, password);
    }


    @Override
    public @NotNull Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
//        url contains '?' retrofit parse next values as @Query
        String url = original.url().toString().replaceAll("%3F", "?");
        Builder requestBuilder = original.newBuilder()
                .header(AUTHORIZATION_HEADER, credentials)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .url(url);

        Request request = requestBuilder.build();
        return chain.proceed(request);
    }
}
