package net.datasa.tanoshimi.util;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * 외부 API 용 WebClient 빌더.
 * Netty 기본 DNS 리졸버는 OS 를 거치지 않고 공유기(192.168.0.1:53)에 UDP 로 직접 물어보는데,
 * 일부 공유기에서 타임아웃 나서 "Failed to resolve 'generativelanguage.googleapis.com'" 로 번역/AI 가 실패했다.
 * JVM(OS) 리졸버를 쓰도록 고정한다.
 */
public final class WebClients {

    private WebClients() {}

    public static WebClient.Builder builder() {
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(
                HttpClient.create().resolver(DefaultAddressResolverGroup.INSTANCE)));
    }
}
