package io.github.legionivo.plugin.api;

import okhttp3.OkHttpClient.Builder;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static io.github.legionivo.plugin.api.UnsafeOkHttpClient.getUnsafeOkHttpClient;

public class TestRailClientBuilder {

    private final String baseUrl;
    private final String user;
    private final String password;

    public TestRailClientBuilder(String baseUrl, String user, String password) {
        this.baseUrl = baseUrl;
        this.user = user;
        this.password = password;
    }

    public TestRailClient build() {
        Builder httpClient = getUnsafeOkHttpClient().newBuilder();
        httpClient.addInterceptor(new HttpLoggingInterceptor());
        httpClient.addInterceptor(new BasicAuthInterceptor(user, password));

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addCallAdapterFactory(new TestRailCallAdapter())
                .addConverterFactory(GsonConverterFactory.create())
                .client(httpClient.build())
                .build();

        return retrofit.create(TestRailClient.class);
    }
}
