package hev.htproxy;

/** hev-socks5-tunnel JNI bağlantısı (varsayılan PKGNAME/CLSNAME). */
public final class TProxyService {
    public static native boolean TProxyStartService(String config_path, int fd);
    public static native boolean TProxyStopService();
    public static native boolean TProxyIsRunning();
    public static native long[] TProxyGetStats();

    static {
        System.loadLibrary("hev-socks5-tunnel");
    }
}
