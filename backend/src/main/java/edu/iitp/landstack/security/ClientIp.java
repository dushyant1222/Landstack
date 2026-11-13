package edu.iitp.landstack.security;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {
    private ClientIp() {}

    /** Uses the container's remote address (X-Forwarded-For is applied by server.forward-headers-strategy). */
    public static String of(HttpServletRequest req) {
        String ip = req.getRemoteAddr();
        return ip == null ? "unknown" : ip;
    }
}
