package git.artdeell.mojo;

/**
 * The native logger has room for one listener only. This lets the in-game log view and Auto Skin both
 * listen, and keeps the native side free of callbacks when nobody does (they are expensive).
 */
public final class LogDispatcher {
    private static Logger.eventLogListener sView, sAuto;

    private static final Logger.eventLogListener SINK = text -> {
        Logger.eventLogListener view = sView, auto = sAuto;
        if(view != null) view.onEventLogged(text);
        if(auto != null) auto.onEventLogged(text);
    };

    private LogDispatcher() {}

    public static synchronized void setViewListener(Logger.eventLogListener listener) {
        sView = listener;
        update();
    }

    public static synchronized void setAutoListener(Logger.eventLogListener listener) {
        sAuto = listener;
        update();
    }

    private static void update() {
        Logger.setLogListener(sView == null && sAuto == null ? null : SINK);
    }
}
