package andro.pluginsuite.net;

public interface ResponseListener<T> {
    void onResponse(T response);
}
