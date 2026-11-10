package com.moyuyo.common;

import com.moyuyo.common.utils.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ClientIpResolver 安全回归测试
 * <p>
 * 核心断言：X-Forwarded-For / X-Real-IP 只能由可信代理（默认私有网段，与 Tomcat
 * internal-proxies 一致）转发时才被采信；不可信对端直连时伪造代理头不得生效，
 * 否则 IP 维度限流可被绕过。
 */
class ClientIpResolverTest {

    /** 构造 mock 请求：remoteAddr 必填，代理头可为 null */
    private HttpServletRequest request(String remoteAddr, String xff, String realIp) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn(remoteAddr);
        when(req.getHeader("X-Forwarded-For")).thenReturn(xff);
        when(req.getHeader("X-Real-IP")).thenReturn(realIp);
        return req;
    }

    @Test
    void resolve_不可信对端直连时忽略伪造的XFF() {
        // 对端是公网地址：客户端可任意伪造 XFF，必须忽略并返回真实对端
        assertEquals("203.0.113.9",
                ClientIpResolver.resolve(request("203.0.113.9", "1.2.3.4", "5.6.7.8")));
    }

    @Test
    void resolve_可信代理链路下采信XFF() {
        // 对端是 Docker 网段代理：XFF 由代理写入，可采信
        assertEquals("1.2.3.4",
                ClientIpResolver.resolve(request("172.17.0.2", "1.2.3.4", null)));
    }

    @Test
    void resolve_多段XFF取最右侧非可信段防止左侧伪造() {
        // 攻击者自带 "9.9.9.9"，代理在右侧追加真实地址 1.2.3.4
        assertEquals("1.2.3.4",
                ClientIpResolver.resolve(request("172.17.0.2", "9.9.9.9, 1.2.3.4", null)));
    }

    @Test
    void resolve_无XFF时用XRealIP兜底() {
        assertEquals("5.6.7.8",
                ClientIpResolver.resolve(request("10.0.0.5", null, "5.6.7.8")));
    }

    @Test
    void resolve_带端口的对端地址会剥离端口() {
        assertEquals("203.0.113.9",
                ClientIpResolver.resolve(request("203.0.113.9:51234", null, null)));
    }

    @Test
    void resolve_可信代理且无代理头时回退对端地址() {
        assertEquals("10.0.0.5",
                ClientIpResolver.resolve(request("10.0.0.5", null, null)));
    }

    @Test
    void isTrustedProxy_覆盖私有网段与公网地址() {
        assertEquals(true, ClientIpResolver.isTrustedProxy("127.0.0.1"));
        assertEquals(true, ClientIpResolver.isTrustedProxy("172.18.0.3"));
        assertEquals(true, ClientIpResolver.isTrustedProxy("192.168.1.10"));
        assertEquals(true, ClientIpResolver.isTrustedProxy("::1"));
        assertEquals(false, ClientIpResolver.isTrustedProxy("203.0.113.9"));
    }

    @Test
    void resolve_IPv4映射地址的对端也能正确识别为可信代理() {
        // Tomcat 双栈监听时 IPv4 连接可能以 ::ffff: 形式出现，必须先归一化再判断信任
        assertEquals("1.2.3.4",
                ClientIpResolver.resolve(request("::ffff:172.17.0.2", "1.2.3.4", null)));
        // 公网映射地址仍不可信，伪造的 XFF 必须忽略
        assertEquals("203.0.113.9",
                ClientIpResolver.resolve(request("::ffff:203.0.113.9", "1.2.3.4", null)));
    }

    @Test
    void resolve_IPv6回环代理链路下采信XFF() {
        // Nginx 反代到 [::1]:8080 的场景
        assertEquals("1.2.3.4",
                ClientIpResolver.resolve(request("::1", "1.2.3.4", null)));
    }
}
