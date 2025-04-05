import android.util.Log;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import java.util.concurrent.TimeUnit;

public class WebSocketClient {
    private static final String TAG = "WebSocketClient";

    private OkHttpClient createClient() {
        return new OkHttpClient.Builder()
                .pingInterval(15, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(1, 30, TimeUnit.SECONDS))
                // Add SSL handling for development
                .hostnameVerifier((hostname, session) -> true)
                .sslSocketFactory(getUnsafeSSLSocketFactory(), getUnsafeTrustManager())
                .build();
    }
    
    /**
     * Get an SSL socket factory that trusts all certificates
     * Only for development!
     */
    private SSLSocketFactory getUnsafeSSLSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{getUnsafeTrustManager()}, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            Log.e(TAG, "Error creating unsafe SSL socket factory", e);
            throw new RuntimeException("Cannot create unsafe SSL socket factory", e);
        }
    }
    
    /**
     * Get a trust manager that trusts all certificates
     * Only for development!
     */
    private X509TrustManager getUnsafeTrustManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }
            
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }
            
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[]{};
            }
        };
    }
} 